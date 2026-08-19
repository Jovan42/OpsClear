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

    // Backfilled right after a discount is created for one or more grant rows — the
    // grant row(s) are inserted first (see insert()), before any discount exists.
    // Bulk because a discount can represent several merged unconsumed grants (see
    // findUnconsumedGrants) once mergeIntoNewPaddleDiscount reassigns them all here.
    public void setPaddleDiscountId(List<UUID> creditIds, String discountId) {
        dsl.update(ORG_CREDITS)
                .set(ORG_CREDITS.PADDLE_DISCOUNT_ID, discountId)
                .where(ORG_CREDITS.ID.in(creditIds))
                .execute();
    }

    // Grant rows still worth something on Paddle: synced to a real discount
    // (paddle_discount_id set) but not yet consumed by a transaction (no debit row for
    // that discount id yet). A subscription only ever has one discount attached at a
    // time, so before creating a new one for a fresh grant, CreditService folds these
    // in — otherwise the new discount would silently replace them, and Paddle would
    // discard whatever value they still represented (JOB-180 #5).
    public List<OrgCreditModel> findUnconsumedGrants(UUID orgId) {
        return selectWithJoins()
                .where(ORG_CREDITS.ORG_ID.eq(orgId))
                .and(ORG_CREDITS.AMOUNT.gt(0))
                .and(ORG_CREDITS.PADDLE_DISCOUNT_ID.isNotNull())
                .and(org.jooq.impl.DSL.notExists(dsl.selectOne()
                        .from(ORG_CREDITS.as("debit"))
                        .where(ORG_CREDITS.as("debit").PADDLE_DISCOUNT_ID.eq(ORG_CREDITS.PADDLE_DISCOUNT_ID))
                        .and(ORG_CREDITS.as("debit").AMOUNT.lt(0))))
                .fetch()
                .map(this::toModel);
    }

    // Every grant row sharing a given discount id — amount > 0 distinguishes them from
    // the negative debit row consumeCredit() may have already inserted for the same
    // discount id (both share paddle_discount_id, see the V038 migration comment).
    // More than one row can share a discount id once mergeIntoNewPaddleDiscount has
    // folded several unconsumed grants into a single replacement discount.
    public List<OrgCreditModel> findGrantsByPaddleDiscountId(String discountId) {
        return selectWithJoins()
                .where(ORG_CREDITS.PADDLE_DISCOUNT_ID.eq(discountId))
                .and(ORG_CREDITS.AMOUNT.gt(0))
                .fetch()
                .map(this::toModel);
    }

    // Idempotency check for CreditService.consumeCredit() — a redelivered
    // transaction.completed webhook must not debit the same discount twice.
    public boolean hasDebitForPaddleDiscountId(String discountId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(ORG_CREDITS)
                .where(ORG_CREDITS.PADDLE_DISCOUNT_ID.eq(discountId))
                .and(ORG_CREDITS.AMOUNT.lt(0)));
    }

    // A negative-amount row, not an UPDATE of the original grant — org_credits is
    // append-only by design (see V030's table comment), so consumption is recorded as
    // its own ledger entry rather than mutating history.
    public void insertDebit(UUID orgId, int amount, String reason, UUID grantedBy, String discountId) {
        dsl.insertInto(ORG_CREDITS)
                .set(ORG_CREDITS.ORG_ID, orgId)
                .set(ORG_CREDITS.AMOUNT, amount)
                .set(ORG_CREDITS.REASON, reason)
                .set(ORG_CREDITS.GRANTED_BY, grantedBy)
                .set(ORG_CREDITS.PADDLE_DISCOUNT_ID, discountId)
                .execute();
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
