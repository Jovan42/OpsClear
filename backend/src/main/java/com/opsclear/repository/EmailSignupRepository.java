package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.EmailSignupsRecord;
import com.opsclear.model.EmailSignupModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.EMAIL_SIGNUPS;

/** Email sign-ups (ADR-0066). Rows are hard-deleted: a removed address must really be gone. */
@Repository
@RequiredArgsConstructor
public class EmailSignupRepository {

    // PostgreSQL sets xmax to 0 on a freshly inserted row and to the updating transaction on an ON CONFLICT update
    private static final Field<Boolean> INSERTED = DSL.field("(xmax = 0)", SQLDataType.BOOLEAN);

    private final DSLContext dsl;

    /**
     * Inserts the sign-up, or, when the email (any case) is already on the list, only fills an empty code. Nothing
     * else on the existing row changes.
     *
     * @return true when a new row was inserted, false for a duplicate
     */
    public boolean upsert(EmailSignupModel signup) {
        return dsl.insertInto(EMAIL_SIGNUPS)
                .set(EMAIL_SIGNUPS.LIST, signup.getList())
                .set(EMAIL_SIGNUPS.EMAIL, signup.getEmail())
                .set(EMAIL_SIGNUPS.CONSENT_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(EMAIL_SIGNUPS.LOCALE, signup.getLocale())
                .set(EMAIL_SIGNUPS.UTM_SOURCE, signup.getUtmSource())
                .set(EMAIL_SIGNUPS.UTM_MEDIUM, signup.getUtmMedium())
                .set(EMAIL_SIGNUPS.UTM_CAMPAIGN, signup.getUtmCampaign())
                .set(EMAIL_SIGNUPS.CODE, signup.getCode())
                .onConflict(EMAIL_SIGNUPS.LIST, DSL.lower(EMAIL_SIGNUPS.EMAIL))
                .doUpdate()
                .set(EMAIL_SIGNUPS.CODE, DSL.coalesce(EMAIL_SIGNUPS.CODE, DSL.excluded(EMAIL_SIGNUPS.CODE)))
                .returningResult(INSERTED)
                .fetchSingle()
                .value1();
    }

    public List<EmailSignupModel> findByListOrderByCreatedAtDesc(String list) {
        return dsl.selectFrom(EMAIL_SIGNUPS)
                .where(EMAIL_SIGNUPS.LIST.eq(list))
                .orderBy(EMAIL_SIGNUPS.CREATED_AT.desc(), EMAIL_SIGNUPS.ID.desc())
                .fetch()
                .map(this::toModel);
    }

    /** @return true when a row was deleted */
    public boolean deleteById(UUID id) {
        return dsl.deleteFrom(EMAIL_SIGNUPS)
                .where(EMAIL_SIGNUPS.ID.eq(id))
                .execute() > 0;
    }

    public void deleteAll() {
        dsl.deleteFrom(EMAIL_SIGNUPS).execute();
    }

    private EmailSignupModel toModel(EmailSignupsRecord r) {
        return EmailSignupModel.builder()
                .id(r.getId())
                .list(r.getList())
                .email(r.getEmail())
                .code(r.getCode())
                .redeemedAt(toInstant(r.getRedeemedAt()))
                .redeemedOrgId(r.getRedeemedOrgId())
                .locale(r.getLocale())
                .utmSource(r.getUtmSource())
                .utmMedium(r.getUtmMedium())
                .utmCampaign(r.getUtmCampaign())
                .consentAt(toInstant(r.getConsentAt()))
                .createdAt(toInstant(r.getCreatedAt()))
                .build();
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
