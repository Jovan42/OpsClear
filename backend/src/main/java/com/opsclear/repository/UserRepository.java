package com.opsclear.repository;

import com.opsclear.model.UserModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.USERS;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private UserModel toModel(Record r) {
        return UserModel.builder()
                .id(r.get(USERS.ID))
                .email(r.get(USERS.EMAIL))
                .name(r.get(USERS.NAME))
                .createdAt(toInstant(r.get(USERS.CREATED_AT)))
                .lastLoginAt(toInstant(r.get(USERS.LAST_LOGIN_AT)))
                .superUser(Boolean.TRUE.equals(r.get(USERS.SUPER_USER)))
                .build();
    }

    public Optional<UserModel> findById(UUID id) {
        return dsl.select(USERS.ID, USERS.EMAIL, USERS.NAME, USERS.CREATED_AT, USERS.LAST_LOGIN_AT, USERS.SUPER_USER)
                .from(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    public Optional<UserModel> findByEmail(String email) {
        return dsl.select(USERS.ID, USERS.EMAIL, USERS.NAME, USERS.CREATED_AT, USERS.LAST_LOGIN_AT, USERS.SUPER_USER)
                .from(USERS)
                .where(USERS.EMAIL.equalIgnoreCase(email))
                .fetchOptional()
                .map(this::toModel);
    }

    // JOB-244: was scoped to the caller's own org (JOIN ORGANISATION_MEMBERS filtered
    // to orgId), which meant it could only ever find someone ALREADY in that org — the
    // opposite of useful for its actual purpose (finding a new candidate to invite).
    // Unscoped by design now: any registered user matching the prefix is a valid
    // result, since the point of this search is picking someone to add. The
    // add-member picker already greys out rows that are already members via a
    // separate client-side check (AddOrgMemberForm's memberIds set).
    public List<UserModel> searchByEmail(String emailPrefix, int limit) {
        return dsl.select(USERS.ID, USERS.EMAIL, USERS.NAME, USERS.CREATED_AT, USERS.LAST_LOGIN_AT, USERS.SUPER_USER)
                .from(USERS)
                .where(USERS.EMAIL.likeIgnoreCase(emailPrefix + "%"))
                .orderBy(USERS.EMAIL.asc())
                .limit(limit)
                .fetch()
                .map(this::toModel);
    }

    public UserModel save(UserModel user) {
        Instant now = Instant.now();
        dsl.insertInto(USERS)
                .set(USERS.ID, user.getId())
                .set(USERS.EMAIL, user.getEmail())
                .set(USERS.NAME, user.getName())
                .set(USERS.CREATED_AT, toLocalDateTime(now))
                .set(USERS.LAST_LOGIN_AT, toLocalDateTime(now))
                .onConflict(USERS.ID)
                .doUpdate()
                .set(USERS.EMAIL, user.getEmail())
                .set(USERS.NAME, user.getName())
                .set(USERS.LAST_LOGIN_AT, toLocalDateTime(now))
                .execute();
        return findById(user.getId()).orElseThrow();
    }

    // Every integration test class ends its own cleanup chain with this call, but
    // users is referenced by ~20 other tables across the schema (organisation_members,
    // project_members, jobs, etc.) — a naive DELETE only works if every one of those
    // was already emptied in the exact right order first. TRUNCATE ... CASCADE
    // follows the FK graph automatically regardless of what any other test class left
    // behind, which is what actually stops the cross-class pollution FK violations
    // (see the "known issue" pattern: SubscriptionIntegrationTest, JobLink/
    // JobRelationship/JobTemplateIntegrationTest, SuperAdminPricingIntegrationTest).
    public void deleteAll() {
        dsl.truncate(USERS).cascade().execute();
    }
}
