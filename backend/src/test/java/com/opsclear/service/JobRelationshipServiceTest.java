package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobRelationshipModel;
import com.opsclear.model.JobRelationshipType;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
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
class JobRelationshipServiceTest {

    @Mock private JobRelationshipRepository jobRelationshipRepository;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private JobRelationshipService service;

    private UUID projectId;
    private UUID ownerId;
    private UUID memberId;
    private ProjectModel project;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel memberMembership;
    private JobModel sourceJob;
    private JobModel targetJob;

    @BeforeEach
    void setUp() {
        service = new JobRelationshipService(
                jobRelationshipRepository, jobRepository, projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        ownerId   = UUID.randomUUID();
        memberId  = UUID.randomUUID();

        project = ProjectModel.builder().id(projectId).name("Test Project").ownerId(ownerId).build();

        ownerMembership  = ProjectMemberModel.builder().projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();
        memberMembership = ProjectMemberModel.builder().projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();

        sourceJob = JobModel.builder().id(UUID.randomUUID()).projectId(projectId).title("Source").status(JobStatus.NEW).build();
        targetJob = JobModel.builder().id(UUID.randomUUID()).projectId(projectId).title("Target").status(JobStatus.NEW).build();
    }

    // --- create ---

    @Test
    @DisplayName("Any member should create a relationship")
    void create_shouldCreateRelationship_forMember() {
        JobRelationshipModel saved = JobRelationshipModel.builder()
                .id(UUID.randomUUID())
                .sourceJobId(sourceJob.getId())
                .targetJobId(targetJob.getId())
                .type(JobRelationshipType.BLOCKED_BY)
                .createdBy(memberId)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(sourceJob.getId())).thenReturn(Optional.of(sourceJob));
        when(jobRepository.findByIdAndDeletedAtIsNull(targetJob.getId())).thenReturn(Optional.of(targetJob));
        when(jobRelationshipRepository.existsBySourceJobIdAndTargetJobIdAndType(
                sourceJob.getId(), targetJob.getId(), JobRelationshipType.BLOCKED_BY)).thenReturn(false);
        when(jobRelationshipRepository.save(any())).thenReturn(saved);

        JobRelationshipModel result = service.create(projectId, sourceJob.getId(), targetJob.getId(),
                JobRelationshipType.BLOCKED_BY, memberId);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getType()).isEqualTo(JobRelationshipType.BLOCKED_BY);
        assertThat(result.getSourceJobId()).isEqualTo(sourceJob.getId());
        assertThat(result.getTargetJobId()).isEqualTo(targetJob.getId());

        ArgumentCaptor<JobRelationshipModel> captor = ArgumentCaptor.forClass(JobRelationshipModel.class);
        verify(jobRelationshipRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(memberId);
        assertThat(captor.getValue().getType()).isEqualTo(JobRelationshipType.BLOCKED_BY);
    }

