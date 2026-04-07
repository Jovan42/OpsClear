package com.opsclear.repository;

import com.opsclear.model.OrgInviteModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORGANISATION_INVITES;
import static com.opsclear.generated.jooq.Tables.USERS;

@Repository
@RequiredArgsConstructor
public class OrgInviteRepository {

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    public OrgInviteModel save(OrgInviteModel invite) {
        UUID id = dsl.insertInto(ORGANISATION_INVITES)
                .set(ORGANISATION_INVITES.ORGANISATION_ID, invite.getOrganisationId())
                .set(ORGANISATION_INVITES.EMAIL, invite.getEmail())
                .set(ORGANISATION_INVITES.INVITED_BY, invite.getInvitedBy())
                .set(ORGANISATION_INVITES.TOKEN, invite.getToken())
                .set(ORGANISATION_INVITES.EXPIRES_AT, toLocalDateTime(invite.getExpiresAt()))
                .returning(ORGANISATION_INVITES.ID)
                .fetchSingle()
                .getId();
        return findById(id).orElseThrow();
    }

    public Optional<OrgInviteModel> findById(UUID id) {
        return dsl.select(
                        ORGANISATION_INVITES.ID,
                        ORGANISATION_INVITES.ORGANISATION_ID,
                        ORGANISATION_INVITES.EMAIL,
                        ORGANISATION_INVITES.INVITED_BY,
                        USERS.NAME.as("invited_by_name"),
                        ORGANISATION_INVITES.TOKEN,
                        ORGANISATION_INVITES.EXPIRES_AT,
                        ORGANISATION_INVITES.ACCEPTED_AT,
                        ORGANISATION_INVITES.REVOKED_AT,
                        ORGANISATION_INVITES.CREATED_AT)
                .from(ORGANISATION_INVITES)
                .leftJoin(USERS).on(ORGANISATION_INVITES.INVITED_BY.eq(USERS.ID))
                .where(ORGANISATION_INVITES.ID.eq(id))
                .fetchOptional(this::toModel);
    }

    public Optional<OrgInviteModel> findByToken(String token) {
        return dsl.select(
                        ORGANISATION_INVITES.ID,
                        ORGANISATION_INVITES.ORGANISATION_ID,
                        ORGANISATION_INVITES.EMAIL,
                        ORGANISATION_INVITES.INVITED_BY,
                        USERS.NAME.as("invited_by_name"),
                        ORGANISATION_INVITES.TOKEN,
                        ORGANISATION_INVITES.EXPIRES_AT,
                        ORGANISATION_INVITES.ACCEPTED_AT,
                        ORGANISATION_INVITES.REVOKED_AT,
                        ORGANISATION_INVITES.CREATED_AT)
                .from(ORGANISATION_INVITES)
                .leftJoin(USERS).on(ORGANISATION_INVITES.INVITED_BY.eq(USERS.ID))
                .where(ORGANISATION_INVITES.TOKEN.eq(token))
                .fetchOptional(this::toModel);
    }

    public List<OrgInviteModel> findPendingByOrgId(UUID organisationId) {
        return dsl.select(
                        ORGANISATION_INVITES.ID,
                        ORGANISATION_INVITES.ORGANISATION_ID,
                        ORGANISATION_INVITES.EMAIL,
                        ORGANISATION_INVITES.INVITED_BY,
                        USERS.NAME.as("invited_by_name"),
                        ORGANISATION_INVITES.TOKEN,
                        ORGANISATION_INVITES.EXPIRES_AT,
                        ORGANISATION_INVITES.ACCEPTED_AT,
                        ORGANISATION_INVITES.REVOKED_AT,
                        ORGANISATION_INVITES.CREATED_AT)
                .from(ORGANISATION_INVITES)
                .leftJoin(USERS).on(ORGANISATION_INVITES.INVITED_BY.eq(USERS.ID))
                .where(ORGANISATION_INVITES.ORGANISATION_ID.eq(organisationId))
                .and(ORGANISATION_INVITES.ACCEPTED_AT.isNull())
                .and(ORGANISATION_INVITES.REVOKED_AT.isNull())
                .and(ORGANISATION_INVITES.EXPIRES_AT.gt(LocalDateTime.now(ZoneOffset.UTC)))
                .orderBy(ORGANISATION_INVITES.CREATED_AT.desc())
                .fetch(this::toModel);
    }

    public boolean existsPendingByEmailAndOrgId(String email, UUID organisationId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(ORGANISATION_INVITES)
                        .where(ORGANISATION_INVITES.EMAIL.equalIgnoreCase(email))
                        .and(ORGANISATION_INVITES.ORGANISATION_ID.eq(organisationId))
                        .and(ORGANISATION_INVITES.ACCEPTED_AT.isNull())
                        .and(ORGANISATION_INVITES.REVOKED_AT.isNull())
                        .and(ORGANISATION_INVITES.EXPIRES_AT.gt(LocalDateTime.now(ZoneOffset.UTC))));
    }

    public void markAccepted(UUID id) {
        dsl.update(ORGANISATION_INVITES)
                .set(ORGANISATION_INVITES.ACCEPTED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(ORGANISATION_INVITES.ID.eq(id))
                .execute();
    }

    public void markRevoked(UUID id) {
        dsl.update(ORGANISATION_INVITES)
                .set(ORGANISATION_INVITES.REVOKED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(ORGANISATION_INVITES.ID.eq(id))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(ORGANISATION_INVITES).execute();
    }

    private OrgInviteModel toModel(org.jooq.Record r) {
        return OrgInviteModel.builder()
                .id(r.get(ORGANISATION_INVITES.ID))
                .organisationId(r.get(ORGANISATION_INVITES.ORGANISATION_ID))
                .email(r.get(ORGANISATION_INVITES.EMAIL))
                .invitedBy(r.get(ORGANISATION_INVITES.INVITED_BY))
                .invitedByName(r.get("invited_by_name", String.class))
                .token(r.get(ORGANISATION_INVITES.TOKEN))
                .expiresAt(toInstant(r.get(ORGANISATION_INVITES.EXPIRES_AT)))
                .acceptedAt(toInstant(r.get(ORGANISATION_INVITES.ACCEPTED_AT)))
                .revokedAt(toInstant(r.get(ORGANISATION_INVITES.REVOKED_AT)))
                .createdAt(toInstant(r.get(ORGANISATION_INVITES.CREATED_AT)))
                .build();
    }
}
