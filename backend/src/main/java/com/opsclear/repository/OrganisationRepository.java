package com.opsclear.repository;

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
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORGANISATIONS;
import static com.opsclear.generated.jooq.Tables.ORGANISATION_MEMBERS;
import static com.opsclear.generated.jooq.Tables.USERS;
import static java.util.List.of;

@Repository
@RequiredArgsConstructor
public class OrganisationRepository {

    private static final Field<String> CREATOR_NAME = USERS.NAME.as("creator_name");

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
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

    public void deleteAll() {
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
