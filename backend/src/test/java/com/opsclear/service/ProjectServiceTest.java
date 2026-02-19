package com.opsclear.service;

import com.opsclear.dto.CreateProjectRequest;
import com.opsclear.dto.UpdateProjectRequest;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
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

    private ProjectService projectService;

    private UserModel testOwner;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, userRepository);
        ownerId = UUID.randomUUID();
        testOwner = UserModel.builder()
                .id(ownerId)
                .email("owner@example.com")
                .name("Test Owner")
                .build();
    }

    @Test
    @DisplayName("Should create project for existing user")
    void create_shouldCreateProject_forExistingUser() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("Acme Corp")
                .description("Main project")
                .build();

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(testOwner));
        when(projectRepository.save(any(ProjectModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModel result = projectService.create(request, ownerId);

        assertThat(result.getName()).isEqualTo("Acme Corp");
        assertThat(result.getDescription()).isEqualTo("Main project");
        assertThat(result.getOwnerId()).isEqualTo(ownerId);

        ArgumentCaptor<ProjectModel> captor = ArgumentCaptor.forClass(ProjectModel.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Acme Corp");
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
    @DisplayName("Should return projects for owner")
    void getProjectsByOwner_shouldReturnProjects() {
        ProjectModel project = ProjectModel.builder()
                .id(UUID.randomUUID())
                .name("Acme Corp")
                .ownerId(ownerId)
                .ownerName(testOwner.getName())
                .build();

        when(projectRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(List.of(project));

        List<ProjectModel> result = projectService.getProjectsByOwner(ownerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("Should return project by ID")
    void getById_shouldReturnProject() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId)
                .name("Acme Corp")
                .ownerId(ownerId)
                .ownerName(testOwner.getName())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
                .thenReturn(Optional.of(project));

        ProjectModel result = projectService.getById(projectId);

        assertThat(result.getId()).isEqualTo(projectId);
        assertThat(result.getName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("Should throw NotFoundException when project not found")
    void getById_shouldThrow_whenNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(projectId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Should update project name and description")
    void update_shouldUpdateProject() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId)
                .name("Old Name")
                .description("Old desc")
                .ownerId(ownerId)
                .ownerName(testOwner.getName())
                .build();

        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .name("New Name")
                .description("New desc")
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
                .thenReturn(Optional.of(project));
        when(projectRepository.save(any(ProjectModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectModel result = projectService.update(projectId, request);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New desc");
    }

    @Test
    @DisplayName("Should soft delete project")
    void softDelete_shouldSetDeletedAt() {
        UUID projectId = UUID.randomUUID();
        ProjectModel project = ProjectModel.builder()
                .id(projectId)
                .name("Acme Corp")
                .ownerId(ownerId)
                .ownerName(testOwner.getName())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
                .thenReturn(Optional.of(project));
        when(projectRepository.save(any(ProjectModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        projectService.softDelete(projectId);

        ArgumentCaptor<ProjectModel> captor = ArgumentCaptor.forClass(ProjectModel.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw NotFoundException when soft deleting non-existent project")
    void softDelete_shouldThrow_whenNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.softDelete(projectId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }
}
