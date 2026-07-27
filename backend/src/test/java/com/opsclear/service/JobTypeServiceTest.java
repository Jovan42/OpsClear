package com.opsclear.service;

import com.opsclear.dto.CreateJobTypeRequest;
import com.opsclear.dto.UpdateJobTypeRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobTypeColor;
import com.opsclear.model.JobTypeModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobTypeRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobTypeService")
class JobTypeServiceTest {

    @Mock private JobTypeRepository jobTypeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private JobTypeService jobTypeService;

    private UUID projectId;
    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private ProjectModel project;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel adminMembership;
    private ProjectMemberModel memberMembership;

    @BeforeEach
    void setUp() {
        jobTypeService = new JobTypeService(jobTypeRepository, projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        project = ProjectModel.builder().id(projectId).name("Test Project").ownerId(ownerId).build();

        ownerMembership = ProjectMemberModel.builder().projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();
        adminMembership = ProjectMemberModel.builder().projectId(projectId).userId(adminId).role(ProjectMemberRole.ADMIN).build();
        memberMembership = ProjectMemberModel.builder().projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();
    }

    // --- list ---

    @Test
    @DisplayName("Any member should be able to list job types")
    void list_shouldReturnTypes_forMember() {
        List<JobTypeModel> types = List.of(
                JobTypeModel.builder().id(UUID.randomUUID()).projectId(projectId).name("Bug").color(JobTypeColor.RED).build());

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));
        when(jobTypeRepository.findByProjectId(projectId)).thenReturn(types);

        List<JobTypeModel> result = jobTypeService.list(projectId, memberId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Bug");
    }

    @Test
    @DisplayName("list should throw NotFoundException when project does not exist")
    void list_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTypeService.list(projectId, memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("list should throw ForbiddenException when caller is not a member")
    void list_shouldThrow_whenCallerNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTypeService.list(projectId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- create ---

    @Test
    @DisplayName("OWNER should be able to create a job type with the next display order")
    void create_shouldCreateType_forOwner() {
        CreateJobTypeRequest request = CreateJobTypeRequest.builder().name("Bug").color(JobTypeColor.RED).build();
        JobTypeModel saved = JobTypeModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).name("Bug").color(JobTypeColor.RED).displayOrder(2).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTypeRepository.nextDisplayOrder(projectId)).thenReturn(2);
        when(jobTypeRepository.save(any())).thenReturn(saved);

        JobTypeModel result = jobTypeService.create(projectId, request, ownerId);

        assertThat(result.getName()).isEqualTo("Bug");
        assertThat(result.getDisplayOrder()).isEqualTo(2);
        ArgumentCaptor<JobTypeModel> captor = ArgumentCaptor.forClass(JobTypeModel.class);
        verify(jobTypeRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Bug");
        assertThat(captor.getValue().getColor()).isEqualTo(JobTypeColor.RED);
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("ADMIN should be able to create a job type")
    void create_shouldCreateType_forAdmin() {
        CreateJobTypeRequest request = CreateJobTypeRequest.builder().name("Feature").color(JobTypeColor.BLUE).build();
        JobTypeModel saved = JobTypeModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).name("Feature").color(JobTypeColor.BLUE).displayOrder(0).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId)).thenReturn(Optional.of(adminMembership));
        when(jobTypeRepository.nextDisplayOrder(projectId)).thenReturn(0);
        when(jobTypeRepository.save(any())).thenReturn(saved);

        JobTypeModel result = jobTypeService.create(projectId, request, adminId);

