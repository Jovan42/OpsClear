package com.opsclear.service;

import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.JobStatusHistoryModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobHistoryService")
class JobHistoryServiceTest {

    @Mock private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private JobHistoryService jobHistoryService;

    private UUID projectId;
    private UUID jobId;
    private UUID ownerId;
    private UUID memberId;
    private ProjectModel project;
    private JobModel job;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel memberMembership;

    @BeforeEach
    void setUp() {
        jobHistoryService = new JobHistoryService(
                jobStatusHistoryRepository, jobRepository, projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        project = ProjectModel.builder().id(projectId).name("Test Project").ownerId(ownerId).build();

        job = JobModel.builder()
                .id(jobId)
                .projectId(projectId)
                .title("Test Job")
                .status(JobStatus.NEW)
                .build();

        ownerMembership = ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();

        memberMembership = ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();
    }

    // --- getHistory ---

    @Test
    @DisplayName("getHistory_shouldReturnHistory_forOwner")
    void getHistory_shouldReturnHistory_forOwner() {
        JobStatusHistoryModel entry = JobStatusHistoryModel.builder()
                .id(UUID.randomUUID()).jobId(jobId)
                .changedFrom(null).changedTo("NEW")
                .changedBy(ownerId).changedAt(Instant.now())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobStatusHistoryRepository.findByJobId(jobId)).thenReturn(List.of(entry));

        List<JobStatusHistoryModel> result = jobHistoryService.getHistory(projectId, jobId, ownerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChangedTo()).isEqualTo("NEW");
        assertThat(result.get(0).getChangedFrom()).isNull();
    }

    @Test
    @DisplayName("getHistory_shouldReturnHistory_forAssignedMember")
    void getHistory_shouldReturnHistory_forAssignedMember() {
        job.setAssignedTo(memberId);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobStatusHistoryRepository.findByJobId(jobId)).thenReturn(List.of());

        List<JobStatusHistoryModel> result = jobHistoryService.getHistory(projectId, jobId, memberId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getHistory_shouldThrowNotFoundException_whenProjectDoesNotExist")
    void getHistory_shouldThrowNotFoundException_whenProjectDoesNotExist() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobHistoryService.getHistory(projectId, jobId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getHistory_shouldThrowForbiddenException_whenCallerIsNotMember")
    void getHistory_shouldThrowForbiddenException_whenCallerIsNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobHistoryService.getHistory(projectId, jobId, ownerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getHistory_shouldThrowNotFoundException_whenJobDoesNotExist")
    void getHistory_shouldThrowNotFoundException_whenJobDoesNotExist() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobHistoryService.getHistory(projectId, jobId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getHistory_shouldThrowNotFoundException_whenJobBelongsToDifferentProject")
    void getHistory_shouldThrowNotFoundException_whenJobBelongsToDifferentProject() {
        job.setProjectId(UUID.randomUUID());

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobHistoryService.getHistory(projectId, jobId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getHistory_shouldThrowForbiddenException_whenMemberIsNotAssigned")
    void getHistory_shouldThrowForbiddenException_whenMemberIsNotAssigned() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobHistoryService.getHistory(projectId, jobId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }
}
