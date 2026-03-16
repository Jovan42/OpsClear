package com.opsclear.integration;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockReasonRepository blockReasonRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        UserModel testUser = UserModel.builder()
                .id(userId)
                .email("testuser@example.com")
                .name("Test User")
                .build();
        userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should create a project")
    void createProject_shouldReturn201() throws Exception {
        String body = """
                {
                  "name": "Acme Corp",
                  "description": "Main operations"
                }
                """;

        mockMvc.perform(post(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.description").value("Main operations"))
                .andExpect(jsonPath("$.ownerId").value(userId.toString()))
                .andExpect(jsonPath("$.id").exists());

        assertThat(projectRepository.findByOwnerIdAndDeletedAtIsNull(userId)).hasSize(1);
    }

    @Test
    @DisplayName("Should return 400 when creating project without name")
    void createProject_shouldReturn400_whenNameMissing() throws Exception {
        String body = """
                {
                  "description": "No name project"
                }
                """;

        mockMvc.perform(post(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    @DisplayName("Should return 401 without authentication")
    void createProject_shouldReturn401_withoutAuth() throws Exception {
        String body = """
                {
                  "name": "Acme Corp"
                }
                """;

        mockMvc.perform(post(ApiPaths.PROJECTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should list projects for authenticated user")
    void listProjects_shouldReturnUserProjects() throws Exception {
        createTestProject("Project A", null);
        createTestProject("Project B", null);

        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[1].name").exists());
    }

    @Test
    @DisplayName("Should not list soft-deleted projects")
    void listProjects_shouldExcludeDeleted() throws Exception {
        createTestProject("Active Project", null);
        ProjectModel deleted = createTestProject("Deleted Project", null);
        deleted.softDelete();
        projectRepository.save(deleted);

        mockMvc.perform(get(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Active Project"));
    }

    @Test
    @DisplayName("Should get project by ID")
    void getProject_shouldReturnProject() throws Exception {
        ProjectModel project = createTestProject("Acme Corp", "Description");

        mockMvc.perform(get(ApiPaths.project(project.getId()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.description").value("Description"))
                .andExpect(jsonPath("$.ownerId").value(userId.toString()));
    }

    @Test
    @DisplayName("Should return 404 for non-existent project")
    void getProject_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(get(ApiPaths.project(UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("Should update project")
    void updateProject_shouldReturnUpdated() throws Exception {
        ProjectModel project = createTestProject("Old Name", "Old desc");

        String body = """
                {
                  "name": "New Name",
                  "description": "New desc"
                }
                """;

        mockMvc.perform(put(ApiPaths.project(project.getId()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.description").value("New desc"));
    }

    @Test
    @DisplayName("Should soft delete project and return 204")
    void deleteProject_shouldReturn204() throws Exception {
        ProjectModel project = createTestProject("To Delete", null);

        assertThat(project.isDeleted()).isFalse();

        mockMvc.perform(delete(ApiPaths.project(project.getId()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isNoContent());

        assertThat(projectRepository.findByIdAndDeletedAtIsNull(project.getId())).isEmpty();
        assertThat(projectRepository.findById(project.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent project")
    void deleteProject_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.project(UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 403 when non-member tries to get project by ID")
    void getProject_shouldReturn403_whenNotMember() throws Exception {
        ProjectModel project = createTestProject("Acme Corp", null);

        UUID outsiderId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(outsiderId).email("outsider@example.com").name("Outsider").build());

        mockMvc.perform(get(ApiPaths.project(project.getId()))
                        .with(jwt().jwt(j -> j
                                .subject(outsiderId.toString())
                                .claim("email", "outsider@example.com")
                                .claim("name", "Outsider"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when MEMBER tries to update project")
    void updateProject_shouldReturn403_whenMember() throws Exception {
        ProjectModel project = createTestProject("Test Project", null);

        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("member@example.com").name("Member User").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project.getId()).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        String body = """
                { "name": "New Name" }
                """;

        mockMvc.perform(put(ApiPaths.project(project.getId()))
                        .with(jwt().jwt(j -> j
                                .subject(memberId.toString())
                                .claim("email", "member@example.com")
                                .claim("name", "Member User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when ADMIN tries to delete project")
    void deleteProject_shouldReturn403_whenAdmin() throws Exception {
        ProjectModel project = createTestProject("Test Project", null);

        UUID adminId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(adminId).email("admin@example.com").name("Admin User").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project.getId()).userId(adminId).role(ProjectMemberRole.ADMIN).build());

        mockMvc.perform(delete(ApiPaths.project(project.getId()))
                        .with(jwt().jwt(j -> j
                                .subject(adminId.toString())
                                .claim("email", "admin@example.com")
                                .claim("name", "Admin User"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 409 when project name already exists for the same owner")
    void createProject_shouldReturn409_whenNameAlreadyExistsForSameOwner() throws Exception {
        createTestProject("Acme Corp", null);

        String body = """
                { "name": "Acme Corp" }
                """;

        mockMvc.perform(post(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("Should return 201 when project name is reused after soft delete")
    void createProject_shouldReturn201_whenNameReusedAfterSoftDelete() throws Exception {
        ProjectModel deleted = createTestProject("Reusable Name", null);
        deleted.softDelete();
        projectRepository.save(deleted);

        String body = """
                { "name": "Reusable Name" }
                """;

        mockMvc.perform(post(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Reusable Name"));
    }

    @Test
    @DisplayName("Should return 409 when updating project name to one taken by another active project")
    void updateProject_shouldReturn409_whenNameTakenByAnotherActiveProject() throws Exception {
        createTestProject("Taken Name", null);
        ProjectModel other = createTestProject("Other Project", null);

        String body = """
                { "name": "Taken Name" }
                """;

        mockMvc.perform(put(ApiPaths.project(other.getId()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("Should return 200 when updating project with unchanged name")
    void updateProject_shouldReturn200_whenNameIsUnchanged() throws Exception {
        ProjectModel project = createTestProject("Same Name", null);

        String body = """
                { "name": "Same Name", "description": "Updated desc" }
                """;

        mockMvc.perform(put(ApiPaths.project(project.getId()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Same Name"))
                .andExpect(jsonPath("$.description").value("Updated desc"));
    }

    // --- PATCH /{id}/status ---

    @Test
    @DisplayName("Should complete project when it has no open jobs")
    void patchStatus_shouldCompleteProject_whenNoOpenJobs() throws Exception {
        ProjectModel project = createTestProject("Acme Corp", null);

        mockMvc.perform(patch(ApiPaths.projectStatus(project.getId()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(projectRepository.findByIdAndDeletedAtIsNull(project.getId()))
                .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(ProjectStatus.COMPLETED));
    }

    @Test
    @DisplayName("Should return 409 when completing project with open jobs")
    void patchStatus_shouldReturn409_whenProjectHasOpenJobs() throws Exception {
        ProjectModel project = createTestProject("Acme Corp", null);
        createTestJob(project.getId());

        mockMvc.perform(patch(ApiPaths.projectStatus(project.getId()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("Should reactivate a completed project")
    void patchStatus_shouldReactivateProject() throws Exception {
        ProjectModel project = createTestProject("Acme Corp", null);
        project.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(project);

        mockMvc.perform(patch(ApiPaths.projectStatus(project.getId()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "ACTIVE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Should return 403 when ADMIN tries to change project status")
    void patchStatus_shouldReturn403_whenAdmin() throws Exception {
        ProjectModel project = createTestProject("Acme Corp", null);

        UUID adminId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(adminId).email("admin@example.com").name("Admin User").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project.getId()).userId(adminId).role(ProjectMemberRole.ADMIN).build());

        mockMvc.perform(patch(ApiPaths.projectStatus(project.getId()))
                        .with(jwt().jwt(j -> j
                                .subject(adminId.toString())
                                .claim("email", "admin@example.com")
                                .claim("name", "Admin User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when MEMBER tries to change project status")
    void patchStatus_shouldReturn403_whenMember() throws Exception {
        ProjectModel project = createTestProject("Acme Corp", null);

        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(memberId).email("member@example.com").name("Member User").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project.getId()).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        mockMvc.perform(patch(ApiPaths.projectStatus(project.getId()))
                        .with(jwt().jwt(j -> j
                                .subject(memberId.toString())
                                .claim("email", "member@example.com")
                                .claim("name", "Member User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when patching status of non-existent project")
    void patchStatus_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(patch(ApiPaths.projectStatus(UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 400 when status field is missing")
    void patchStatus_shouldReturn400_whenStatusIsNull() throws Exception {
        ProjectModel project = createTestProject("Acme Corp", null);

        mockMvc.perform(patch(ApiPaths.projectStatus(project.getId()))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    // --- List filtering by status ---

    @Test
    @DisplayName("Should return only completed projects when filtered by COMPLETED")
    void listProjects_shouldReturnOnlyCompleted_whenFilteredByCompleted() throws Exception {
        createTestProject("Active Project", null);
        ProjectModel completed = createTestProject("Completed Project", null);
        completed.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(completed);

        mockMvc.perform(get(ApiPaths.projectsByStatus("COMPLETED"))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Completed Project"));
    }

    @Test
    @DisplayName("Should return all projects when filtered by ALL")
    void listProjects_shouldReturnAll_whenFilteredByAll() throws Exception {
        createTestProject("Active Project", null);
        ProjectModel completed = createTestProject("Completed Project", null);
        completed.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(completed);

        mockMvc.perform(get(ApiPaths.projectsByStatus("ALL"))
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    private ProjectModel createTestProject(String name, String description) {
        ProjectModel project = ProjectModel.builder()
                .name(name)
                .description(description)
                .ownerId(userId)
                .build();
        project = projectRepository.save(project);
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project.getId())
                .userId(userId)
                .role(ProjectMemberRole.OWNER)
                .build());
        return project;
    }

    private void createTestJob(UUID projectId) {
        jobRepository.save(JobModel.builder()
                .projectId(projectId)
                .title("Test Job")
                .status(JobStatus.IN_PROGRESS)
                .createdBy(userId)
                .build());
    }
}
