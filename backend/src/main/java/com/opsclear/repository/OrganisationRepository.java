package com.opsclear.repository;

import com.opsclear.model.OrgMemberModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTION_ADDONS;
import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTIONS;
import static com.opsclear.generated.jooq.Tables.ORGANISATION_INVITES;
import static com.opsclear.generated.jooq.Tables.ORGANISATIONS;
import static com.opsclear.generated.jooq.Tables.ORGANISATION_MEMBERS;
import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static com.opsclear.generated.jooq.Tables.USERS;
import static java.util.List.of;

@Repository
@RequiredArgsConstructor
public class OrganisationRepository {

    private static final Field<String> CREATOR_NAME = USERS.NAME.as("creator_name");
    private static final String MEMBER_NAME = "member_name";
    private static final String MEMBER_EMAIL = "member_email";

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    public Optional<OrganisationModel> findByProject(UUID projectId) {
        return dsl.select(of(
                        ORGANISATIONS.ID,
                        ORGANISATIONS.NAME,
                        ORGANISATIONS.SLUG,
                        ORGANISATIONS.CREATED_BY,
                        CREATOR_NAME,
                        ORGANISATIONS.CREATED_AT,
                        ORGANISATIONS.DELETED_AT))
                .from(ORGANISATIONS)
                .leftJoin(USERS).on(ORGANISATIONS.CREATED_BY.eq(USERS.ID))
                .join(PROJECTS).on(PROJECTS.ORGANISATION_ID.eq(ORGANISATIONS.ID))
                .where(PROJECTS.ID.eq(projectId))
                .and(ORGANISATIONS.DELETED_AT.isNull())
                .limit(1)
                .fetchOptional()
                .map(this::toModel);
    }

    public Optional<OrganisationModel> findByMember(UUID userId) {
        return dsl.select(of(
                        ORGANISATIONS.ID,
                        ORGANISATIONS.NAME,
                        ORGANISATIONS.SLUG,
                        ORGANISATIONS.CREATED_BY,
                        CREATOR_NAME,
                        ORGANISATIONS.CREATED_AT,
                        ORGANISATIONS.DELETED_AT))
                .from(ORGANISATIONS)
                .leftJoin(USERS).on(ORGANISATIONS.CREATED_BY.eq(USERS.ID))
                .join(ORGANISATION_MEMBERS).on(ORGANISATION_MEMBERS.ORGANISATION_ID.eq(ORGANISATIONS.ID))
                .where(ORGANISATION_MEMBERS.USER_ID.eq(userId))
                .and(ORGANISATIONS.DELETED_AT.isNull())
                .limit(1)
                .fetchOptional()
                .map(this::toModel);
    }

    public Optional<OrganisationModel> findByIdAndDeletedAtIsNull(UUID id) {
        return selectWithCreator()
                .where(ORGANISATIONS.ID.eq(id))
                .and(ORGANISATIONS.DELETED_AT.isNull())
                .fetchOptional()
                .map(this::toModel);
    }

    public Optional<OrganisationModel> findById(UUID id) {
        return selectWithCreator()
                .where(ORGANISATIONS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    // Super-admin only — lets the credit-grant org picker resolve any org, not just
    // ones the caller happens to belong to (see findByMember/getById's member check).
    public List<OrganisationModel> findAllActive() {
        return selectWithCreator()
                .where(ORGANISATIONS.DELETED_AT.isNull())
                .orderBy(ORGANISATIONS.NAME.asc())
                .fetch()
                .map(this::toModel);
    }

    public boolean existsBySlugAndDeletedAtIsNull(String slug) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(ORGANISATIONS)
                        .where(ORGANISATIONS.SLUG.equalIgnoreCase(slug))
                        .and(ORGANISATIONS.DELETED_AT.isNull()));
    }

