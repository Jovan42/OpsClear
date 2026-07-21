package com.opsclear.service;

import com.opsclear.dto.CreateProjectRequest;
import com.opsclear.dto.UpdateProjectRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectLinkRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private BlockReasonRepository blockReasonRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private ProjectLinkRepository projectLinkRepository;

    @Mock
    private FriendlyIdService friendlyIdService;

    private ProjectService projectService;

    private UserModel testOwner;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, userRepository, projectMemberRepository, blockReasonRepository, organisationRepository, projectLinkRepository, friendlyIdService);
        ownerId = UUID.randomUUID();
        testOwner = UserModel.builder()
                .id(ownerId)
                .email("owner@example.com")
                .name("Test Owner")
                .build();
        OrganisationModel org = OrganisationModel.builder()
                .id(UUID.randomUUID()).name("Test Org").slug("TST").createdBy(ownerId).build();
        // Default: all callers belong to an org. Individual tests override for "no org" scenarios.
        when(organisationRepository.findByMember(any())).thenReturn(Optional.of(org));
    }

    @Test
    @DisplayName("Should create project and add creator as OWNER member")
    void create_shouldCreateProjectAndAddOwnerMembership() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("Acme Corp")
                .description("Main project")
                .build();

        ProjectModel saved = ProjectModel.builder()
                .id(UUID.randomUUID())
                .name("Acme Corp")
                .description("Main project")
                .ownerId(ownerId)
                .build();

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(testOwner));
        when(projectRepository.save(any(ProjectModel.class))).thenReturn(saved);
        when(projectMemberRepository.save(any(ProjectMemberModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModel result = projectService.create(request, ownerId);

        assertThat(result.getName()).isEqualTo("Acme Corp");
        assertThat(result.getOwnerId()).isEqualTo(ownerId);

        ArgumentCaptor<ProjectMemberModel> memberCaptor = ArgumentCaptor.forClass(ProjectMemberModel.class);
        verify(projectMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(ProjectMemberRole.OWNER);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("Should seed block reasons when provided at project creation")
    void create_shouldSeedBlockReasons_whenProvided() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("Acme Corp")
                .blockReasons(List.of("Waiting on client", "Missing spec"))
                .build();

        ProjectModel saved = ProjectModel.builder()
                .id(UUID.randomUUID())
                .name("Acme Corp")
                .ownerId(ownerId)
                .build();

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(testOwner));
        when(projectRepository.save(any(ProjectModel.class))).thenReturn(saved);
        when(projectMemberRepository.save(any(ProjectMemberModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        projectService.create(request, ownerId);

        verify(blockReasonRepository, times(2)).findOrCreate(any(UUID.class), any(String.class));
    }

    @Test
    @DisplayName("Should not call blockReasonRepository when no block reasons provided")
    void create_shouldNotSeedBlockReasons_whenNoneProvided() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("Acme Corp")
                .build();

        ProjectModel saved = ProjectModel.builder()
                .id(UUID.randomUUID())
                .name("Acme Corp")
                .ownerId(ownerId)
                .build();

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(testOwner));
        when(projectRepository.save(any(ProjectModel.class))).thenReturn(saved);
        when(projectMemberRepository.save(any(ProjectMemberModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        projectService.create(request, ownerId);

        verify(blockReasonRepository, never()).findOrCreate(any(UUID.class), any(String.class));
    }

    @Test
    @DisplayName("create_shouldThrowConflictException_whenNameAlreadyExistsForSameOwner")
    void create_shouldThrow_whenNameAlreadyExistsForSameOwner() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("Acme Corp")
                .build();

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(testOwner));
        when(projectRepository.existsByNameAndOwnerIdAndDeletedAtIsNull("Acme Corp", ownerId)).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(request, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A project with this name already exists");
    }

    @Test
    @DisplayName("Should throw NotFoundException when user does not exist")
    void create_shouldThrow_whenUserNotFound() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("Acme Corp")
                .build();

        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.create(request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("Should return active projects where user is a member")
    void getProjectsForMember_shouldReturnProjects() {
        ProjectModel project = ProjectModel.builder()
                .id(UUID.randomUUID())
                .name("Acme Corp")
                .ownerId(ownerId)
                .ownerName(testOwner.getName())
                .status(ProjectStatus.ACTIVE)
                .build();

        when(projectRepository.findByMemberIdAndStatusAndDeletedAtIsNull(ownerId, ProjectStatus.ACTIVE))
                .thenReturn(List.of(project));

        List<ProjectModel> result = projectService.getProjectsForMember(ownerId, ProjectStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("Should return project by ID for a member")
    void getById_shouldReturnProject_forMember() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId)
                .name("Acme Corp")
                .ownerId(ownerId)
                .ownerName(testOwner.getName())
                .build();

        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(ownerId)
                .role(ProjectMemberRole.MEMBER)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(membership));

        ProjectModel result = projectService.getById(projectId, ownerId);

        assertThat(result.getId()).isEqualTo(projectId);
        assertThat(result.getName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("Should throw NotFoundException when project not found")
    void getById_shouldThrow_whenNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(projectId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when requester is not a member")
    void getById_shouldThrow_whenNotMember() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder().id(projectId).name("Acme Corp").ownerId(ownerId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(projectId, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("Should update project name and description for OWNER")
    void update_shouldUpdateProject_forOwner() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId)
                .name("Old Name")
                .description("Old desc")
                .ownerId(ownerId)
                .ownerName(testOwner.getName())
                .build();

        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(ownerId)
                .role(ProjectMemberRole.OWNER)
                .build();

        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .name("New Name")
                .description("New desc")
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(membership));
        when(projectRepository.save(any(ProjectModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModel result = projectService.update(projectId, request, ownerId);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New desc");
    }

    @Test
    @DisplayName("update_shouldThrowConflictException_whenNameTakenByAnotherActiveProject")
    void update_shouldThrow_whenNameTakenByAnotherActiveProject() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId).name("Old Name").ownerId(ownerId).build();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(membership));
        when(projectRepository.existsByNameAndOwnerIdAndIdNotAndDeletedAtIsNull("Taken Name", ownerId, projectId))
                .thenReturn(true);

        UpdateProjectRequest request = UpdateProjectRequest.builder().name("Taken Name").build();
        assertThatThrownBy(() -> projectService.update(projectId, request, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A project with this name already exists");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when MEMBER tries to update project")
    void update_shouldThrow_whenMemberRole() {
        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder().id(projectId).name("Acme").ownerId(ownerId).build();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(membership));

        UpdateProjectRequest request = UpdateProjectRequest.builder().name("x").build();
        assertThatThrownBy(() -> projectService.update(projectId, request, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("Should throw NotFoundException when project is not found on update")
    void update_shouldThrow_whenProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        UpdateProjectRequest request = UpdateProjectRequest.builder().name("x").build();
        assertThatThrownBy(() -> projectService.update(projectId, request, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when non-member tries to update project")
    void update_shouldThrow_whenNotMember() {
        UUID projectId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder().id(projectId).name("Acme").ownerId(ownerId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, outsiderId))
                .thenReturn(Optional.empty());

        UpdateProjectRequest request = UpdateProjectRequest.builder().name("x").build();
        assertThatThrownBy(() -> projectService.update(projectId, request, outsiderId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("Should soft delete project for OWNER")
    void softDelete_shouldSetDeletedAt_forOwner() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId)
                .name("Acme Corp")
                .ownerId(ownerId)
                .ownerName(testOwner.getName())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(ownerId)
                .role(ProjectMemberRole.OWNER)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(membership));
        when(projectRepository.save(any(ProjectModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        projectService.softDelete(projectId, ownerId);

        ArgumentCaptor<ProjectModel> captor = ArgumentCaptor.forClass(ProjectModel.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw ForbiddenException when ADMIN tries to delete project")
    void softDelete_shouldThrow_whenAdminRole() {
        UUID projectId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder().id(projectId).name("Acme").ownerId(ownerId).build();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId).userId(adminId).role(ProjectMemberRole.ADMIN).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> projectService.softDelete(projectId, adminId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER role required");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when non-member tries to delete project")
    void softDelete_shouldThrow_whenNotMember() {
        UUID projectId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder().id(projectId).name("Acme").ownerId(ownerId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, outsiderId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.softDelete(projectId, outsiderId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("Should throw NotFoundException when soft deleting non-existent project")
    void softDelete_shouldThrow_whenNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.softDelete(projectId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    // --- updateStatus ---

    @Test
    @DisplayName("updateStatus_shouldCompleteProject_whenNoOpenJobs")
    void updateStatus_shouldCompleteProject_whenNoOpenJobs() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId).name("Acme").ownerId(ownerId).status(ProjectStatus.ACTIVE).build();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(membership));
        when(projectRepository.countOpenJobsByProjectId(projectId)).thenReturn(0);
        when(projectRepository.save(any(ProjectModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModel result = projectService.updateStatus(projectId, ProjectStatus.COMPLETED, ownerId);

        assertThat(result.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        ArgumentCaptor<ProjectModel> captor = ArgumentCaptor.forClass(ProjectModel.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProjectStatus.COMPLETED);
    }

    @Test
    @DisplayName("updateStatus_shouldThrowConflictException_whenProjectHasOpenJobs")
    void updateStatus_shouldThrowConflict_whenProjectHasOpenJobs() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId).name("Acme").ownerId(ownerId).status(ProjectStatus.ACTIVE).build();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(membership));
        when(projectRepository.countOpenJobsByProjectId(projectId)).thenReturn(3);

        assertThatThrownBy(() -> projectService.updateStatus(projectId, ProjectStatus.COMPLETED, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3 job(s)");
    }

    @Test
    @DisplayName("updateStatus_shouldReactivateProject_whenSettingActive")
    void updateStatus_shouldReactivateProject_whenSettingActive() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId).name("Acme").ownerId(ownerId).status(ProjectStatus.COMPLETED).build();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(membership));
        when(projectRepository.save(any(ProjectModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModel result = projectService.updateStatus(projectId, ProjectStatus.ACTIVE, ownerId);

        assertThat(result.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        verify(projectRepository, never()).countOpenJobsByProjectId(any());
    }

    @Test
    @DisplayName("updateStatus_shouldThrowForbiddenException_whenRequesterIsAdmin")
    void updateStatus_shouldThrowForbidden_whenAdmin() {
        UUID projectId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder().id(projectId).name("Acme").ownerId(ownerId).build();
        ProjectMemberModel membership = ProjectMemberModel.builder()
                .projectId(projectId).userId(adminId).role(ProjectMemberRole.ADMIN).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, adminId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> projectService.updateStatus(projectId, ProjectStatus.COMPLETED, adminId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER role required");
    }

    @Test
    @DisplayName("updateStatus_shouldThrowForbiddenException_whenRequesterIsNotMember")
    void updateStatus_shouldThrowForbidden_whenNotMember() {
        UUID projectId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder().id(projectId).name("Acme").ownerId(ownerId).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, outsiderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateStatus(projectId, ProjectStatus.COMPLETED, outsiderId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a member of this project");
    }

    @Test
    @DisplayName("updateStatus_shouldThrowNotFoundException_whenProjectNotFound")
    void updateStatus_shouldThrowNotFound_whenProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateStatus(projectId, ProjectStatus.COMPLETED, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    // --- Org membership enforcement ---

    @Test
    @DisplayName("create_shouldThrowForbiddenException_whenCallerHasNoOrg")
    void create_shouldThrow_whenCallerHasNoOrg() {
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.empty());

        CreateProjectRequest request = CreateProjectRequest.builder().name("Test").build();
        assertThatThrownBy(() -> projectService.create(request, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Organisation.NOT_IN_ORG);
    }

    @Test
    @DisplayName("getProjectsForMember_shouldThrowForbiddenException_whenCallerHasNoOrg")
    void getProjectsForMember_shouldThrow_whenCallerHasNoOrg() {
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectsForMember(ownerId, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Organisation.NOT_IN_ORG);
    }

    @Test
    @DisplayName("getById_shouldThrowForbiddenException_whenCallerHasNoOrg")
    void getById_shouldThrow_whenCallerHasNoOrg() {
        UUID projectId = UUID.randomUUID();
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(projectId, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Organisation.NOT_IN_ORG);
    }

    @Test
    @DisplayName("update_shouldThrowForbiddenException_whenCallerHasNoOrg")
    void update_shouldThrow_whenCallerHasNoOrg() {
        UUID projectId = UUID.randomUUID();
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.empty());

        UpdateProjectRequest request = UpdateProjectRequest.builder().name("x").build();
        assertThatThrownBy(() -> projectService.update(projectId, request, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Organisation.NOT_IN_ORG);
    }

    @Test
    @DisplayName("softDelete_shouldThrowForbiddenException_whenCallerHasNoOrg")
    void softDelete_shouldThrow_whenCallerHasNoOrg() {
        UUID projectId = UUID.randomUUID();
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.softDelete(projectId, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Organisation.NOT_IN_ORG);
    }

    @Test
    @DisplayName("updateStatus_shouldThrowForbiddenException_whenCallerHasNoOrg")
    void updateStatus_shouldThrow_whenCallerHasNoOrg() {
        UUID projectId = UUID.randomUUID();
        when(organisationRepository.findByMember(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateStatus(projectId, ProjectStatus.COMPLETED, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Organisation.NOT_IN_ORG);
    }

}
