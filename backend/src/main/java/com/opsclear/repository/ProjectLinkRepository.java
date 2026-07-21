package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.ProjectLinksRecord;
import com.opsclear.model.ProjectLinkModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.PROJECT_LINKS;

@Repository
@RequiredArgsConstructor
public class ProjectLinkRepository {

    private final DSLContext dsl;

    private static Instant toInstant(OffsetDateTime odt) {
        return odt.toInstant();
    }

    public ProjectLinkModel save(ProjectLinkModel link) {
        if (link.getId() == null) {
            UUID id = dsl.insertInto(PROJECT_LINKS)
                    .set(PROJECT_LINKS.PROJECT_ID, link.getProjectId())
                    .set(PROJECT_LINKS.URL, link.getUrl())
                    .set(PROJECT_LINKS.LABEL, link.getLabel())
                    .set(PROJECT_LINKS.CREATED_BY, link.getCreatedBy())
                    .returning(PROJECT_LINKS.ID)
                    .fetchSingle()
                    .getId();
            return findById(id).orElseThrow();
        }
        dsl.update(PROJECT_LINKS)
                .set(PROJECT_LINKS.URL, link.getUrl())
                .set(PROJECT_LINKS.LABEL, link.getLabel())
                .set(PROJECT_LINKS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(PROJECT_LINKS.ID.eq(link.getId()))
                .execute();
        return findById(link.getId()).orElseThrow();
    }

    public Optional<ProjectLinkModel> findById(UUID id) {
        return dsl.selectFrom(PROJECT_LINKS)
                .where(PROJECT_LINKS.ID.eq(id))
                .fetchOptional(this::toModel);
    }

    public List<ProjectLinkModel> findByProjectId(UUID projectId) {
        return dsl.selectFrom(PROJECT_LINKS)
                .where(PROJECT_LINKS.PROJECT_ID.eq(projectId))
                .orderBy(PROJECT_LINKS.CREATED_AT.asc(), PROJECT_LINKS.ID.asc())
                .fetch()
                .map(this::toModel);
    }

    public List<ProjectLinkModel> findByProjectIds(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(PROJECT_LINKS)
                .where(PROJECT_LINKS.PROJECT_ID.in(projectIds))
                .orderBy(PROJECT_LINKS.CREATED_AT.asc(), PROJECT_LINKS.ID.asc())
                .fetch()
                .map(this::toModel);
    }

    public void deleteById(UUID id) {
        dsl.deleteFrom(PROJECT_LINKS).where(PROJECT_LINKS.ID.eq(id)).execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(PROJECT_LINKS).execute();
    }

    private ProjectLinkModel toModel(ProjectLinksRecord r) {
        return ProjectLinkModel.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .url(r.getUrl())
                .label(r.getLabel())
                .createdBy(r.getCreatedBy())
                .createdAt(toInstant(r.getCreatedAt()))
                .updatedAt(toInstant(r.getUpdatedAt()))
                .build();
    }
}
