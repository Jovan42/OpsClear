package com.opsclear.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.event.ApprovalDecidedEvent;
import com.opsclear.event.ApprovalRequestedEvent;
import com.opsclear.event.NoteAddedEvent;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationPage;
import com.opsclear.model.NotificationType;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Notification centre (ADR-0051).
 *
 * <p>Read side: list, unread count, mark read/unread, mark all read. Every method acts only on the caller's own
 * notifications, and the repository applies the read-time visibility filter (still a member of the notification's
 * organisation; for project-scoped ones, still a project member and the project not deleted).
 *
 * <p>Write side (fan-out): the {@code notifyXxx} methods, called by {@code NotificationEventListener} after the
 * triggering transaction commits. Each resolves its type's recipients, then {@link #fanOut} removes the actor,
 * de-duplicates, applies the job visibility filter and each recipient's mutes, and batch-inserts one notification per
 * remaining recipient with a payload snapshot. Recipients are resolved at fan-out time, from current data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationSettingsService notificationSettingsService;
    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * The caller's notifications, newest first. {@code unread} true returns only unread ones; {@code type} null
     * means any type. {@code page} is zero-based (default 0), {@code size} 1 to 100 (default 20).
     */
    @Transactional(readOnly = true)
    public NotificationPage list(UUID callerId, Boolean unread, NotificationType type, Integer page, Integer size) {
        int p = page != null ? page : DEFAULT_PAGE;
        int s = size != null ? size : DEFAULT_SIZE;
        requireValidPage(p);
        requireValidSize(s);
        boolean unreadOnly = Boolean.TRUE.equals(unread);
        return NotificationPage.builder()
                .items(notificationRepository.findVisibleByRecipient(callerId, unreadOnly, type, (long) p * s, s))
                .page(p)
                .size(s)
                .totalElements(notificationRepository.countVisibleByRecipient(callerId, unreadOnly, type))
                .build();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID callerId) {
        return notificationRepository.countVisibleByRecipient(callerId, true, null);
    }

    /**
     * Marks one of the caller's notifications read or unread. Another user's ID, an unknown ID or a notification
     * hidden by the visibility filter is a 404 (not 403), so existence isn't revealed. Marking an already read
     * notification read keeps its original {@code read_at}.
     */
    @Transactional
    public NotificationModel markRead(UUID callerId, UUID id, boolean read) {
        NotificationModel notification = requireOwnNotification(callerId, id);
        if (read != notification.isRead()) {
            notificationRepository.updateReadAt(id, read ? Instant.now() : null);
            // Hook point for the SSE stream job: push a read-state event to the caller's other open streams here.
        }
        return requireOwnNotification(callerId, id);
    }

    /** Marks all of the caller's visible unread notifications read. Returns how many changed. */
    @Transactional
    public int markAllRead(UUID callerId) {
        int updated = notificationRepository.markAllRead(callerId);
        log.info("Marked {} notification(s) read for user {}", updated, callerId);
        // Hook point for the SSE stream job: push a read-state {all: true} event to the caller's open streams here.
        return updated;
    }

    // --- Fan-out ---
    // REQUIRES_NEW: these run from an after-commit callback (inline when the executor is synchronous, as in tests).
    // A new transaction makes the inserts commit on their own instead of joining the already-committed one.

    /** {@code APPROVAL_REQUESTED}: the project's Owners and Admins, who can decide it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyApprovalRequested(ApprovalRequestedEvent event) {
        return fanOutForJob(NotificationType.APPROVAL_REQUESTED, event.jobId(), scope -> FanOut.builder()
                .actorId(event.actorId())
                .approvalId(event.approvalId())
                .candidates(scope.ownerAndAdminIds())
                .payload(scope.payload(actorName(event.actorId()))
                        .put(NotificationPayload.DESCRIPTION, event.description()))
                .build());
    }

    /** {@code APPROVAL_DECIDED}: the person who requested the approval. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyApprovalDecided(ApprovalDecidedEvent event) {
        return fanOutForJob(NotificationType.APPROVAL_DECIDED, event.jobId(), scope -> FanOut.builder()
                .actorId(event.actorId())
                .approvalId(event.approvalId())
                .candidates(List.of(event.requesterId()))
                .payload(scope.payload(actorName(event.actorId()))
                        .put(NotificationPayload.DECISION, event.decision().name())
                        .put(NotificationPayload.COMMENT, event.comment()))
                .build());
    }

    /**
     * {@code NOTE_ADDED}: the job's assignee and everyone who has already written a note on it (thread participants).
     * The visibility filter drops participants who can no longer see the job (e.g. an unassigned Member).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyNoteAdded(NoteAddedEvent event) {
        return fanOutForJob(NotificationType.NOTE_ADDED, event.jobId(), scope -> {
            List<UUID> candidates = new ArrayList<>();
            candidates.add(scope.job().getAssignedTo());
            candidates.addAll(noteRepository.findDistinctAuthorIdsByJobId(scope.job().getId()));
            return FanOut.builder()
                    .actorId(event.actorId())
                    .candidates(candidates)
                    .payload(scope.payload(actorName(event.actorId())))
                    .build();
        });
    }

    /**
     * Loads the job, its project and the project's Owners/Admins, then fans out the request built from them. A job
     * or project deleted between the action and the fan-out notifies no one.
     */
    private List<NotificationModel> fanOutForJob(NotificationType type, UUID jobId,
                                                 Function<JobScope, FanOut> request) {
        Optional<JobModel> job = jobRepository.findByIdAndDeletedAtIsNull(jobId);
        Optional<ProjectModel> project =
                job.flatMap(j -> projectRepository.findByIdAndDeletedAtIsNull(j.getProjectId()));
        if (project.isEmpty()) {
            log.info("Skipped {} notifications: job {} or its project no longer exists", type, jobId);
            return List.of();
        }
        List<UUID> ownerAndAdminIds = projectMemberRepository.findOwnerAndAdminUserIds(project.get().getId());
        JobScope scope = new JobScope(job.get(), project.get(), ownerAndAdminIds,
                visibleUserIds(job.get(), ownerAndAdminIds));
        return fanOut(type, scope, request.apply(scope));
    }

    // Who can currently see the job: Owners/Admins plus the assignee. Removing a member doesn't unassign their jobs,
    // so the assignee only counts while still a project member.
    private Set<UUID> visibleUserIds(JobModel job, List<UUID> ownerAndAdminIds) {
        Set<UUID> visible = new LinkedHashSet<>(ownerAndAdminIds);
        UUID assigneeId = job.getAssignedTo();
        if (assigneeId != null && projectMemberRepository.existsByProjectIdAndUserId(job.getProjectId(), assigneeId)) {
            visible.add(assigneeId);
        }
        return visible;
    }

    /**
     * The shared pipeline: candidates minus the actor, de-duplicated (one notification per person per action), only
     * those who can currently see the job, minus those who muted the type; then one batch insert.
     */
    private List<NotificationModel> fanOut(NotificationType type, JobScope scope, FanOut request) {
        Set<UUID> recipients = new LinkedHashSet<>(request.candidates());
        recipients.remove(null); // an unassigned job yields no assignee
        recipients.remove(request.actorId());
        recipients.retainAll(scope.visibleUserIds());
        recipients.removeIf(userId -> notificationSettingsService.getMutedTypes(userId).contains(type));
        if (recipients.isEmpty()) {
            return List.of();
        }

        String payload = request.payload().toJson(objectMapper);
        List<NotificationModel> notifications = recipients.stream()
                .map(recipientId -> NotificationModel.builder()
                        .orgId(scope.project().getOrganisationId())
                        .recipientId(recipientId)
                        .type(type)
                        .actorId(request.actorId())
                        .projectId(scope.project().getId())
                        .jobId(scope.job().getId())
                        .approvalId(request.approvalId())
                        .payload(payload)
                        .build())
                .toList();
        List<NotificationModel> saved = notificationRepository.insertBatch(notifications);
        log.info("Created {} {} notification(s) for job {}", saved.size(), type, scope.job().getId());
        // Hook point for the SSE stream job: push a "notification" event to each recipient's open streams here.
        return saved;
    }

    private String actorName(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return userRepository.findById(actorId).map(UserModel::getName).orElse(null);
    }

    /**
     * The job a notification is about, its project, its Owners/Admins, and who can currently see it (Owners/Admins
     * plus the assignee while they're still a project member).
     */
    private record JobScope(JobModel job, ProjectModel project, List<UUID> ownerAndAdminIds,
                            Set<UUID> visibleUserIds) {

        NotificationPayload payload(String actorName) {
            return NotificationPayload.forJob(job, project, actorName);
        }
    }

    /** One action to fan out: who did it (null for system events), who might hear about it, and what to show. */
    @Builder
    private record FanOut(UUID actorId, UUID approvalId, Collection<UUID> candidates, NotificationPayload payload) {
    }

    // --- Guards ---

    private NotificationModel requireOwnNotification(UUID callerId, UUID id) {
        return notificationRepository.findVisibleByIdAndRecipient(id, callerId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Notification.NOT_FOUND));
    }

    private static void requireValidPage(int page) {
        if (page < 0) {
            throw new BadRequestException(ErrorMessages.Notification.INVALID_PAGE);
        }
    }

    private static void requireValidSize(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException(String.format(ErrorMessages.Notification.INVALID_SIZE, MAX_SIZE));
        }
    }
}