        assertThat(result.getName()).isEqualTo("Feature");
    }

    @Test
    @DisplayName("MEMBER should not be able to create a job type")
    void create_shouldThrow_forMember() {
        CreateJobTypeRequest request = CreateJobTypeRequest.builder().name("Bug").color(JobTypeColor.RED).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> jobTypeService.create(projectId, request, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
        verify(jobTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create should strip whitespace from the name")
    void create_shouldStripName() {
        CreateJobTypeRequest request = CreateJobTypeRequest.builder().name("  Bug  ").color(JobTypeColor.RED).build();
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTypeRepository.nextDisplayOrder(projectId)).thenReturn(0);
        when(jobTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobTypeService.create(projectId, request, ownerId);

        ArgumentCaptor<JobTypeModel> captor = ArgumentCaptor.forClass(JobTypeModel.class);
        verify(jobTypeRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Bug");
    }

    // --- update ---

    @Test
    @DisplayName("OWNER should be able to update a job type")
    void update_shouldUpdateType_forOwner() {
        UUID typeId = UUID.randomUUID();
        JobTypeModel existing = JobTypeModel.builder()
                .id(typeId).projectId(projectId).name("Bug").color(JobTypeColor.RED).displayOrder(0).build();
        UpdateJobTypeRequest request = UpdateJobTypeRequest.builder()
                .name("Defect").color(JobTypeColor.ORANGE).displayOrder(1).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTypeRepository.findById(typeId)).thenReturn(Optional.of(existing));
        when(jobTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobTypeModel result = jobTypeService.update(projectId, typeId, request, ownerId);

        assertThat(result.getName()).isEqualTo("Defect");
        assertThat(result.getColor()).isEqualTo(JobTypeColor.ORANGE);
        assertThat(result.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("MEMBER should not be able to update a job type")
    void update_shouldThrow_forMember() {
        UUID typeId = UUID.randomUUID();
        UpdateJobTypeRequest request = UpdateJobTypeRequest.builder()
                .name("Defect").color(JobTypeColor.ORANGE).displayOrder(1).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> jobTypeService.update(projectId, typeId, request, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("update should throw NotFoundException when type does not exist")
    void update_shouldThrow_whenTypeNotFound() {
        UUID typeId = UUID.randomUUID();
        UpdateJobTypeRequest request = UpdateJobTypeRequest.builder()
                .name("Defect").color(JobTypeColor.ORANGE).displayOrder(1).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTypeRepository.findById(typeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTypeService.update(projectId, typeId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job type not found");
    }

    @Test
    @DisplayName("update should throw NotFoundException when type belongs to a different project")
    void update_shouldThrow_whenTypeBelongsToDifferentProject() {
        UUID typeId = UUID.randomUUID();
        JobTypeModel otherProjectType = JobTypeModel.builder()
                .id(typeId).projectId(UUID.randomUUID()).name("Bug").color(JobTypeColor.RED).build();
        UpdateJobTypeRequest request = UpdateJobTypeRequest.builder()
                .name("Defect").color(JobTypeColor.ORANGE).displayOrder(1).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTypeRepository.findById(typeId)).thenReturn(Optional.of(otherProjectType));

        assertThatThrownBy(() -> jobTypeService.update(projectId, typeId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job type not found");
    }

    // --- delete ---

    @Test
    @DisplayName("OWNER should be able to delete a job type with no job references")
    void delete_shouldDeleteType_whenNoJobsReference() {
        UUID typeId = UUID.randomUUID();
        JobTypeModel existing = JobTypeModel.builder().id(typeId).projectId(projectId).name("Bug").color(JobTypeColor.RED).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTypeRepository.findById(typeId)).thenReturn(Optional.of(existing));
        when(jobTypeRepository.countJobsReferencing(typeId)).thenReturn(0);

        jobTypeService.delete(projectId, typeId, ownerId);

        verify(jobTypeRepository).delete(typeId);
    }

    @Test
    @DisplayName("MEMBER should not be able to delete a job type")
    void delete_shouldThrow_forMember() {
        UUID typeId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> jobTypeService.delete(projectId, typeId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
        verify(jobTypeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete should throw NotFoundException when type does not exist")
    void delete_shouldThrow_whenTypeNotFound() {
        UUID typeId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTypeRepository.findById(typeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobTypeService.delete(projectId, typeId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Job type not found");
        verify(jobTypeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete should throw ConflictException when jobs still reference the type")
    void delete_shouldThrow_whenJobsStillReference() {
        UUID typeId = UUID.randomUUID();
        JobTypeModel existing = JobTypeModel.builder().id(typeId).projectId(projectId).name("Bug").color(JobTypeColor.RED).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(jobTypeRepository.findById(typeId)).thenReturn(Optional.of(existing));
        when(jobTypeRepository.countJobsReferencing(typeId)).thenReturn(3);

        assertThatThrownBy(() -> jobTypeService.delete(projectId, typeId, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cannot delete type: 3 job(s) still use this type");
        verify(jobTypeRepository, never()).delete(any());
    }
}
