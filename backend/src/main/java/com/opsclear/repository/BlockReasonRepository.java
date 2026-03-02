package com.opsclear.repository;

import com.opsclear.model.BlockReasonModel;
import com.opsclear.generated.jooq.tables.records.ProjectBlockReasonsRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.PROJECT_BLOCK_REASONS;

@Repository
@RequiredArgsConstructor
public class BlockReasonRepository {

    private final DSLContext dsl;

    public List<BlockReasonModel> findActiveByProjectId(UUID projectId) {
        return dsl.selectFrom(PROJECT_BLOCK_REASONS)
                .where(PROJECT_BLOCK_REASONS.PROJECT_ID.eq(projectId))
                .and(PROJECT_BLOCK_REASONS.DELETED_AT.isNull())
                .orderBy(PROJECT_BLOCK_REASONS.CREATED_AT.asc())
                .fetch()
                .map(this::toModel);
    }

    public Optional<BlockReasonModel> findByIdAndDeletedAtIsNull(UUID id) {
        return dsl.selectFrom(PROJECT_BLOCK_REASONS)
                .where(PROJECT_BLOCK_REASONS.ID.eq(id))
                .and(PROJECT_BLOCK_REASONS.DELETED_AT.isNull())
                .fetchOptional()
                .map(this::toModel);
    }

    /**
     * Find or create a block reason for the project.
     * If a soft-deleted record with the same text exists, it is restored.
     */
    public BlockReasonModel findOrCreate(UUID projectId, String reason) {
        UUID id = dsl.insertInto(PROJECT_BLOCK_REASONS)
                .set(PROJECT_BLOCK_REASONS.PROJECT_ID, projectId)
                .set(PROJECT_BLOCK_REASONS.REASON, reason)
                .set(PROJECT_BLOCK_REASONS.CREATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .onConflict(PROJECT_BLOCK_REASONS.PROJECT_ID, PROJECT_BLOCK_REASONS.REASON)
                .doUpdate()
                .set(PROJECT_BLOCK_REASONS.DELETED_AT, (LocalDateTime) null)
                .returning(PROJECT_BLOCK_REASONS.ID)
                .fetchSingle()
                .getId();

        return dsl.selectFrom(PROJECT_BLOCK_REASONS)
                .where(PROJECT_BLOCK_REASONS.ID.eq(id))
                .fetchSingle(this::toModel);
    }

    public void softDelete(UUID id) {
        dsl.update(PROJECT_BLOCK_REASONS)
                .set(PROJECT_BLOCK_REASONS.DELETED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(PROJECT_BLOCK_REASONS.ID.eq(id))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(PROJECT_BLOCK_REASONS).execute();
    }

    private BlockReasonModel toModel(ProjectBlockReasonsRecord r) {
        return BlockReasonModel.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .reason(r.getReason())
                .createdAt(toInstant(r.getCreatedAt()))
                .deletedAt(toInstant(r.getDeletedAt()))
                .build();
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
