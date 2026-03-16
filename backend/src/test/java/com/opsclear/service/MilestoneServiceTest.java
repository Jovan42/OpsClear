package com.opsclear.service;

import com.opsclear.dto.CreateMilestoneRequest;
import com.opsclear.dto.UpdateMilestoneRequest;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.MilestoneModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    @Mock private MilestoneRepository milestoneRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private MilestoneService milestoneService;

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
        milestoneService = new MilestoneService(milestoneRepository, projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        ownerId   = UUID.randomUUID();
        adminId   = UUID.randomUUID();
        memberId  = UUID.randomUUID();

        project = ProjectModel.builder().id(projectId).name("Test Project").ownerId(ownerId).build();

        ownerMembership  = ProjectMemberModel.builder().projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();
        adminMembership  = ProjectMemberModel.builder().projectId(projectId).userId(adminId).role(ProjectMemberRole.ADMIN).build();
        memberMembership = ProjectMemberModel.builder().projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();
    }

    // --- list ---

    @Test
    @DisplayName("Any member should be able to list milestones")
    void list_shouldReturnMilestones_forMember() {
        List<MilestoneModel> milestones = List.of(
                MilestoneModel.builder().id(UUID.randomUUID()).projectId(projectId).name("Alpha").build()
        );

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(milestoneRepository.findActiveByProjectId(projectId)).thenReturn(milestones);

        List<MilestoneModel> result = milestoneService.list(projectId, memberId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Alpha");
    }

    @Test
    @DisplayName("Should throw NotFoundException when project does not exist on list")
    void list_shouldThrow_whenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> milestoneService.list(projectId, memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a member on list")
    void list_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> milestoneService.list(projectId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- create ---

    @Test
    @DisplayName("OWNER should be able to create a milestone with stripped name")
    void create_shouldCreateMilestone_forOwner() {
        CreateMilestoneRequest request = CreateMilestoneRequest.builder()
                .name("  Sprint 1  ")
                .description("First sprint")
                .deadline(LocalDate.of(2026, 4, 1))
                .build();

        MilestoneModel saved = MilestoneModel.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name("Sprint 1")
                .description("First sprint")
                .deadline(LocalDate.of(2026, 4, 1))
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(milestoneRepository.save(any())).thenReturn(saved);

        MilestoneModel result = milestoneService.create(projectId, request, ownerId);

        assertThat(result.getName()).isEqualTo("Sprint 1");
        ArgumentCaptor<MilestoneModel> captor = ArgumentCaptor.forClass(MilestoneModel.class);
        verify(milestoneRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Sprint 1");
        assertThat(captor.getValue().getProjectId()).isEqualTo(projectId);
    }

    @Test
    @DisplayName("ADMIN should be able to create a milestone")
    void create_shouldCreateMilestone_forAdmin() {
        CreateMilestoneRequest request = CreateMilestoneRequest.builder().name("Beta").build();
        MilestoneModel saved = MilestoneModel.builder().id(UUID.randomUUID()).projectId(projectId).name("Beta").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId))
                .thenReturn(Optional.of(adminMembership));
        when(milestoneRepository.save(any())).thenReturn(saved);

        MilestoneModel result = milestoneService.create(projectId, request, adminId);

        assertThat(result.getName()).isEqualTo("Beta");
    }

    @Test
    @DisplayName("MEMBER should be forbidden from creating a milestone")
    void create_shouldThrow_whenMemberRole() {
        CreateMilestoneRequest request = CreateMilestoneRequest.builder().name("M1").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> milestoneService.create(projectId, request, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a project member on create")
    void create_shouldThrow_whenNotMember() {
        CreateMilestoneRequest request = CreateMilestoneRequest.builder().name("M1").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> milestoneService.create(projectId, request, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    // --- update ---

    @Test
    @DisplayName("OWNER should be able to update a milestone")
    void update_shouldUpdateMilestone_forOwner() {
        UUID milestoneId = UUID.randomUUID();
        MilestoneModel existing = MilestoneModel.builder()
                .id(milestoneId).projectId(projectId).name("Old Name").build();
        UpdateMilestoneRequest request = UpdateMilestoneRequest.builder()
                .name("  New Name  ").description("Updated desc").deadline(LocalDate.of(2026, 5, 1)).build();
        MilestoneModel updated = MilestoneModel.builder()
                .id(milestoneId).projectId(projectId).name("New Name").description("Updated desc").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)).thenReturn(Optional.of(existing));
        when(milestoneRepository.save(any())).thenReturn(updated);

        MilestoneModel result = milestoneService.update(projectId, milestoneId, request, ownerId);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(existing.getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("Should throw NotFoundException when milestone does not exist on update")
    void update_shouldThrow_whenMilestoneNotFound() {
        UUID milestoneId = UUID.randomUUID();
        UpdateMilestoneRequest request = UpdateMilestoneRequest.builder().name("X").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> milestoneService.update(projectId, milestoneId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Milestone not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when milestone belongs to a different project on update")
    void update_shouldThrow_whenMilestoneBelongsToDifferentProject() {
        UUID milestoneId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        MilestoneModel milestonInOther = MilestoneModel.builder()
                .id(milestoneId).projectId(otherProjectId).name("Other").build();
        UpdateMilestoneRequest request = UpdateMilestoneRequest.builder().name("X").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)).thenReturn(Optional.of(milestonInOther));

        assertThatThrownBy(() -> milestoneService.update(projectId, milestoneId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Milestone not found");
    }

    @Test
    @DisplayName("MEMBER should be forbidden from updating a milestone")
    void update_shouldThrow_whenMemberRole() {
        UUID milestoneId = UUID.randomUUID();
        UpdateMilestoneRequest request = UpdateMilestoneRequest.builder().name("X").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> milestoneService.update(projectId, milestoneId, request, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    // --- softDelete ---

    @Test
    @DisplayName("OWNER should be able to soft delete a milestone")
    void softDelete_shouldSoftDeleteMilestone_forOwner() {
        UUID milestoneId = UUID.randomUUID();
        MilestoneModel milestone = MilestoneModel.builder()
                .id(milestoneId).projectId(projectId).name("Sprint 1").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)).thenReturn(Optional.of(milestone));

        milestoneService.softDelete(projectId, milestoneId, ownerId);

        verify(milestoneRepository).softDelete(milestoneId);
    }

    @Test
    @DisplayName("MEMBER should be forbidden from deleting a milestone")
    void softDelete_shouldThrow_whenMemberRole() {
        UUID milestoneId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> milestoneService.softDelete(projectId, milestoneId, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("Should throw NotFoundException when milestone does not exist on softDelete")
    void softDelete_shouldThrow_whenMilestoneNotFound() {
        UUID milestoneId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> milestoneService.softDelete(projectId, milestoneId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Milestone not found");
    }

    @Test
    @DisplayName("Should throw NotFoundException when milestone belongs to a different project on softDelete")
    void softDelete_shouldThrow_whenMilestoneBelongsToDifferentProject() {
        UUID milestoneId = UUID.randomUUID();
        MilestoneModel otherMilestone = MilestoneModel.builder()
                .id(milestoneId).projectId(UUID.randomUUID()).name("Other").build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)).thenReturn(Optional.of(otherMilestone));

        assertThatThrownBy(() -> milestoneService.softDelete(projectId, milestoneId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Milestone not found");
    }

    // --- MilestoneModel ---

    @Test
    @DisplayName("isDeleted should return true when deletedAt is set")
    void milestoneModel_isDeleted_returnsTrueWhenDeletedAtIsSet() {
        MilestoneModel milestone = MilestoneModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).name("M").deletedAt(java.time.Instant.now()).build();

        assertThat(milestone.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("softDelete should set deletedAt")
    void milestoneModel_softDelete_setsDeletedAt() {
        MilestoneModel milestone = MilestoneModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).name("M").build();

        assertThat(milestone.isDeleted()).isFalse();
        milestone.softDelete();
        assertThat(milestone.isDeleted()).isTrue();
        assertThat(milestone.getDeletedAt()).isNotNull();
    }
}
