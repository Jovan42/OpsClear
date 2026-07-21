package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobLinkModel;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectLinkModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobLinkRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectLinkRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock private JobLinkRepository jobLinkRepository;
    @Mock private ProjectLinkRepository projectLinkRepository;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private LinkService service;

    private UUID projectId;
    private UUID ownerId;
    private UUID memberId;
    private ProjectModel project;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel memberMembership;
    private JobModel job;

    @BeforeEach
    void setUp() {
        service = new LinkService(
                jobLinkRepository, projectLinkRepository, jobRepository, projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        ownerId   = UUID.randomUUID();
        memberId  = UUID.randomUUID();

        project = ProjectModel.builder().id(projectId).name("Test Project").ownerId(ownerId).build();

        ownerMembership  = ProjectMemberModel.builder().projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();
        memberMembership = ProjectMemberModel.builder().projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();

        job = JobModel.builder().id(UUID.randomUUID()).projectId(projectId).title("Job").status(JobStatus.NEW).build();
    }

    // --- createForJob ---

    @Test
    @DisplayName("Any member should create a job link")
    void createForJob_shouldCreateLink_forMember() {
        JobLinkModel saved = JobLinkModel.builder()
                .id(UUID.randomUUID()).jobId(job.getId()).url("https://github.com/org/repo/pull/1")
                .label("PR #1").createdBy(memberId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(jobLinkRepository.save(any())).thenReturn(saved);

        JobLinkModel result = service.createForJob(
                projectId, job.getId(), "https://github.com/org/repo/pull/1", "PR #1", memberId);

        assertThat(result.getId()).isEqualTo(saved.getId());

        ArgumentCaptor<JobLinkModel> captor = ArgumentCaptor.forClass(JobLinkModel.class);
        verify(jobLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getJobId()).isEqualTo(job.getId());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(memberId);
        assertThat(captor.getValue().getUrl()).isEqualTo("https://github.com/org/repo/pull/1");
    }

    @Test
    @DisplayName("Should throw NotFoundException when project does not exist on createForJob")
    void createForJob_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createForJob(projectId, job.getId(), "https://example.com", null, memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when job belongs to a different project on createForJob")
    void createForJob_shouldThrow_whenJobInDifferentProject() {
        JobModel foreignJob = JobModel.builder().id(UUID.randomUUID()).projectId(UUID.randomUUID())
                .title("Foreign").status(JobStatus.NEW).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(foreignJob.getId())).thenReturn(Optional.of(foreignJob));

        assertThatThrownBy(() -> service.createForJob(projectId, foreignJob.getId(), "https://example.com", null, memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a project member on createForJob")
    void createForJob_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createForJob(projectId, job.getId(), "https://example.com", null, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("Should throw BadRequestException when URL scheme is javascript")
    void createForJob_shouldThrow_whenSchemeDisallowed() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> service.createForJob(
                projectId, job.getId(), "javascript:alert(1)", null, memberId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("URL is invalid or uses a disallowed scheme");
    }

    @Test
    @DisplayName("Should throw BadRequestException when URL has no scheme")
    void createForJob_shouldThrow_whenSchemeMissing() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> service.createForJob(projectId, job.getId(), "not-a-url", null, memberId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("URL is invalid or uses a disallowed scheme");
    }

    @Test
    @DisplayName("Should throw BadRequestException when URL is malformed")
    void createForJob_shouldThrow_whenUrlMalformed() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> service.createForJob(
                projectId, job.getId(), "http://exa mple.com", null, memberId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("URL is invalid or uses a disallowed scheme");
    }

    // --- updateJobLink ---

    @Test
    @DisplayName("Owner should update a job link")
    void updateJobLink_shouldUpdate_forOwner() {
        UUID linkId = UUID.randomUUID();
        JobLinkModel existing = JobLinkModel.builder().id(linkId).jobId(job.getId())
                .url("https://old.example.com").createdBy(memberId).build();
        JobLinkModel updated = JobLinkModel.builder().id(linkId).jobId(job.getId())
                .url("https://new.example.com").label("New label").createdBy(memberId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobLinkRepository.findById(linkId)).thenReturn(Optional.of(existing));
        when(jobLinkRepository.save(any())).thenReturn(updated);

        JobLinkModel result = service.updateJobLink(
                projectId, job.getId(), linkId, "https://new.example.com", "New label", ownerId);

        assertThat(result.getUrl()).isEqualTo("https://new.example.com");
        assertThat(result.getLabel()).isEqualTo("New label");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when member tries to update a job link")
    void updateJobLink_shouldThrow_whenMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> service.updateJobLink(
                projectId, job.getId(), UUID.randomUUID(), "https://example.com", null, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("Should throw NotFoundException when job link does not belong to the given job")
    void updateJobLink_shouldThrow_whenLinkBelongsToDifferentJob() {
        UUID linkId = UUID.randomUUID();
        JobLinkModel existing = JobLinkModel.builder().id(linkId).jobId(UUID.randomUUID())
                .url("https://old.example.com").createdBy(memberId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobLinkRepository.findById(linkId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateJobLink(
                projectId, job.getId(), linkId, "https://example.com", null, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Link not found");
    }

    // --- deleteJobLink ---

    @Test
    @DisplayName("Owner should delete a job link")
    void deleteJobLink_shouldDelete_forOwner() {
        UUID linkId = UUID.randomUUID();
        JobLinkModel existing = JobLinkModel.builder().id(linkId).jobId(job.getId())
                .url("https://example.com").createdBy(memberId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobLinkRepository.findById(linkId)).thenReturn(Optional.of(existing));

        service.deleteJobLink(projectId, job.getId(), linkId, ownerId);

        verify(jobLinkRepository).deleteById(linkId);
    }

    @Test
    @DisplayName("Should throw NotFoundException when job link does not exist on delete")
    void deleteJobLink_shouldThrow_whenNotFound() {
        UUID linkId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobLinkRepository.findById(linkId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteJobLink(projectId, job.getId(), linkId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Link not found");
    }

    // --- createForProject ---

    @Test
    @DisplayName("Any member should create a project link")
    void createForProject_shouldCreateLink_forMember() {
        ProjectLinkModel saved = ProjectLinkModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).url("https://figma.com/file/abc")
                .label("Design").createdBy(memberId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(projectLinkRepository.save(any())).thenReturn(saved);

        ProjectLinkModel result = service.createForProject(
                projectId, "https://figma.com/file/abc", "Design", memberId);

        assertThat(result.getId()).isEqualTo(saved.getId());

        ArgumentCaptor<ProjectLinkModel> captor = ArgumentCaptor.forClass(ProjectLinkModel.class);
        verify(projectLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(projectId);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a project member on createForProject")
    void createForProject_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createForProject(projectId, "https://example.com", null, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- updateProjectLink ---

    @Test
    @DisplayName("Owner should update a project link")
    void updateProjectLink_shouldUpdate_forOwner() {
        UUID linkId = UUID.randomUUID();
        ProjectLinkModel existing = ProjectLinkModel.builder().id(linkId).projectId(projectId)
                .url("https://old.example.com").createdBy(memberId).build();
        ProjectLinkModel updated = ProjectLinkModel.builder().id(linkId).projectId(projectId)
                .url("https://new.example.com").label("New").createdBy(memberId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(projectLinkRepository.findById(linkId)).thenReturn(Optional.of(existing));
        when(projectLinkRepository.save(any())).thenReturn(updated);

        ProjectLinkModel result = service.updateProjectLink(
                projectId, linkId, "https://new.example.com", "New", ownerId);

        assertThat(result.getUrl()).isEqualTo("https://new.example.com");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when member tries to update a project link")
    void updateProjectLink_shouldThrow_whenMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> service.updateProjectLink(
                projectId, UUID.randomUUID(), "https://example.com", null, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("Should throw NotFoundException when project link does not belong to the given project")
    void updateProjectLink_shouldThrow_whenLinkBelongsToDifferentProject() {
        UUID linkId = UUID.randomUUID();
        ProjectLinkModel existing = ProjectLinkModel.builder().id(linkId).projectId(UUID.randomUUID())
                .url("https://old.example.com").createdBy(memberId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(projectLinkRepository.findById(linkId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateProjectLink(
                projectId, linkId, "https://example.com", null, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Link not found");
    }

    // --- deleteProjectLink ---

    @Test
    @DisplayName("Owner should delete a project link")
    void deleteProjectLink_shouldDelete_forOwner() {
        UUID linkId = UUID.randomUUID();
        ProjectLinkModel existing = ProjectLinkModel.builder().id(linkId).projectId(projectId)
                .url("https://example.com").createdBy(memberId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(projectLinkRepository.findById(linkId)).thenReturn(Optional.of(existing));

        service.deleteProjectLink(projectId, linkId, ownerId);

        verify(projectLinkRepository).deleteById(linkId);
    }

    @Test
    @DisplayName("Should throw NotFoundException when project link does not exist on delete")
    void deleteProjectLink_shouldThrow_whenNotFound() {
        UUID linkId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(projectLinkRepository.findById(linkId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProjectLink(projectId, linkId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Link not found");
    }
}