    public boolean existsBySlugAndIdNotAndDeletedAtIsNull(String slug, UUID excludeId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(ORGANISATIONS)
                        .where(ORGANISATIONS.SLUG.equalIgnoreCase(slug))
                        .and(ORGANISATIONS.ID.notEqual(excludeId))
                        .and(ORGANISATIONS.DELETED_AT.isNull()));
    }

    public OrganisationModel save(OrganisationModel org) {
        if (org.getId() == null) {
            UUID id = dsl.insertInto(ORGANISATIONS)
                    .set(ORGANISATIONS.NAME, org.getName())
                    .set(ORGANISATIONS.SLUG, org.getSlug().toUpperCase())
                    .set(ORGANISATIONS.CREATED_BY, org.getCreatedBy())
                    .set(ORGANISATIONS.CREATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .returning(ORGANISATIONS.ID)
                    .fetchSingle()
                    .getId();
            return findById(id).orElseThrow();
        }
        dsl.update(ORGANISATIONS)
                .set(ORGANISATIONS.NAME, org.getName())
                .set(ORGANISATIONS.SLUG, org.getSlug().toUpperCase())
                .set(ORGANISATIONS.DELETED_AT, toLocalDateTime(org.getDeletedAt()))
                .where(ORGANISATIONS.ID.eq(org.getId()))
                .execute();
        return findById(org.getId()).orElseThrow();
    }

    public void saveMember(UUID organisationId, UUID userId, OrganisationRole role) {
        dsl.insertInto(ORGANISATION_MEMBERS)
                .set(ORGANISATION_MEMBERS.ORGANISATION_ID, organisationId)
                .set(ORGANISATION_MEMBERS.USER_ID, userId)
                .set(ORGANISATION_MEMBERS.ORG_ROLE, role.name())
                .onDuplicateKeyIgnore()
                .execute();
    }

    public Optional<OrganisationRole> findMemberRole(UUID organisationId, UUID userId) {
        return dsl.select(ORGANISATION_MEMBERS.ORG_ROLE)
                .from(ORGANISATION_MEMBERS)
                .where(ORGANISATION_MEMBERS.ORGANISATION_ID.eq(organisationId))
                .and(ORGANISATION_MEMBERS.USER_ID.eq(userId))
                .fetchOptional(r -> OrganisationRole.valueOf(r.get(ORGANISATION_MEMBERS.ORG_ROLE)));
    }

    public int countMembers(UUID organisationId) {
        return dsl.fetchCount(
                dsl.selectFrom(ORGANISATION_MEMBERS)
                        .where(ORGANISATION_MEMBERS.ORGANISATION_ID.eq(organisationId)));
    }

    public List<OrgMemberModel> findMembers(UUID organisationId) {
        return dsl.select(
                        ORGANISATION_MEMBERS.ORGANISATION_ID,
                        ORGANISATION_MEMBERS.USER_ID,
                        USERS.NAME.as(MEMBER_NAME),
                        USERS.EMAIL.as(MEMBER_EMAIL),
                        ORGANISATION_MEMBERS.ORG_ROLE,
                        ORGANISATION_MEMBERS.JOINED_AT)
                .from(ORGANISATION_MEMBERS)
                .join(USERS).on(ORGANISATION_MEMBERS.USER_ID.eq(USERS.ID))
                .where(ORGANISATION_MEMBERS.ORGANISATION_ID.eq(organisationId))
                .orderBy(ORGANISATION_MEMBERS.JOINED_AT.asc())
                .fetch(r -> OrgMemberModel.builder()
                        .organisationId(r.get(ORGANISATION_MEMBERS.ORGANISATION_ID))
                        .userId(r.get(ORGANISATION_MEMBERS.USER_ID))
                        .userName(r.get(MEMBER_NAME, String.class))
                        .userEmail(r.get(MEMBER_EMAIL, String.class))
                        .role(OrganisationRole.valueOf(r.get(ORGANISATION_MEMBERS.ORG_ROLE)))
                        .joinedAt(toInstant(r.get(ORGANISATION_MEMBERS.JOINED_AT)))
                        .build());
    }

    public Optional<OrgMemberModel> findMember(UUID organisationId, UUID userId) {
        return dsl.select(
                        ORGANISATION_MEMBERS.ORGANISATION_ID,
                        ORGANISATION_MEMBERS.USER_ID,
                        USERS.NAME.as(MEMBER_NAME),
                        USERS.EMAIL.as(MEMBER_EMAIL),
                        ORGANISATION_MEMBERS.ORG_ROLE,
                        ORGANISATION_MEMBERS.JOINED_AT)
                .from(ORGANISATION_MEMBERS)
                .join(USERS).on(ORGANISATION_MEMBERS.USER_ID.eq(USERS.ID))
                .where(ORGANISATION_MEMBERS.ORGANISATION_ID.eq(organisationId))
                .and(ORGANISATION_MEMBERS.USER_ID.eq(userId))
                .fetchOptional(r -> OrgMemberModel.builder()
                        .organisationId(r.get(ORGANISATION_MEMBERS.ORGANISATION_ID))
                        .userId(r.get(ORGANISATION_MEMBERS.USER_ID))
                        .userName(r.get(MEMBER_NAME, String.class))
                        .userEmail(r.get(MEMBER_EMAIL, String.class))
                        .role(OrganisationRole.valueOf(r.get(ORGANISATION_MEMBERS.ORG_ROLE)))
                        .joinedAt(toInstant(r.get(ORGANISATION_MEMBERS.JOINED_AT)))
                        .build());
    }

    public boolean existsMember(UUID organisationId, UUID userId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(ORGANISATION_MEMBERS)
                        .where(ORGANISATION_MEMBERS.ORGANISATION_ID.eq(organisationId))
                        .and(ORGANISATION_MEMBERS.USER_ID.eq(userId)));
    }

    public void updateMemberRole(UUID organisationId, UUID userId, OrganisationRole role) {
        dsl.update(ORGANISATION_MEMBERS)
                .set(ORGANISATION_MEMBERS.ORG_ROLE, role.name())
                .where(ORGANISATION_MEMBERS.ORGANISATION_ID.eq(organisationId))
                .and(ORGANISATION_MEMBERS.USER_ID.eq(userId))
                .execute();
    }

    public void deleteMember(UUID organisationId, UUID userId) {
        dsl.deleteFrom(ORGANISATION_MEMBERS)
                .where(ORGANISATION_MEMBERS.ORGANISATION_ID.eq(organisationId))
                .and(ORGANISATION_MEMBERS.USER_ID.eq(userId))
                .execute();
    }

    // JOB-200: kept as standalone lookups (not on OrganisationModel/toModel) — this
    // field is only ever needed by Paddle-related code, not by the many general
    // org-display call sites already reading OrganisationModel.
    public Optional<String> findPaddleCustomerId(UUID orgId) {
        return dsl.select(ORGANISATIONS.PADDLE_CUSTOMER_ID)
                .from(ORGANISATIONS)
                .where(ORGANISATIONS.ID.eq(orgId))
                .fetchOptional(ORGANISATIONS.PADDLE_CUSTOMER_ID);
    }

    public void updatePaddleCustomerId(UUID orgId, String paddleCustomerId) {
        dsl.update(ORGANISATIONS)
                .set(ORGANISATIONS.PADDLE_CUSTOMER_ID, paddleCustomerId)
                .where(ORGANISATIONS.ID.eq(orgId))
                .execute();
    }

    public Optional<UUID> findIdByPaddleCustomerId(String paddleCustomerId) {
        return dsl.select(ORGANISATIONS.ID)
                .from(ORGANISATIONS)
                .where(ORGANISATIONS.PADDLE_CUSTOMER_ID.eq(paddleCustomerId))
                .fetchOptional(ORGANISATIONS.ID);
    }

    public void deleteAll() {
        dsl.deleteFrom(ORG_SUBSCRIPTION_ADDONS).execute();
        dsl.deleteFrom(ORG_SUBSCRIPTIONS).execute();
        dsl.deleteFrom(ORGANISATION_INVITES).execute();
        dsl.deleteFrom(ORGANISATION_MEMBERS).execute();
        dsl.deleteFrom(ORGANISATIONS).execute();
    }

    private org.jooq.SelectOnConditionStep<Record> selectWithCreator() {
        return dsl.select(of(
                        ORGANISATIONS.ID,
                        ORGANISATIONS.NAME,
                        ORGANISATIONS.SLUG,
                        ORGANISATIONS.CREATED_BY,
                        CREATOR_NAME,
                        ORGANISATIONS.CREATED_AT,
                        ORGANISATIONS.DELETED_AT))
                .from(ORGANISATIONS)
                .leftJoin(USERS).on(ORGANISATIONS.CREATED_BY.eq(USERS.ID));
    }

    private OrganisationModel toModel(Record r) {
        return OrganisationModel.builder()
                .id(r.get(ORGANISATIONS.ID))
                .name(r.get(ORGANISATIONS.NAME))
                .slug(r.get(ORGANISATIONS.SLUG))
                .createdBy(r.get(ORGANISATIONS.CREATED_BY))
                .createdByName(r.get(CREATOR_NAME))
                .createdAt(toInstant(r.get(ORGANISATIONS.CREATED_AT)))
                .deletedAt(toInstant(r.get(ORGANISATIONS.DELETED_AT)))
                .build();
    }
}
