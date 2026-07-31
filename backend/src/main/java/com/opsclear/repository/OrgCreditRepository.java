package com.opsclear.repository;

import com.opsclear.model.OrgCreditModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_CREDITS;
import static com.opsclear.generated.jooq.Tables.USERS;
import static java.util.List.of;
import static org.jooq.impl.DSL.sum;

@Repository
@RequiredArgsConstructor
public class OrgCreditRepository {

    private static final Field<String> GRANTED_BY_NAME = USERS.NAME.as("granted_by_name");

    private final DSLContext dsl;

    public OrgCreditModel insert(UUID orgId, int amount, String reason, UUID submissionId, UUID grantedBy) {
        UUID id = dsl.insertInto(ORG_CREDITS)
                .set(ORG_CREDITS.ORG_ID, orgId)
                .set(ORG_CREDITS.AMOUNT, amount)
                .set(ORG_CREDITS.REASON, reason)
                .set(ORG_CREDITS.SUBMISSION_ID, submissionId)
                .set(ORG_CREDITS.GRANTED_BY, grantedBy)
                .returning(ORG_CREDITS.ID)
                .fetchSingle()
                .getId();
        return findById(id).orElseThrow();
    }

    public List<OrgCreditModel> findByOrgId(UUID orgId) {
        return selectWithJoins()
                .where(ORG_CREDITS.ORG_ID.eq(orgId))
                .orderBy(ORG_CREDITS.CREATED_AT.desc())
                .fetch()
                .map(this::toModel);
    }

    public int sumByOrgId(UUID orgId) {
        BigDecimal total = dsl.select(sum(ORG_CREDITS.AMOUNT))
                .from(ORG_CREDITS)
                .where(ORG_CREDITS.ORG_ID.eq(orgId))
                .fetchOne(0, BigDecimal.class);
        return total != null ? total.intValue() : 0;
    }

    public void deleteAll() {
        dsl.deleteFrom(ORG_CREDITS).execute();
    }

    private Optional<OrgCreditModel> findById(UUID id) {
        return selectWithJoins()
                .where(ORG_CREDITS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    private SelectOnConditionStep<Record> selectWithJoins() {
        return dsl.select(of(
                        ORG_CREDITS.ID,
                        ORG_CREDITS.ORG_ID,
                        ORG_CREDITS.AMOUNT,
                        ORG_CREDITS.REASON,
                        ORG_CREDITS.SUBMISSION_ID,
                        ORG_CREDITS.GRANTED_BY,
                        GRANTED_BY_NAME,
                        ORG_CREDITS.CREATED_AT))
                .from(ORG_CREDITS)
                .join(USERS).on(ORG_CREDITS.GRANTED_BY.eq(USERS.ID));
    }

    private OrgCreditModel toModel(Record r) {
        return OrgCreditModel.builder()
                .id(r.get(ORG_CREDITS.ID))
                .orgId(r.get(ORG_CREDITS.ORG_ID))
                .amount(r.get(ORG_CREDITS.AMOUNT))
                .reason(r.get(ORG_CREDITS.REASON))
                .submissionId(r.get(ORG_CREDITS.SUBMISSION_ID))
                .grantedBy(r.get(ORG_CREDITS.GRANTED_BY))
                .grantedByName(r.get(GRANTED_BY_NAME))
                .createdAt(r.get(ORG_CREDITS.CREATED_AT).toInstant())
                .build();
    }
}
