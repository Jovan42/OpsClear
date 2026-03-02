package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock private ApprovalRepository approvalRepository;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private ApprovalService approvalService;

    private UUID projectId;
    private UUID jobId;
    private UUID callerId;
    private UUID approvalId;
    private ProjectModel project;
    private JobModel job;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel memberMembership;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(
                approvalRepository, jobRepository, projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        approvalId = UUID.randomUUID();

        project = ProjectModel.builder()
                .id(projectId)
                .name("Test Project")
                .ownerId(callerId)
                .build();

        job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Install panel")
                .status(JobStatus.IN_PROGRESS)
                .assignedTo(callerId)
                .build();

        ownerMembership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(callerId)
                .role(ProjectMemberRole.OWNER)
                .build();

        memberMembership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(callerId)
                .role(ProjectMemberRole.MEMBER)
                .build();
    }

    // --- request ---

    @Test
    @DisplayName("OWNER should be able to request approval on any job")
    void request_shouldInsertApproval_forOwner() {
        ApprovalModel expected = buildApproval(ApprovalStatus.PENDING);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalRepository.insert(jobId, callerId, "Need transformer")).thenReturn(expected);

        ApprovalModel result = approvalService.request(projectId, jobId, "Need transformer", callerId);

        assertThat(result.getId()).isEqualTo(expected.getId());
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        verify(approvalRepository).insert(jobId, callerId, "Need transformer");
    }

    @Test
    @DisplayName("request should strip whitespace from description before inserting")
    void request_shouldStripDescription_beforeInsert() {
        ApprovalModel expected = buildApproval(ApprovalStatus.PENDING);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalRepository.insert(jobId, callerId, "Need transformer")).thenReturn(expected);

        approvalService.request(projectId, jobId, "  Need transformer  ", callerId);

        verify(approvalRepository).insert(jobId, callerId, "Need transformer");
    }

    @Test
    @DisplayName("MEMBER should be able to request approval on their own assigned job")
    void request_shouldInsertApproval_forMemberOnOwnJob() {
        ApprovalModel expected = buildApproval(ApprovalStatus.PENDING);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(memberMembership));
        when(approvalRepository.insert(jobId, callerId, "Need transformer")).thenReturn(expected);

        ApprovalModel result = approvalService.request(projectId, jobId, "Need transformer", callerId);

        assertThat(result.getId()).isEqualTo(expected.getId());
    }

    @Test
    @DisplayName("request should throw ForbiddenException when MEMBER requests on unassigned job")
    void request_shouldThrow_whenMemberRequestsOnUnassignedJob() {
        JobModel unassignedJob = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Other job")
                .status(JobStatus.NEW)
                .assignedTo(UUID.randomUUID())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(unassignedJob));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> approvalService.request(projectId, jobId, "Need approval", callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied: you are not assigned to this job");
    }

    @Test
    @DisplayName("request should throw NotFoundException when project does not exist")
    void request_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.request(projectId, jobId, "desc", callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("request should throw NotFoundException when job does not exist")
    void request_shouldThrow_whenJobNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.request(projectId, jobId, "desc", callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("request should throw NotFoundException when job belongs to a different project")
    void request_shouldThrow_whenJobInDifferentProject() {
        JobModel jobInOtherProject = JobModel.builder()
                .id(jobId)
                .projectId(UUID.randomUUID())
                .title("Other job")
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(jobInOtherProject));

        assertThatThrownBy(() -> approvalService.request(projectId, jobId, "desc", callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("request should throw ForbiddenException when caller is not a project member")
    void request_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.request(projectId, jobId, "desc", callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- decide ---

    @Test
    @DisplayName("OWNER should be able to approve a pending approval")
    void decide_shouldApproveApproval_forOwner() {
        ApprovalModel pending = buildApproval(ApprovalStatus.PENDING);
        ApprovalModel approved = buildApproval(ApprovalStatus.APPROVED);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalRepository.findByIdAndJobId(approvalId, jobId))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(approved));
        when(approvalRepository.updateDecision(eq(approvalId), eq(callerId),
                eq(ApprovalStatus.APPROVED), eq("Approved"), any(Instant.class))).thenReturn(1);

        ApprovalModel result = approvalService.decide(
                projectId, jobId, approvalId, ApprovalStatus.APPROVED, "Approved", callerId);

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("OWNER should be able to reject a pending approval")
    void decide_shouldRejectApproval_forOwner() {
        ApprovalModel pending = buildApproval(ApprovalStatus.PENDING);
        ApprovalModel rejected = buildApproval(ApprovalStatus.REJECTED);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalRepository.findByIdAndJobId(approvalId, jobId))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(rejected));
        when(approvalRepository.updateDecision(eq(approvalId), eq(callerId),
                eq(ApprovalStatus.REJECTED), eq(null), any(Instant.class))).thenReturn(1);

        ApprovalModel result = approvalService.decide(
                projectId, jobId, approvalId, ApprovalStatus.REJECTED, null, callerId);

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("decide should throw BadRequestException when status is PENDING")
    void decide_shouldThrow_whenStatusIsPending() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalRepository.findByIdAndJobId(approvalId, jobId))
                .thenReturn(Optional.of(buildApproval(ApprovalStatus.PENDING)));

        assertThatThrownBy(() -> approvalService.decide(
                projectId, jobId, approvalId, ApprovalStatus.PENDING, null, callerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot transition an approval back to PENDING");
    }

    @Test
    @DisplayName("decide should throw ConflictException when approval was already decided")
    void decide_shouldThrow_whenAlreadyDecided() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalRepository.findByIdAndJobId(approvalId, jobId))
                .thenReturn(Optional.of(buildApproval(ApprovalStatus.PENDING)));
        when(approvalRepository.updateDecision(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> approvalService.decide(
                projectId, jobId, approvalId, ApprovalStatus.APPROVED, null, callerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This approval has already been decided");
    }

    @Test
    @DisplayName("decide should throw ForbiddenException when caller is a MEMBER")
    void decide_shouldThrow_whenCallerIsMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> approvalService.decide(
                projectId, jobId, approvalId, ApprovalStatus.APPROVED, null, callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("decide should throw NotFoundException when approval does not exist")
    void decide_shouldThrow_whenApprovalNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalRepository.findByIdAndJobId(approvalId, jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.decide(
                projectId, jobId, approvalId, ApprovalStatus.APPROVED, null, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Approval not found");
    }

    // --- listByJob ---

    @Test
    @DisplayName("Any project member should be able to list approvals for a job")
    void listByJob_shouldReturnApprovals_forMember() {
        List<ApprovalModel> approvals = List.of(
                buildApproval(ApprovalStatus.PENDING),
                buildApproval(ApprovalStatus.APPROVED)
        );

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(memberMembership));
        when(approvalRepository.findByJobId(jobId)).thenReturn(approvals);

        List<ApprovalModel> result = approvalService.listByJob(projectId, jobId, callerId);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("listByJob should throw NotFoundException when project does not exist")
    void listByJob_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.listByJob(projectId, jobId, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("listByJob should throw NotFoundException when job does not exist")
    void listByJob_shouldThrow_whenJobNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.listByJob(projectId, jobId, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("listByJob should throw ForbiddenException when caller is not a project member")
    void listByJob_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.listByJob(projectId, jobId, callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- listPendingByProject ---

    @Test
    @DisplayName("OWNER should be able to list all pending approvals in a project")
    void listPendingByProject_shouldReturnPendingApprovals_forOwner() {
        List<ApprovalModel> pending = List.of(buildApproval(ApprovalStatus.PENDING));

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalRepository.findPendingByProjectId(projectId)).thenReturn(pending);

        List<ApprovalModel> result = approvalService.listPendingByProject(projectId, callerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("listPendingByProject should throw ForbiddenException when caller is a MEMBER")
    void listPendingByProject_shouldThrow_whenCallerIsMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> approvalService.listPendingByProject(projectId, callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("listPendingByProject should throw NotFoundException when project does not exist")
    void listPendingByProject_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.listPendingByProject(projectId, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    // --- helpers ---

    private ApprovalModel buildApproval(ApprovalStatus status) {
        return ApprovalModel.builder()
                .id(approvalId)
                .jobId(jobId)
                .requesterId(callerId)
                .description("Need transformer")
                .status(status)
                .requestedAt(Instant.now())
                .build();
    }
}
