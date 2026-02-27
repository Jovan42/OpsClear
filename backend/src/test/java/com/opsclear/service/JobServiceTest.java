package com.opsclear.service;

import com.opsclear.dto.CreateJobRequest;
import com.opsclear.dto.UpdateJobRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.BlockReasonModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private BlockReasonService blockReasonService;

    private JobService jobService;

    private UUID projectId;
    private UUID ownerId;
    private UUID memberId;
    private ProjectModel project;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel memberMembership;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, projectRepository, projectMemberRepository, userRepository, blockReasonService);

        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        project = ProjectModel.builder()
                .id(projectId)
                .name("Test Project")
                .ownerId(ownerId)
                .build();

        ownerMembership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(ownerId)
                .role(ProjectMemberRole.OWNER)
                .build();

        memberMembership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(memberId)
                .role(ProjectMemberRole.MEMBER)
                .build();
    }

    private ProjectMemberModel adminMembership(UUID adminId) {
        return ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(adminId)
                .role(ProjectMemberRole.ADMIN)
                .build();
    }

    // --- create ---

    @Test
    @DisplayName("Should create job for any project member")
    void create_shouldCreateJob_forMember() {
        CreateJobRequest request = CreateJobRequest.builder().title("Fix bug").build();

        JobModel saved = JobModel.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .title("Fix bug")
                .status(JobStatus.NEW)
                .createdBy(ownerId)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.save(any())).thenReturn(saved);

        JobModel result = jobService.create(projectId, request, ownerId);

        assertThat(result.getTitle()).isEqualTo("Fix bug");
        assertThat(result.getStatus()).isEqualTo(JobStatus.NEW);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(ownerId);
        assertThat(captor.getValue().getProjectId()).isEqualTo(projectId);
    }

    @Test
    @DisplayName("Should throw NotFoundException when project does not exist")
    void create_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.create(projectId, CreateJobRequest.builder().title("x").build(), ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a project member")
    void create_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.create(projectId, CreateJobRequest.builder().title("x").build(), ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("Should throw NotFoundException when assigned user does not exist")
    void create_shouldThrow_whenAssignedUserNotFound() {
        UUID unknownUser = UUID.randomUUID();
        CreateJobRequest request = CreateJobRequest.builder().title("x").assignedTo(unknownUser).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findById(unknownUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.create(projectId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Assigned user not found");
    }

    @Test
    @DisplayName("Should create job and resolve assigned user when assignedTo is set")
    void create_shouldCreateJob_withAssignedUser() {
        UUID assignedUserId = UUID.randomUUID();
        CreateJobRequest request = CreateJobRequest.builder().title("Fix bug").assignedTo(assignedUserId).build();

        JobModel saved = JobModel.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .title("Fix bug")
                .assignedTo(assignedUserId)
                .status(JobStatus.NEW)
                .createdBy(ownerId)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findById(assignedUserId))
                .thenReturn(Optional.of(com.opsclear.model.UserModel.builder().id(assignedUserId).build()));
        when(jobRepository.save(any())).thenReturn(saved);

        JobModel result = jobService.create(projectId, request, ownerId);

        assertThat(result.getAssignedTo()).isEqualTo(assignedUserId);
    }

    // --- list ---

    @Test
    @DisplayName("OWNER should see all jobs in the project")
    void list_shouldReturnAllJobs_forOwner() {
        List<JobModel> allJobs = List.of(
                JobModel.builder()
                        .id(UUID.randomUUID())
                        .projectId(projectId)
                        .title("Job 1")
                        .status(JobStatus.NEW)
                        .build(),
                JobModel.builder()
                        .id(UUID.randomUUID())
                        .projectId(projectId)
                        .title("Job 2")
                        .status(JobStatus.IN_PROGRESS)
                        .build()
        );

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).thenReturn(allJobs);

        List<JobModel> result = jobService.list(projectId, ownerId);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("MEMBER should see only assigned jobs")
    void list_shouldReturnOnlyAssignedJobs_forMember() {
        List<JobModel> assignedJobs = List.of(
                JobModel.builder()
                        .id(UUID.randomUUID())
                        .projectId(projectId)
                        .title("My Job")
                        .assignedTo(memberId)
                        .status(JobStatus.NEW)
                        .build()
        );

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(jobRepository.findByProjectIdAndAssignedToAndDeletedAtIsNull(projectId, memberId))
                .thenReturn(assignedJobs);

        List<JobModel> result = jobService.list(projectId, memberId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAssignedTo()).isEqualTo(memberId);
    }

    // --- getById ---

    @Test
    @DisplayName("OWNER should be able to get any job by ID")
    void getById_shouldReturnJob_forOwner() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Fix bug")
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        JobModel result = jobService.getById(projectId, jobId, ownerId);

        assertThat(result.getId()).isEqualTo(jobId);
    }

    @Test
    @DisplayName("Assigned MEMBER should be able to get their own job")
    void getById_shouldReturnJob_forAssignedMember() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("My job")
                .assignedTo(memberId)
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        JobModel result = jobService.getById(projectId, jobId, memberId);

        assertThat(result.getId()).isEqualTo(jobId);
    }

    @Test
    @DisplayName("MEMBER should be forbidden from accessing a job not assigned to them")
    void getById_shouldThrow_whenMemberNotAssigned() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Other job")
                .assignedTo(UUID.randomUUID())
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.getById(projectId, jobId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied: you are not assigned to this job");
    }

    @Test
    @DisplayName("Should throw NotFoundException when job does not exist")
    void getById_shouldThrow_whenJobNotFound() {
        UUID jobId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getById(projectId, jobId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when job belongs to a different project")
    void getById_shouldThrow_whenJobNotInProject() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(UUID.randomUUID())
                .title("Other project job")
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.getById(projectId, jobId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    // --- update ---

    @Test
    @DisplayName("OWNER should be able to update job fields")
    void update_shouldUpdateFields_forOwner() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Old title")
                .status(JobStatus.NEW)
                .build();
        UpdateJobRequest request = UpdateJobRequest.builder().title("New title").description("New desc").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobModel result = jobService.update(projectId, jobId, request, ownerId);

        assertThat(result.getTitle()).isEqualTo("New title");
        assertThat(result.getDescription()).isEqualTo("New desc");
    }

    @Test
    @DisplayName("MEMBER should be forbidden from updating job fields")
    void update_shouldThrow_whenMemberRole() {
        UUID jobId = UUID.randomUUID();
        UpdateJobRequest request = UpdateJobRequest.builder().title("x").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> jobService.update(projectId, jobId, request, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("Should throw NotFoundException when job does not exist on update")
    void update_shouldThrow_whenJobNotFound() {
        UUID jobId = UUID.randomUUID();
        UpdateJobRequest request = UpdateJobRequest.builder().title("x").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.update(projectId, jobId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should update job and resolve assigned user when assignedTo is set")
    void update_shouldUpdateJob_withAssignedUser() {
        UUID jobId = UUID.randomUUID();
        UUID assignedUserId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Old title")
                .status(JobStatus.NEW)
                .build();
        UpdateJobRequest request = UpdateJobRequest.builder().title("New title").assignedTo(assignedUserId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(userRepository.findById(assignedUserId))
                .thenReturn(Optional.of(com.opsclear.model.UserModel.builder().id(assignedUserId).build()));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobModel result = jobService.update(projectId, jobId, request, ownerId);

        assertThat(result.getAssignedTo()).isEqualTo(assignedUserId);
    }

    @Test
    @DisplayName("Should throw NotFoundException when updating a job from a different project")
    void update_shouldThrow_whenJobNotInProject() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(UUID.randomUUID())
                .title("Other")
                .status(JobStatus.NEW)
                .build();
        UpdateJobRequest request = UpdateJobRequest.builder().title("x").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.update(projectId, jobId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when updating assigned user that does not exist")
    void update_shouldThrow_whenAssignedUserNotFound() {
        UUID jobId = UUID.randomUUID();
        UUID unknownUser = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Job")
                .status(JobStatus.NEW)
                .build();
        UpdateJobRequest request = UpdateJobRequest.builder().title("x").assignedTo(unknownUser).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(userRepository.findById(unknownUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.update(projectId, jobId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Assigned user not found");
    }

    // --- updateStatus ---

    @Test
    @DisplayName("Should transition NEW → IN_PROGRESS for assigned MEMBER")
    void updateStatus_shouldTransition_newToInProgress_forAssignedMember() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .assignedTo(memberId)
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobModel result = jobService.updateStatus(projectId, jobId, JobStatus.IN_PROGRESS, null, memberId);

        assertThat(result.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Should transition IN_PROGRESS → COMPLETED for OWNER")
    void updateStatus_shouldTransition_inProgressToCompleted_forOwner() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.IN_PROGRESS)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobModel result = jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, ownerId);

        assertThat(result.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should reopen COMPLETED → IN_PROGRESS for OWNER")
    void updateStatus_shouldTransition_completedToInProgress_forOwner() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.COMPLETED)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobModel result = jobService.updateStatus(projectId, jobId, JobStatus.IN_PROGRESS, null, ownerId);

        assertThat(result.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Should block IN_PROGRESS job with reason for OWNER")
    void updateStatus_shouldBlock_whenReasonProvided() {
        UUID jobId = UUID.randomUUID();
        UUID reasonId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.IN_PROGRESS)
                .build();
        BlockReasonModel blockReason = BlockReasonModel.builder()
                .id(reasonId)
                .projectId(projectId)
                .reason("Waiting for client sign-off")
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(blockReasonService.findOrCreate(projectId, "Waiting for client sign-off")).thenReturn(blockReason);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobModel result = jobService.updateStatus(projectId, jobId, JobStatus.BLOCKED, "Waiting for client sign-off", ownerId);

        assertThat(result.getStatus()).isEqualTo(JobStatus.BLOCKED);
        assertThat(result.getBlockedBy()).isEqualTo(ownerId);
        assertThat(result.getBlockedReasonId()).isEqualTo(reasonId);
        assertThat(result.getBlockedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw BadRequestException when blocking without a reason")
    void updateStatus_shouldThrow_whenBlockingWithoutReason() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.IN_PROGRESS)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.BLOCKED, null, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A block reason is required when blocking a job");
    }

    @Test
    @DisplayName("Should throw BadRequestException when blocking with a blank reason")
    void updateStatus_shouldThrow_whenBlockingWithBlankReason() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.IN_PROGRESS)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.BLOCKED, "   ", ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A block reason is required when blocking a job");
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid transition BLOCKED → COMPLETED")
    void updateStatus_shouldThrow_whenInvalidTransition_blockedToCompleted() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.BLOCKED)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid transition: BLOCKED → COMPLETED");
    }

    @Test
    @DisplayName("Should unblock BLOCKED → IN_PROGRESS and clear blocking metadata")
    void updateStatus_shouldUnblock_clearingBlockingMetadata() {
        UUID jobId = UUID.randomUUID();
        UUID reasonId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.BLOCKED)
                .blockedBy(ownerId)
                .blockedReasonId(reasonId)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobModel result = jobService.updateStatus(projectId, jobId, JobStatus.IN_PROGRESS, null, ownerId);

        assertThat(result.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(result.getBlockedBy()).isNull();
        assertThat(result.getBlockedReasonId()).isNull();
        assertThat(result.getBlockedAt()).isNull();
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid transition NEW → COMPLETED")
    void updateStatus_shouldThrow_whenInvalidTransition_newToCompleted() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid transition: NEW → COMPLETED");
    }

    @Test
    @DisplayName("Should transition IN_PROGRESS → COMPLETED for ADMIN")
    void updateStatus_shouldTransition_inProgressToCompleted_forAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.IN_PROGRESS)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId))
                .thenReturn(Optional.of(adminMembership(adminId)));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobModel result = jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, adminId);

        assertThat(result.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid transition IN_PROGRESS → NEW")
    void updateStatus_shouldThrow_whenInvalidTransition_inProgressToNew() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.IN_PROGRESS)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.NEW, null, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid transition: IN_PROGRESS → NEW");
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid transition COMPLETED → NEW")
    void updateStatus_shouldThrow_whenInvalidTransition_completedToNew() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .status(JobStatus.COMPLETED)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.NEW, null, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid transition: COMPLETED → NEW");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when MEMBER tries to change status on unassigned job")
    void updateStatus_shouldThrow_whenMemberNotAssigned() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .assignedTo(UUID.randomUUID())
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.IN_PROGRESS, null, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only OWNER, ADMIN, or the assigned member can change job status");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when MEMBER tries to reopen a completed job")
    void updateStatus_shouldThrow_whenMemberTriesToReopen() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .assignedTo(memberId)
                .status(JobStatus.COMPLETED)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.IN_PROGRESS, null, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only OWNER or ADMIN can reopen a completed job");
    }

    @Test
    @DisplayName("Should throw NotFoundException when job does not exist on updateStatus")
    void updateStatus_shouldThrow_whenJobNotFound() {
        UUID jobId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.IN_PROGRESS, null, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when updating status of a job from a different project")
    void updateStatus_shouldThrow_whenJobNotInProject() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(UUID.randomUUID())
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.IN_PROGRESS, null, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    // --- softDelete ---

    @Test
    @DisplayName("OWNER should be able to soft delete a job")
    void softDelete_shouldSetDeletedAt_forOwner() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Fix bug")
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobService.softDelete(projectId, jobId, ownerId);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("MEMBER should be forbidden from deleting a job")
    void softDelete_shouldThrow_whenMemberRole() {
        UUID jobId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> jobService.softDelete(projectId, jobId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("Should throw NotFoundException when soft deleting non-existent job")
    void softDelete_shouldThrow_whenNotFound() {
        UUID jobId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.softDelete(projectId, jobId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when deleting a job from a different project")
    void softDelete_shouldThrow_whenJobNotInProject() {
        UUID jobId = UUID.randomUUID();
        JobModel job = JobModel.builder()
                .id(jobId)
                .projectId(UUID.randomUUID())
                .title("Other")
                .status(JobStatus.NEW)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.softDelete(projectId, jobId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }
}
