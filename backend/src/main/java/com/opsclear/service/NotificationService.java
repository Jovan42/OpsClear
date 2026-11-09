package com.opsclear.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.event.ApprovalDecidedEvent;
import com.opsclear.event.ApprovalRequestedEvent;
import com.opsclear.event.BlockerClearedEvent;
import com.opsclear.event.FeedbackResolvedEvent;
import com.opsclear.event.InviteAcceptedEvent;
import com.opsclear.event.JobAssignedEvent;
import com.opsclear.event.JobBlockedEvent;
import com.opsclear.event.JobDeletedEvent;
import com.opsclear.event.JobStatusChangedEvent;
import com.opsclear.event.JobUpdatedEvent;
import com.opsclear.event.NoteAddedEvent;
import com.opsclear.event.ProjectMemberAddedEvent;
import com.opsclear.event.SubscriptionPastDueEvent;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationPage;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrgMemberModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.NotificationTarget.JobScope;
import com.opsclear.service.NotificationTarget.OrgMembers;
import com.opsclear.service.NotificationTarget.OrgScope;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    private final OrganisationRepository organisationRepository;

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

    // --- Job lifecycle fan-out (JOB-277) ---

    /**
     * {@code JOB_ASSIGNED}: the new assignee, only while they're still the job's assignee (a later reassignment sends
     * its own event). The payload carries the deadline and priority, since an edit that changes the assignee and the
     * deadline sends only this type.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyJobAssigned(JobAssignedEvent event) {
        return fanOutForJob(NotificationType.JOB_ASSIGNED, event.jobId(), scope -> FanOut.builder()
                .actorId(event.actorId())
                .candidates(event.assigneeId().equals(scope.job().getAssignedTo())
                        ? List.of(event.assigneeId()) : List.of())
                .payload(scope.payload(actorName(event.actorId()))
                        .put(NotificationPayload.DEADLINE, isoInstant(scope.job().getDeadline()))
                        .put(NotificationPayload.PRIORITY, scope.job().getPriority()))
                .build());
    }

    /** {@code JOB_STATUS_CHANGED}: the assignee and the project's Owners/Admins, with the from/to status. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyJobStatusChanged(JobStatusChangedEvent event) {
        return fanOutForJob(NotificationType.JOB_STATUS_CHANGED, event.jobId(), scope -> FanOut.builder()
                .actorId(event.actorId())
                .candidates(assigneeAndOwnersAndAdmins(scope))
                .payload(scope.payload(actorName(event.actorId()))
                        .put(NotificationPayload.FROM_STATUS, event.fromStatus())
                        .put(NotificationPayload.TO_STATUS, event.toStatus()))
                .build());
    }

    /** {@code JOB_BLOCKED}: the assignee and the project's Owners/Admins, with the block reason. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyJobBlocked(JobBlockedEvent event) {
        return fanOutForJob(NotificationType.JOB_BLOCKED, event.jobId(), scope -> FanOut.builder()
                .actorId(event.actorId())
                .candidates(assigneeAndOwnersAndAdmins(scope))
                .payload(scope.payload(actorName(event.actorId()))
                        .put(NotificationPayload.BLOCK_REASON, event.blockReason()))
                .build());
    }

    /**
     * {@code BLOCKER_CLEARED}: the assignee of each waiting job that isn't Completed, one notification per waiting
     * job (about that job), with the blocker's friendly ID and title. Nothing is sent if the blocker was deleted or
     * reopened before the fan-out.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyBlockerCleared(BlockerClearedEvent event) {
        Optional<JobModel> blocker = jobRepository.findByIdAndDeletedAtIsNull(event.blockerJobId())
                .filter(j -> j.getStatus() == JobStatus.COMPLETED);
        if (blocker.isEmpty()) {
            log.info("Skipped BLOCKER_CLEARED notifications: job {} is no longer completed", event.blockerJobId());
            return List.of();
        }
        String actorName = actorName(event.actorId());
        List<NotificationModel> saved = new ArrayList<>();
        for (UUID waitingJobId : event.waitingJobIds()) {
            saved.addAll(fanOutForJob(NotificationType.BLOCKER_CLEARED, waitingJobId, scope -> FanOut.builder()
                    .actorId(event.actorId())
                    .candidates(scope.job().getStatus() == JobStatus.COMPLETED
                            ? List.of() : Collections.singletonList(scope.job().getAssignedTo()))
                    .payload(scope.payload(actorName)
                            .put(NotificationPayload.BLOCKER_JOB_FRIENDLY_ID, blocker.get().getFriendlyId())
                            .put(NotificationPayload.BLOCKER_JOB_TITLE, blocker.get().getTitle()))
                    .build()));
        }
        return saved;
    }

    /** {@code JOB_UPDATED}: the assignee, with the changed field(s) and their new values. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyJobUpdated(JobUpdatedEvent event) {
        return fanOutForJob(NotificationType.JOB_UPDATED, event.jobId(), scope -> FanOut.builder()
                .actorId(event.actorId())
                .candidates(Collections.singletonList(scope.job().getAssignedTo()))
                .payload(scope.payload(actorName(event.actorId()))
                        .put(NotificationPayload.CHANGED_FIELDS, event.changedFields())
                        .put(NotificationPayload.DEADLINE, isoInstant(event.deadline()))
                        .put(NotificationPayload.PRIORITY, event.priority()))
                .build());
    }

    /**
     * {@code JOB_DELETED}: the (soft-deleted) job's assignee. Unlike the other types it reads the deleted job, so the
     * payload snapshot lets the notification render after deletion. A deleted project notifies no one.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyJobDeleted(JobDeletedEvent event) {
        return fanOutForJob(NotificationType.JOB_DELETED, event.jobId(), jobRepository.findById(event.jobId()),
                scope -> FanOut.builder()
                        .actorId(event.actorId())
                        .candidates(Collections.singletonList(scope.job().getAssignedTo()))
                        .payload(scope.payload(actorName(event.actorId())))
                        .build());
    }

    // The assignee (null when unassigned, dropped by the pipeline) plus the project's Owners and Admins.
    private static List<UUID> assigneeAndOwnersAndAdmins(JobScope scope) {
        List<UUID> candidates = new ArrayList<>();
        candidates.add(scope.job().getAssignedTo());
        candidates.addAll(scope.ownerAndAdminIds());
        return candidates;
    }

    private static String isoInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    // --- Project, organisation and billing types (JOB-279) ---

    /**
     * {@code PROJECT_MEMBER_ADDED}: the added user, with the role they were given. Project-scoped, no job. A project
     * deleted, or the user removed again, before the fan-out notifies no one.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyProjectMemberAdded(ProjectMemberAddedEvent event) {
        Optional<ProjectModel> project = projectRepository.findByIdAndDeletedAtIsNull(event.projectId());
        if (project.isEmpty()) {
            log.info("Skipped {} notifications: project {} no longer exists",
                    NotificationType.PROJECT_MEMBER_ADDED, event.projectId());
            return List.of();
        }
        Set<UUID> visible = projectMemberRepository.existsByProjectIdAndUserId(event.projectId(), event.userId())
                ? Set.of(event.userId()) : Set.of();
        OrgScope scope = new OrgScope(project.get().getOrganisationId(), project.get().getId(), visible);
        return fanOut(NotificationType.PROJECT_MEMBER_ADDED, scope, FanOut.builder()
                .actorId(event.actorId())
                .candidates(List.of(event.userId()))
                .payload(NotificationPayload.forProject(project.get(), actorName(event.actorId()))
                        .put(NotificationPayload.ROLE, event.role().name()))
                .build());
    }

    /**
     * {@code INVITE_ACCEPTED}: the inviter. If the invite has no inviter, or they're no longer in the organisation,
     * the organisation's Owners and Admins instead. Organisation-level (no project). The actor is the new member.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyInviteAccepted(InviteAcceptedEvent event) {
        return fanOutForOrg(NotificationType.INVITE_ACCEPTED, event.orgId(), org -> {
            UUID inviterId = event.inviterId();
            Collection<UUID> candidates = inviterId != null && org.memberIds().contains(inviterId)
                    ? List.of(inviterId) : org.ownerAndAdminIds();
            Optional<OrgMemberModel> member = org.member(event.actorId());
            String memberName = member.map(OrgMemberModel::getUserName).orElse(null);
            return FanOut.builder()
                    .actorId(event.actorId())
                    .candidates(candidates)
                    .payload(org.payload(memberName)
                            .put(NotificationPayload.MEMBER_NAME, memberName)
                            .put(NotificationPayload.MEMBER_EMAIL,
                                    member.map(OrgMemberModel::getUserEmail).orElse(null)))
                    .build();
        });
    }

    /**
     * {@code FEEDBACK_RESOLVED}: the submitter, with the outcome and the credit amount if credited. A super-admin
     * action, so there's no actor (shown as "OpsClear").
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifyFeedbackResolved(FeedbackResolvedEvent event) {
        return fanOutForOrg(NotificationType.FEEDBACK_RESOLVED, event.orgId(), org -> FanOut.builder()
                .candidates(List.of(event.submitterId()))
                .payload(org.payload(null)
                        .put(NotificationPayload.FEEDBACK_TITLE, event.title())
                        .put(NotificationPayload.OUTCOME, event.outcome().name())
                        .put(NotificationPayload.CREDIT_AMOUNT, event.creditAmount()))
                .build());
    }

    /**
     * {@code SUBSCRIPTION_PAST_DUE}: the organisation's Owners and Admins. A Paddle webhook, so there's no actor. This
     * type can't be muted: {@link #fanOut} ignores mutes for it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationModel> notifySubscriptionPastDue(SubscriptionPastDueEvent event) {
        return fanOutForOrg(NotificationType.SUBSCRIPTION_PAST_DUE, event.orgId(), org -> FanOut.builder()
                .candidates(org.ownerAndAdminIds())
                .payload(org.payload(null))
                .build());
    }

    /**
     * Loads the organisation and its members, then fans out the request built from them. Organisation-level
     * notifications have no project; only current members can receive them. An organisation deleted between the
     * action and the fan-out notifies no one.
     */
    private List<NotificationModel> fanOutForOrg(NotificationType type, UUID orgId,
                                                 Function<OrgMembers, FanOut> request) {
        Optional<OrganisationModel> org = organisationRepository.findByIdAndDeletedAtIsNull(orgId);
        if (org.isEmpty()) {
            log.info("Skipped {} notifications: organisation {} no longer exists", type, orgId);
            return List.of();
        }
        OrgMembers members = new OrgMembers(org.get(), organisationRepository.findMembers(orgId));
        return fanOut(type, new OrgScope(orgId, null, members.memberIds()), request.apply(members));
    }

    /**
     * Loads the job, its project and the project's Owners/Admins, then fans out the request built from them. A job
     * or project deleted between the action and the fan-out notifies no one.
     */
    private List<NotificationModel> fanOutForJob(NotificationType type, UUID jobId,
                                                 Function<JobScope, FanOut> request) {
        return fanOutForJob(type, jobId, jobRepository.findByIdAndDeletedAtIsNull(jobId), request);
    }

    private List<NotificationModel> fanOutForJob(NotificationType type, UUID jobId, Optional<JobModel> job,
                                                 Function<JobScope, FanOut> request) {
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
     * those who may currently receive it ({@link NotificationTarget#visibleUserIds}), minus those who muted the type
     * (skipped for types that can't be muted); then one batch insert.
     */
    private List<NotificationModel> fanOut(NotificationType type, NotificationTarget scope, FanOut request) {
        Set<UUID> recipients = new LinkedHashSet<>(request.candidates());
        recipients.remove(null); // an unassigned job yields no assignee
        recipients.remove(request.actorId());
        recipients.retainAll(scope.visibleUserIds());
        recipients.removeIf(userId -> type.isMutable()
                && notificationSettingsService.getMutedTypes(userId).contains(type));
        if (recipients.isEmpty()) {
            return List.of();
        }

        String payload = request.payload().toJson(objectMapper);
        List<NotificationModel> notifications = recipients.stream()
                .map(recipientId -> NotificationModel.builder()
                        .orgId(scope.orgId())
                        .recipientId(recipientId)
                        .type(type)
                        .actorId(request.actorId())
                        .projectId(scope.projectId())
                        .jobId(scope.jobId())
                        .approvalId(request.approvalId())
                        .payload(payload)
                        .build())
                .toList();
        List<NotificationModel> saved = notificationRepository.insertBatch(notifications);
        log.info("Created {} {} notification(s) for {}", saved.size(), type, scope.describe());
        // Hook point for the SSE stream job: push a "notification" event to each recipient's open streams here.
        return saved;
    }

    private String actorName(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return userRepository.findById(actorId).map(UserModel::getName).orElse(null);
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
