package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.Projects;
import com.opsclear.generated.jooq.tables.records.NotificationsRecord;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStep10;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static com.opsclear.generated.jooq.Tables.ORGANISATION_MEMBERS;
import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static com.opsclear.generated.jooq.Tables.PROJECT_MEMBERS;
import static com.opsclear.generated.jooq.Tables.USERS;

/**
 * Notifications store (ADR-0051). Every read, and mark-all-read, applies the visibility filter: a notification is
 * visible only while its recipient is still a member of its organisation, and a project-scoped one only while the
 * recipient is still a member of that project and the project isn't soft-deleted.
 */
@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private static final Field<String> ACTOR_NAME = USERS.NAME.as("actor_name");
    private static final Field<String> PROJECT_FRIENDLY_ID = PROJECTS.FRIENDLY_ID.as("project_friendly_id");
    private static final Field<String> JOB_FRIENDLY_ID = JOBS.FRIENDLY_ID.as("job_friendly_id");
    private static final String EMPTY_PAYLOAD = "{}";
    // Aliased so the visibility subquery doesn't clash with the outer LEFT JOIN on projects.
    private static final Projects VISIBLE_PROJECT = PROJECTS.as("visible_project");

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private NotificationModel toModel(Record r) {
        return NotificationModel.builder()
                .id(r.get(NOTIFICATIONS.ID))
                .orgId(r.get(NOTIFICATIONS.ORG_ID))
                .recipientId(r.get(NOTIFICATIONS.RECIPIENT_ID))
                .type(NotificationType.valueOf(r.get(NOTIFICATIONS.TYPE)))
                .actorId(r.get(NOTIFICATIONS.ACTOR_ID))
                .actorName(r.get(ACTOR_NAME))
                .projectId(r.get(NOTIFICATIONS.PROJECT_ID))
                .projectFriendlyId(r.get(PROJECT_FRIENDLY_ID))
                .jobId(r.get(NOTIFICATIONS.JOB_ID))
                .jobFriendlyId(r.get(JOB_FRIENDLY_ID))
                .approvalId(r.get(NOTIFICATIONS.APPROVAL_ID))
                .payload(r.get(NOTIFICATIONS.PAYLOAD).data()) // NOT NULL, defaults to {}
                .readAt(toInstant(r.get(NOTIFICATIONS.READ_AT)))
                .createdAt(toInstant(r.get(NOTIFICATIONS.CREATED_AT)))
                .build();
    }

    // --- Reads (visibility-filtered) ---

    /** The recipient's visible notifications, newest first. {@code type} null means any type. */
    public List<NotificationModel> findVisibleByRecipient(
            UUID recipientId, boolean unreadOnly, NotificationType type, long offset, int limit) {
        return selectWithJoins()
                .where(visibleTo(recipientId))
                .and(filters(unreadOnly, type))
                .orderBy(NOTIFICATIONS.CREATED_AT.desc(), NOTIFICATIONS.ID.desc())
                .offset(offset)
                .limit(limit)
                .fetch()
                .map(this::toModel);
    }

    public long countVisibleByRecipient(UUID recipientId, boolean unreadOnly, NotificationType type) {
        return dsl.selectCount()
                .from(NOTIFICATIONS)
                .where(visibleTo(recipientId))
                .and(filters(unreadOnly, type))
                .fetchSingle()
                .value1();
    }

    public Optional<NotificationModel> findVisibleByIdAndRecipient(UUID id, UUID recipientId) {
        return selectWithJoins()
                .where(NOTIFICATIONS.ID.eq(id))
                .and(visibleTo(recipientId))
                .fetchOptional()
                .map(this::toModel);
    }

    // --- Writes ---

    /** Sets or clears {@code read_at}; {@code readAt} null marks the notification unread. */
    public void updateReadAt(UUID id, Instant readAt) {
        dsl.update(NOTIFICATIONS)
                .set(NOTIFICATIONS.READ_AT, toLocalDateTime(readAt))
                .where(NOTIFICATIONS.ID.eq(id))
                .execute();
    }

    /** Marks every visible unread notification of the recipient read. Returns the number of rows changed. */
    public int markAllRead(UUID recipientId) {
        return dsl.update(NOTIFICATIONS)
                .set(NOTIFICATIONS.READ_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(visibleTo(recipientId))
                .and(NOTIFICATIONS.READ_AT.isNull())
                .execute();
    }

    /**
     * Inserts notifications in one statement (fan-out, Backend 2) and returns them re-fetched with their
     * JOIN fields, newest first. {@code createdAt} and {@code readAt} are written when set; a null
     * {@code createdAt} uses the current time.
     */
    public List<NotificationModel> insertBatch(List<NotificationModel> notifications) {
        if (notifications.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        InsertValuesStep10<NotificationsRecord, UUID, UUID, String, UUID, UUID, UUID, UUID, JSONB,
                LocalDateTime, LocalDateTime> insert = dsl.insertInto(NOTIFICATIONS,
                NOTIFICATIONS.ORG_ID, NOTIFICATIONS.RECIPIENT_ID, NOTIFICATIONS.TYPE, NOTIFICATIONS.ACTOR_ID,
                NOTIFICATIONS.PROJECT_ID, NOTIFICATIONS.JOB_ID, NOTIFICATIONS.APPROVAL_ID, NOTIFICATIONS.PAYLOAD,
                NOTIFICATIONS.READ_AT, NOTIFICATIONS.CREATED_AT);
        for (NotificationModel n : notifications) {
            insert = insert.values(n.getOrgId(), n.getRecipientId(), n.getType().name(), n.getActorId(),
                    n.getProjectId(), n.getJobId(), n.getApprovalId(),
                    JSONB.valueOf(n.getPayload() != null ? n.getPayload() : EMPTY_PAYLOAD),
                    toLocalDateTime(n.getReadAt()),
                    n.getCreatedAt() != null ? toLocalDateTime(n.getCreatedAt()) : now);
        }
        List<UUID> ids = insert.returning(NOTIFICATIONS.ID).fetch().map(NotificationsRecord::getId);
        return selectWithJoins()
                .where(NOTIFICATIONS.ID.in(ids))
                .orderBy(NOTIFICATIONS.CREATED_AT.desc(), NOTIFICATIONS.ID.desc())
                .fetch()
                .map(this::toModel);
    }

    // --- Retention ---

    /** Deletes read notifications created before {@code cutoff}. Returns the number deleted. */
    public int deleteReadCreatedBefore(Instant cutoff) {
        return dsl.deleteFrom(NOTIFICATIONS)
                .where(NOTIFICATIONS.READ_AT.isNotNull())
                .and(NOTIFICATIONS.CREATED_AT.lt(toLocalDateTime(cutoff)))
                .execute();
    }

    /** Deletes every notification (read or not) created before {@code cutoff}. Returns the number deleted. */
    public int deleteCreatedBefore(Instant cutoff) {
        return dsl.deleteFrom(NOTIFICATIONS)
                .where(NOTIFICATIONS.CREATED_AT.lt(toLocalDateTime(cutoff)))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(NOTIFICATIONS).execute();
    }

    // --- Query helpers ---

    private SelectOnConditionStep<Record> selectWithJoins() {
        return dsl.select(NOTIFICATIONS.fields())
                .select(ACTOR_NAME, PROJECT_FRIENDLY_ID, JOB_FRIENDLY_ID)
                .from(NOTIFICATIONS)
                .leftJoin(USERS).on(NOTIFICATIONS.ACTOR_ID.eq(USERS.ID))
                .leftJoin(PROJECTS).on(NOTIFICATIONS.PROJECT_ID.eq(PROJECTS.ID))
                .leftJoin(JOBS).on(NOTIFICATIONS.JOB_ID.eq(JOBS.ID));
    }

    // A notification is visible while its recipient is still a member of the notification's organisation and, for a
    // project-scoped one, still a member of that project and the project isn't soft-deleted.
    private static Condition visibleTo(UUID recipientId) {
        return NOTIFICATIONS.RECIPIENT_ID.eq(recipientId)
                .and(DSL.exists(DSL.selectOne()
                        .from(ORGANISATION_MEMBERS)
                        .where(ORGANISATION_MEMBERS.ORGANISATION_ID.eq(NOTIFICATIONS.ORG_ID))
                        .and(ORGANISATION_MEMBERS.USER_ID.eq(NOTIFICATIONS.RECIPIENT_ID))))
                .and(NOTIFICATIONS.PROJECT_ID.isNull()
                        .or(DSL.exists(DSL.selectOne()
                                .from(PROJECT_MEMBERS)
                                .join(VISIBLE_PROJECT).on(VISIBLE_PROJECT.ID.eq(PROJECT_MEMBERS.PROJECT_ID))
                                .where(PROJECT_MEMBERS.PROJECT_ID.eq(NOTIFICATIONS.PROJECT_ID))
                                .and(PROJECT_MEMBERS.USER_ID.eq(NOTIFICATIONS.RECIPIENT_ID))
                                .and(VISIBLE_PROJECT.DELETED_AT.isNull()))));
    }

    private static Condition filters(boolean unreadOnly, NotificationType type) {
        Condition unread = unreadOnly ? NOTIFICATIONS.READ_AT.isNull() : DSL.noCondition();
        Condition ofType = type != null ? NOTIFICATIONS.TYPE.eq(type.name()) : DSL.noCondition();
        return unread.and(ofType);
    }
}
