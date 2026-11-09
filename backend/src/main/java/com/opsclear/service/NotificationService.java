package com.opsclear.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.event.ApprovalDecidedEvent;
import com.opsclear.event.ApprovalRequestedEvent;
import com.opsclear.event.FeedbackResolvedEvent;
import com.opsclear.event.InviteAcceptedEvent;
import com.opsclear.event.NoteAddedEvent;
import com.opsclear.event.ProjectMemberAddedEvent;
import com.opsclear.event.SubscriptionPastDueEvent;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationPage;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrgMemberModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrganisationRepository;
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
     * those who may currently receive it ({@link Target#visibleUserIds}), minus those who muted the type (skipped for
     * types that can't be muted); then one batch insert.
     */
    private List<NotificationModel> fanOut(NotificationType type, Target scope, FanOut request) {
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

    /**
     * The job a notification is about, its project, its Owners/Admins, and who can currently see it (Owners/Admins
     * plus the assignee while they're still a project member).
     */
    private record JobScope(JobModel job, ProjectModel project, List<UUID> ownerAndAdminIds,
                            Set<UUID> visibleUserIds) implements Target {

        NotificationPayload payload(String actorName) {
            return NotificationPayload.forJob(job, project, actorName);
        }

        @Override
        public UUID orgId() {
            return project.getOrganisationId();
        }

        @Override
        public UUID projectId() {
            return project.getId();
        }

        @Override
        public UUID jobId() {
            return job.getId();
        }

        @Override
        public String describe() {
            return "job " + job.getId();
        }
    }

    /**
     * Where a fan-out's notifications point (organisation, optional project and job) and who may receive them. The
     * pipeline in {@link #fanOut} only needs this, so job, project and organisation-level types share it.
     */
    private sealed interface Target permits JobScope, OrgScope {
        UUID orgId();

        UUID projectId();

        UUID jobId();

        Set<UUID> visibleUserIds();

        String describe();
    }

    /**
     * A notification without a job (JOB-279): organisation-level ({@code projectId} null) or project-level. The
     * caller decides who may receive it ({@code visibleUserIds}).
     */
    private record OrgScope(UUID orgId, UUID projectId, Set<UUID> visibleUserIds) implements Target {

        @Override
        public UUID jobId() {
            return null;
        }

        @Override
        public String describe() {
            return projectId != null ? "project " + projectId : "organisation " + orgId;
        }
    }

    /** An organisation and its current members, for resolving organisation-level recipients. */
    private record OrgMembers(OrganisationModel org, List<OrgMemberModel> members) {

        Set<UUID> memberIds() {
            Set<UUID> ids = new LinkedHashSet<>();
            members.forEach(m -> ids.add(m.getUserId()));
            return ids;
        }

        List<UUID> ownerAndAdminIds() {
            return members.stream()
                    .filter(m -> m.getRole() != OrganisationRole.MEMBER)
                    .map(OrgMemberModel::getUserId)
                    .toList();
        }

        Optional<OrgMemberModel> member(UUID userId) {
            return members.stream().filter(m -> m.getUserId().equals(userId)).findFirst();
        }

        NotificationPayload payload(String actorName) {
            return NotificationPayload.forOrganisation(org, actorName);
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