    @Test
    @DisplayName("Should throw NotFoundException when project does not exist on create")
    void create_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(projectId, sourceJob.getId(), targetJob.getId(),
                JobRelationshipType.RELATED_TO, memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a project member on create")
    void create_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(projectId, sourceJob.getId(), targetJob.getId(),
                JobRelationshipType.RELATED_TO, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("Should throw BadRequestException when source and target are the same job")
    void create_shouldThrow_whenSelfReference() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> service.create(projectId, sourceJob.getId(), sourceJob.getId(),
                JobRelationshipType.RELATED_TO, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A job cannot have a relationship with itself");
    }

    @Test
    @DisplayName("Should throw NotFoundException when source job does not exist")
    void create_shouldThrow_whenSourceJobNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(sourceJob.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(projectId, sourceJob.getId(), targetJob.getId(),
                JobRelationshipType.RELATED_TO, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when target job does not exist")
    void create_shouldThrow_whenTargetJobNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(sourceJob.getId())).thenReturn(Optional.of(sourceJob));
        when(jobRepository.findByIdAndDeletedAtIsNull(targetJob.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(projectId, sourceJob.getId(), targetJob.getId(),
                JobRelationshipType.RELATED_TO, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when source job belongs to a different project")
    void create_shouldThrow_whenSourceJobInDifferentProject() {
        JobModel foreignJob = JobModel.builder().id(UUID.randomUUID()).projectId(UUID.randomUUID())
                .title("Foreign").status(JobStatus.NEW).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(foreignJob.getId())).thenReturn(Optional.of(foreignJob));

        assertThatThrownBy(() -> service.create(projectId, foreignJob.getId(), targetJob.getId(),
                JobRelationshipType.RELATED_TO, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when target job belongs to a different project")
    void create_shouldThrow_whenTargetJobInDifferentProject() {
        JobModel foreignJob = JobModel.builder().id(UUID.randomUUID()).projectId(UUID.randomUUID())
                .title("Foreign").status(JobStatus.NEW).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(sourceJob.getId())).thenReturn(Optional.of(sourceJob));
        when(jobRepository.findByIdAndDeletedAtIsNull(foreignJob.getId())).thenReturn(Optional.of(foreignJob));

        assertThatThrownBy(() -> service.create(projectId, sourceJob.getId(), foreignJob.getId(),
                JobRelationshipType.RELATED_TO, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    @DisplayName("Should throw ConflictException when relationship already exists")
    void create_shouldThrow_whenDuplicate() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRepository.findByIdAndDeletedAtIsNull(sourceJob.getId())).thenReturn(Optional.of(sourceJob));
        when(jobRepository.findByIdAndDeletedAtIsNull(targetJob.getId())).thenReturn(Optional.of(targetJob));
        when(jobRelationshipRepository.existsBySourceJobIdAndTargetJobIdAndType(
                sourceJob.getId(), targetJob.getId(), JobRelationshipType.BLOCKED_BY)).thenReturn(true);

        assertThatThrownBy(() -> service.create(projectId, sourceJob.getId(), targetJob.getId(),
                JobRelationshipType.BLOCKED_BY, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This relationship already exists");
    }

    // --- delete ---

    @Test
    @DisplayName("Should delete a relationship when requester is member and job is the source")
    void delete_shouldDeleteRelationship_fromSourceSide() {
        UUID relationshipId = UUID.randomUUID();
        JobRelationshipModel relationship = JobRelationshipModel.builder()
                .id(relationshipId)
                .sourceJobId(sourceJob.getId())
                .targetJobId(targetJob.getId())
                .type(JobRelationshipType.RELATED_TO)
                .createdBy(ownerId)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(jobRelationshipRepository.findById(relationshipId)).thenReturn(Optional.of(relationship));

        service.delete(projectId, sourceJob.getId(), relationshipId, memberId);

        verify(jobRelationshipRepository).deleteById(relationshipId);
    }

    @Test
    @DisplayName("Should delete a relationship when requester is member and job is the target")
    void delete_shouldDeleteRelationship_fromTargetSide() {
        UUID relationshipId = UUID.randomUUID();
        JobRelationshipModel relationship = JobRelationshipModel.builder()
                .id(relationshipId)
                .sourceJobId(sourceJob.getId())
                .targetJobId(targetJob.getId())
                .type(JobRelationshipType.RELATED_TO)
                .createdBy(ownerId)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(jobRelationshipRepository.findById(relationshipId)).thenReturn(Optional.of(relationship));

        service.delete(projectId, targetJob.getId(), relationshipId, memberId);

        verify(jobRelationshipRepository).deleteById(relationshipId);
    }

    @Test
    @DisplayName("Should throw NotFoundException when project does not exist on delete")
    void delete_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(projectId, sourceJob.getId(), UUID.randomUUID(), ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a project member on delete")
    void delete_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(projectId, sourceJob.getId(), UUID.randomUUID(), ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("Should throw NotFoundException when relationship does not exist on delete")
    void delete_shouldThrow_whenRelationshipNotFound() {
        UUID relationshipId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRelationshipRepository.findById(relationshipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(projectId, sourceJob.getId(), relationshipId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job relationship not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when relationship exists but does not belong to the given job")
    void delete_shouldThrow_whenRelationshipBelongsToDifferentJob() {
        UUID relationshipId = UUID.randomUUID();
        UUID unrelatedJobId = UUID.randomUUID();
        JobRelationshipModel relationship = JobRelationshipModel.builder()
                .id(relationshipId)
                .sourceJobId(sourceJob.getId())
                .targetJobId(targetJob.getId())
                .type(JobRelationshipType.RELATED_TO)
                .createdBy(ownerId)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobRelationshipRepository.findById(relationshipId)).thenReturn(Optional.of(relationship));

        assertThatThrownBy(() -> service.delete(projectId, unrelatedJobId, relationshipId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job relationship not found");
    }
}
