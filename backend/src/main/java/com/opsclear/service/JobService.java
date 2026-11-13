package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.dto.CreateJobRequest;
import com.opsclear.dto.UpdateJobRequest;
import com.opsclear.event.JobAssignedEvent;
import com.opsclear.event.JobDeletedEvent;
import com.opsclear.event.JobUpdatedEvent;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.exception.ConflictException;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.JobLinkModel;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobRelationshipDirection;
import com.opsclear.model.JobRelationshipEntry;
import com.opsclear.model.JobRelationshipModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.JobStatusUpdateResult;
import com.opsclear.model.PendingCompletionApprovalModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.JobLinkRepository;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.JobTypeRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final JobRelationshipRepository jobRelationshipRepository;
    private final JobLinkRepository jobLinkRepository;
    private final JobStatusHistoryRepository jobStatusHistoryRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final MilestoneRepository milestoneRepository;
    private final JobTypeRepository jobTypeRepository;
    private final OrganisationRepository organisationRepository;
    private final FriendlyIdService friendlyIdService;
    private final ApplicationEventPublisher eventPublisher;
    private final JobStatusTransitioner jobStatusTransitioner;
    private final ApprovalRepository approvalRepository;
    private final ApprovalService approvalService;
    private final AddonAccessChecker addonAccessChecker;

    @Transactional
    public JobModel create(UUID projectId, CreateJobRequest request, UUID requesterId) {
        UUID orgId = requireOrgMembership(requesterId);
        ProjectModel project = requireProjectExists(projectId);
        requireProjectNotCompleted(project);
        requireMember(projectId, requesterId);
        requireAssignedUserExists(request.getAssignedTo());
        requireMilestoneInProject(request.getMilestoneId(), projectId);
        requireTypeInProject(request.getTypeId(), projectId);

        String friendlyId = friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.JOB);

        JobModel job = JobModel.builder()
                .friendlyId(friendlyId)
                .projectId(projectId)
                .title(request.getTitle())
                .description(request.getDescription())
                .client(request.getClient())
                .assignedTo(request.getAssignedTo())
                .deadline(request.getDeadline())
                .status(JobStatus.NEW)
                .priority(request.getPriority() != null ? request.getPriority() : JobPriority.MEDIUM)
                .milestoneId(request.getMilestoneId())
                .typeId(request.getTypeId())
                .createdBy(requesterId)
                .build();

        JobModel saved = jobRepository.save(job);
        jobStatusHistoryRepository.insert(saved.getId(), null, JobStatus.NEW.name(), requesterId, null);
        log.info("Created job '{}' in project {} by user {}", saved.getTitle(), projectId, requesterId);
        if (saved.getAssignedTo() != null) {
            eventPublisher.publishEvent(new JobAssignedEvent(saved.getId(), projectId, requesterId,
                    saved.getAssignedTo()));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<JobModel> list(UUID projectId, UUID requesterId, String q,
                               JobPriority priority, UUID milestoneId) {
        return list(projectId, requesterId, q, priority, milestoneId, null);
    }

    @Transactional(readOnly = true)
    public List<JobModel> list(UUID projectId, UUID requesterId, String q,
                               JobPriority priority, UUID milestoneId, UUID typeId) {
        requireOrgMembership(requesterId);
        requireProjectExistsById(projectId);
        ProjectMemberModel requester = requireMember(projectId, requesterId);
        boolean isMember = requester.getRole() == ProjectMemberRole.MEMBER;
        UUID assignedTo = isMember ? requesterId : null;
        String trimmedQ = (q != null && !q.isBlank()) ? q.trim() : null;
        List<JobModel> jobs = jobRepository.findByFilters(
                projectId, assignedTo, trimmedQ, priority, milestoneId, typeId);
        attachLinks(jobs);
        attachPendingCompletionApprovals(jobs);
        return jobs;
    }

    @Transactional(readOnly = true)
    public JobModel getById(UUID projectId, UUID jobId, UUID requesterId) {
        requireOrgMembership(requesterId);
        requireProjectExistsById(projectId);
        ProjectMemberModel requester = requireMember(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);
        requireCanViewJob(requester, job, requesterId);

        job.setRelationships(fetchRelationships(jobId));
        job.setLinks(jobLinkRepository.findByJobId(jobId));
        attachPendingCompletionApproval(job);
        return job;
    }

    private void attachLinks(List<JobModel> jobs) {
        List<UUID> jobIds = jobs.stream().map(JobModel::getId).toList();
        Map<UUID, List<JobLinkModel>> byJobId = jobLinkRepository.findByJobIds(jobIds).stream()
                .collect(Collectors.groupingBy(JobLinkModel::getJobId));
        jobs.forEach(j -> j.setLinks(byJobId.getOrDefault(j.getId(), List.of())));
    }

    // ADR-0052: one query for the whole list, no N+1
    private void attachPendingCompletionApprovals(List<JobModel> jobs) {
        List<UUID> jobIds = jobs.stream().map(JobModel::getId).toList();
        Map<UUID, PendingCompletionApprovalModel> byJobId = approvalRepository.findPendingCompletionByJobIds(jobIds)
                .stream()
                .collect(Collectors.toMap(PendingCompletionApprovalModel::getJobId, Function.identity()));
        jobs.forEach(j -> j.setPendingCompletionApproval(byJobId.get(j.getId())));
    }

    private void attachPendingCompletionApproval(JobModel job) {
        job.setPendingCompletionApproval(approvalRepository.findPendingCompletionByJobId(job.getId()).orElse(null));
    }

    private List<JobRelationshipEntry> fetchRelationships(UUID jobId) {
        List<JobRelationshipModel> raw = jobRelationshipRepository.findByJobId(jobId);
        List<UUID> linkedIds = raw.stream()
                .map(r -> r.getSourceJobId().equals(jobId) ? r.getTargetJobId() : r.getSourceJobId())
                .collect(Collectors.toList());
        Map<UUID, JobModel> linkedJobs = jobRepository.findByIds(linkedIds).stream()
                .collect(Collectors.toMap(JobModel::getId, j -> j));
        return raw.stream()
                .map(r -> {
                    boolean outgoing = r.getSourceJobId().equals(jobId);
                    UUID linkedId = outgoing ? r.getTargetJobId() : r.getSourceJobId();
                    JobModel linked = linkedJobs.get(linkedId);
                    return JobRelationshipEntry.builder()
                            .id(r.getId())
                            .type(r.getType())
                            .direction(outgoing ? JobRelationshipDirection.OUTGOING : JobRelationshipDirection.INCOMING)
                            .linkedJobId(linkedId)
                            .linkedJobFriendlyId(linked != null ? linked.getFriendlyId() : null)
                            .linkedJobTitle(linked != null ? linked.getTitle() : null)
                            .linkedJobStatus(linked != null ? linked.getStatus() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public JobModel update(UUID projectId, UUID jobId, UpdateJobRequest request, UUID requesterId) {
        requireOrgMembership(requesterId);
        ProjectModel project = requireProjectExists(projectId);
        requireProjectNotCompleted(project);
        requireOwnerOrAdmin(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);
        requireAssignedUserExists(request.getAssignedTo());
        requireMilestoneInProject(request.getMilestoneId(), projectId);
        requireTypeInProject(request.getTypeId(), projectId);

        UUID oldAssignee = job.getAssignedTo();
        Instant oldDeadline = job.getDeadline();
        JobPriority oldPriority = job.getPriority();
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setClient(request.getClient());
        job.setAssignedTo(request.getAssignedTo());
        job.setDeadline(request.getDeadline());
        if (request.getPriority() != null) {
            job.setPriority(request.getPriority());
        }
        job.setMilestoneId(request.getMilestoneId());
        job.setTypeId(request.getTypeId());

        JobModel updated = jobRepository.save(job);
        log.info("Updated job '{}' in project {}", jobId, projectId);
        publishUpdateEvent(updated, oldAssignee, oldDeadline, oldPriority, requesterId);
        return updated;
    }

    /**
     * Changes the job's status, or (ADR-0052) for a Member completing a job on a gated project, validates the
     * transition and creates a completion approval instead, leaving the status unchanged. The gate is enforced only
     * while the org can use the {@code APPROVALS} add-on for writes (not while {@code PAST_DUE}), since otherwise
     * nobody could decide the approval. The returned job carries its pending completion approval, if any.
     *
     * @param message optional completion request message, ignored unless a completion approval is created
     */
    @Transactional
    public JobStatusUpdateResult updateStatus(UUID projectId, UUID jobId, JobStatus newStatus, String reason,
                                              String message, UUID requesterId) {
        requireOrgMembership(requesterId);
        ProjectModel project = requireProjectExists(projectId);
        requireProjectNotCompleted(project);
        ProjectMemberModel requester = requireMember(projectId, requesterId);
        // Row lock (ADR-0052): a status change racing a completion approval's decision sees its result
        JobModel job = requireJobForUpdate(jobId);
        requireJobInProject(job, projectId);

        boolean completionRequested = isCompletionGated(project, requester, newStatus);
        JobModel result = job;
        if (completionRequested) {
            jobStatusTransitioner.validate(job, newStatus, requester);
            approvalService.createCompletionApproval(project, job, requester, message);
        } else {
            result = jobStatusTransitioner.transition(job, newStatus, reason, requester);
        }
        attachPendingCompletionApproval(result);
        return JobStatusUpdateResult.builder().job(result).completionRequested(completionRequested).build();
    }

    // ADR-0052: Owners/Admins complete directly; the add-on check runs last since it queries the subscription
    private boolean isCompletionGated(ProjectModel project, ProjectMemberModel requester, JobStatus newStatus) {
        return newStatus == JobStatus.COMPLETED
                && project.isRequireCompletionApproval()
                && requester.getRole() == ProjectMemberRole.MEMBER
                && addonAccessChecker.isAddonUsableForWrites(project.getOrganisationId(), AddonCode.APPROVALS);
    }

    @Transactional
    public void softDelete(UUID projectId, UUID jobId, UUID requesterId) {
        requireOrgMembership(requesterId);
        requireProjectExistsById(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);

        job.softDelete();
        jobRepository.save(job);
        log.info("Soft-deleted job '{}' from project {}", jobId, projectId);
        eventPublisher.publishEvent(new JobDeletedEvent(jobId, projectId, requesterId));
    }

    // --- Notification events (ADR-0051) ---

    // A changed assignee sends only JOB_ASSIGNED (its payload carries the deadline); otherwise a changed deadline or
    // priority sends JOB_UPDATED. Other field edits, and unassigning, send nothing.
    private void publishUpdateEvent(JobModel updated, UUID oldAssignee, Instant oldDeadline,
                                    JobPriority oldPriority, UUID requesterId) {
        UUID newAssignee = updated.getAssignedTo();
        if (!Objects.equals(oldAssignee, newAssignee)) {
            if (newAssignee != null) {
                eventPublisher.publishEvent(new JobAssignedEvent(updated.getId(), updated.getProjectId(),
                        requesterId, newAssignee));
            }
            return;
        }
        List<String> changedFields = new ArrayList<>();
        boolean deadlineChanged = !Objects.equals(oldDeadline, updated.getDeadline());
        boolean priorityChanged = oldPriority != updated.getPriority();
        if (deadlineChanged) {
            changedFields.add(JobUpdatedEvent.DEADLINE);
        }
        if (priorityChanged) {
            changedFields.add(JobUpdatedEvent.PRIORITY);
        }
        if (!changedFields.isEmpty()) {
            eventPublisher.publishEvent(new JobUpdatedEvent(updated.getId(), updated.getProjectId(), requesterId,
                    changedFields, deadlineChanged ? updated.getDeadline() : null,
                    priorityChanged ? updated.getPriority() : null));
        }
    }

    // --- Org guard ---

    private UUID requireOrgMembership(UUID userId) {
        return organisationRepository.findByMember(userId)
                .map(OrganisationModel::getId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_IN_ORG));
    }

    // --- Guards ---

    private void requireCanViewJob(ProjectMemberModel requester, JobModel job, UUID requesterId) {
        if (requester.getRole() == ProjectMemberRole.MEMBER
                && !requesterId.equals(job.getAssignedTo())) {
            throw new ForbiddenException(ErrorMessages.Job.ACCESS_DENIED_NOT_ASSIGNED);
        }
    }

    private JobModel requireJob(UUID jobId) {
        return jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
    }

    private JobModel requireJobForUpdate(UUID jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
    }

    private void requireMilestoneInProject(UUID milestoneId, UUID projectId) {
        if (milestoneId == null) {
            return;
        }
        milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)
                .filter(m -> m.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Milestone.NOT_FOUND));
    }

    private void requireTypeInProject(UUID typeId, UUID projectId) {
        if (typeId == null) {
            return;
        }
        jobTypeRepository.findById(typeId)
                .filter(t -> t.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.JobType.NOT_FOUND));
    }

    private void requireAssignedUserExists(UUID userId) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.ASSIGNED_USER_NOT_FOUND));
    }

    private void requireJobInProject(JobModel job, UUID projectId) {
        if (!job.getProjectId().equals(projectId)) {
            throw new NotFoundException(ErrorMessages.Job.NOT_FOUND);
        }
    }

    private ProjectModel requireProjectExists(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private void requireProjectExistsById(UUID projectId) {
        requireProjectExists(projectId);
    }

    private void requireProjectNotCompleted(ProjectModel project) {
        if (project.isCompleted()) {
            throw new ConflictException(ErrorMessages.Project.COMPLETED_NO_MUTATIONS);
        }
    }

    // --- Permission helpers ---

    private ProjectMemberModel requireMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel requester = requireMember(projectId, userId);
        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }
}
