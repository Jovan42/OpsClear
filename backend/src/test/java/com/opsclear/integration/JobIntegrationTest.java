package com.opsclear.integration;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JobRepository jobRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());
    }

    // --- POST /api/projects/{projectId}/jobs ---

    @Test
    @DisplayName("Should create a job and return 201")
    void createJob_shouldReturn201() throws Exception {
        String body = """
                {
                  "title": "Fix login bug",
                  "description": "Users report 500 on login",
                  "client": "Acme Corp"
                }
                """;

        mockMvc.perform(post("/api/projects/" + projectId + "/jobs")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Fix login bug"))
                .andExpect(jsonPath("$.client").value("Acme Corp"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.id").exists());

        assertThat(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).hasSize(1);
    }

    @Test
    @DisplayName("Should return 400 when job title is missing")
    void createJob_shouldReturn400_whenTitleMissing() throws Exception {
        String body = """
                { "description": "No title" }
                """;

        mockMvc.perform(post("/api/projects/" + projectId + "/jobs")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    @DisplayName("Should return 403 when requester is not a project member")
    void createJob_shouldReturn403_whenNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();
        String body = """
                { "title": "Job" }
                """;

        mockMvc.perform(post("/api/projects/" + projectId + "/jobs")
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when project does not exist")
    void createJob_shouldReturn404_whenProjectNotFound() throws Exception {
        String body = """
                { "title": "Job" }
                """;

        mockMvc.perform(post("/api/projects/" + UUID.randomUUID() + "/jobs")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/projects/{projectId}/jobs ---

    @Test
    @DisplayName("OWNER should see all jobs in the project")
    void listJobs_shouldReturnAllJobs_forOwner() throws Exception {
        createTestJob("Job 1", null, JobStatus.NEW);
        createTestJob("Job 2", memberId, JobStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/projects/" + projectId + "/jobs")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("MEMBER should see only jobs assigned to them")
    void listJobs_shouldReturnOnlyAssignedJobs_forMember() throws Exception {
        createTestJob("My Job", memberId, JobStatus.NEW);
        createTestJob("Others Job", null, JobStatus.NEW);

        mockMvc.perform(get("/api/projects/" + projectId + "/jobs")
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("My Job"));
    }

    // --- GET /api/projects/{projectId}/jobs/{jobId} ---

    @Test
    @DisplayName("OWNER should be able to get any job by ID")
    void getJob_shouldReturn200_forOwner() throws Exception {
        JobModel job = createTestJob("Fix bug", null, JobStatus.NEW);

        mockMvc.perform(get("/api/projects/" + projectId + "/jobs/" + job.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fix bug"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @DisplayName("Assigned MEMBER should be able to get their own job")
    void getJob_shouldReturn200_forAssignedMember() throws Exception {
        JobModel job = createTestJob("My Job", memberId, JobStatus.NEW);

        mockMvc.perform(get("/api/projects/" + projectId + "/jobs/" + job.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My Job"));
    }

    @Test
    @DisplayName("MEMBER should be forbidden from getting a job not assigned to them")
    void getJob_shouldReturn403_whenMemberNotAssigned() throws Exception {
        JobModel job = createTestJob("Others Job", null, JobStatus.NEW);

        mockMvc.perform(get("/api/projects/" + projectId + "/jobs/" + job.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/projects/{projectId}/jobs/{jobId} ---

    @Test
    @DisplayName("OWNER should be able to update job fields")
    void updateJob_shouldReturn200_forOwner() throws Exception {
        JobModel job = createTestJob("Old title", null, JobStatus.NEW);

        String body = """
                {
                  "title": "New title",
                  "description": "Updated"
                }
                """;

        mockMvc.perform(put("/api/projects/" + projectId + "/jobs/" + job.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.description").value("Updated"));
    }

    @Test
    @DisplayName("MEMBER should be forbidden from updating job fields")
    void updateJob_shouldReturn403_forMember() throws Exception {
        JobModel job = createTestJob("Job", memberId, JobStatus.NEW);

        String body = """
                { "title": "Hacked" }
                """;

        mockMvc.perform(put("/api/projects/" + projectId + "/jobs/" + job.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // --- PATCH /api/projects/{projectId}/jobs/{jobId}/status ---

    @Test
    @DisplayName("Should transition NEW → IN_PROGRESS for assigned MEMBER")
    void updateStatus_shouldReturn200_forAssignedMember() throws Exception {
        JobModel job = createTestJob("My Job", memberId, JobStatus.NEW);

        mockMvc.perform(patch("/api/projects/" + projectId + "/jobs/" + job.getId() + "/status")
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Should return 400 for invalid status transition NEW → COMPLETED")
    void updateStatus_shouldReturn400_forInvalidTransition() throws Exception {
        JobModel job = createTestJob("Job", null, JobStatus.NEW);

        mockMvc.perform(patch("/api/projects/" + projectId + "/jobs/" + job.getId() + "/status")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    // --- DELETE /api/projects/{projectId}/jobs/{jobId} ---

    @Test
    @DisplayName("OWNER should be able to soft delete a job and return 204")
    void deleteJob_shouldReturn204_forOwner() throws Exception {
        JobModel job = createTestJob("To Delete", null, JobStatus.NEW);

        mockMvc.perform(delete("/api/projects/" + projectId + "/jobs/" + job.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).isEmpty();
        assertThat(jobRepository.findById(job.getId())).isPresent();
    }

    @Test
    @DisplayName("MEMBER should be forbidden from deleting a job")
    void deleteJob_shouldReturn403_forMember() throws Exception {
        JobModel job = createTestJob("Job", memberId, JobStatus.NEW);

        mockMvc.perform(delete("/api/projects/" + projectId + "/jobs/" + job.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when deleting a non-existent job")
    void deleteJob_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/projects/" + projectId + "/jobs/" + UUID.randomUUID())
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private JobModel createTestJob(String title, UUID assignedTo, JobStatus status) {
        return jobRepository.save(JobModel.builder()
                .projectId(projectId)
                .title(title)
                .assignedTo(assignedTo)
                .status(status)
                .createdBy(ownerId)
                .build());
    }
}
