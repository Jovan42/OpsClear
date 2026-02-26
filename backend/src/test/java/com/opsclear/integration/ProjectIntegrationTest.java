package com.opsclear.integration;

import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
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

        mockMvc.perform(post("/api/projects")
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

        mockMvc.perform(post("/api/projects")
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

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should list projects for authenticated user")
    void listProjects_shouldReturnUserProjects() throws Exception {
        createTestProject("Project A", null);
        createTestProject("Project B", null);

        mockMvc.perform(get("/api/projects")
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

        mockMvc.perform(get("/api/projects")
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

        mockMvc.perform(get("/api/projects/" + project.getId())
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
        mockMvc.perform(get("/api/projects/" + UUID.randomUUID())
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

        mockMvc.perform(put("/api/projects/" + project.getId())
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

        mockMvc.perform(delete("/api/projects/" + project.getId())
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isNoContent());

        assertThat(projectRepository.findByIdAndDeletedAtIsNull(project.getId())).isEmpty();
        assertThat(projectRepository.findById(project.getId())).isPresent();
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent project")
    void deleteProject_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/projects/" + UUID.randomUUID())
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId.toString())
                                .claim("email", "testuser@example.com")
                                .claim("name", "Test User"))))
                .andExpect(status().isNotFound());
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
}
