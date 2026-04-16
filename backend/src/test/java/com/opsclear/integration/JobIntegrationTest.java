package com.opsclear.integration;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.model.MilestoneModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.stream.Stream;

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
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private com.opsclear.repository.FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        projectMemberRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
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

        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Fix login bug"))
                .andExpect(jsonPath("$.client").value("Acme Corp"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.friendlyId").value("JOB-001"));

        assertThat(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).hasSize(1);
    }

    @Test
    @DisplayName("Should return 400 when job title is missing")
    void createJob_shouldReturn400_whenTitleMissing() throws Exception {
        String body = """
                { "description": "No title" }
                """;

        mockMvc.perform(post(ApiPaths.jobs(projectId))
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
        userRepository.save(UserModel.builder().id(outsider).email("outsider@example.com").name("Outsider").build());
        organisationRepository.saveMember(orgId, outsider, OrganisationRole.MEMBER);

        String body = """
                { "title": "Job" }
                """;

        mockMvc.perform(post(ApiPaths.jobs(projectId))
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

        mockMvc.perform(post(ApiPaths.jobs(UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when assignedTo user does not exist")
    void createJob_shouldReturn404_whenAssignedUserNotFound() throws Exception {
        String body = String.format("""
                {
                  "title": "Job",
                  "assignedTo": "%s"
                }
                """, UUID.randomUUID());

        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should create a job with a valid milestoneId and return 201")
    void createJob_shouldReturn201_whenValidMilestoneId() throws Exception {
        MilestoneModel milestone = milestoneRepository.save(
                MilestoneModel.builder().projectId(projectId).name("Sprint 1").build());

        String body = String.format("""
                {
                  "title": "Milestone task",
                  "milestoneId": "%s"
                }
                """, milestone.getId());

        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.milestoneId").value(milestone.getId().toString()));
    }

    @Test
    @DisplayName("Should return 404 when milestoneId does not exist on create")
    void createJob_shouldReturn404_whenMilestoneNotFound() throws Exception {
        String body = String.format("""
                {
                  "title": "Task",
                  "milestoneId": "%s"
                }
                """, UUID.randomUUID());

        mockMvc.perform(post(ApiPaths.jobs(projectId))
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

        mockMvc.perform(get(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("MEMBER should see only jobs assigned to them")
    void listJobs_shouldReturnOnlyAssignedJobs_forMember() throws Exception {
        createTestJob("My Job", memberId, JobStatus.NEW);
        createTestJob("Others Job", null, JobStatus.NEW);

        mockMvc.perform(get(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("My Job"));
    }

    @Test
    @DisplayName("Should return 403 when requester is not a project member")
    void listJobs_shouldReturn403_whenNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(outsider).email("outsider@example.com").name("Outsider").build());
        organisationRepository.saveMember(orgId, outsider, OrganisationRole.MEMBER);

        mockMvc.perform(get(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when project does not exist")
    void listJobs_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(get(ApiPaths.jobs(UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/projects/{projectId}/jobs/{jobId} ---

    @Test
    @DisplayName("OWNER should be able to get any job by ID")
    void getJob_shouldReturn200_forOwner() throws Exception {
        JobModel job = createTestJob("Fix bug", null, JobStatus.NEW);

        mockMvc.perform(get(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fix bug"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @DisplayName("Assigned MEMBER should be able to get their own job")
    void getJob_shouldReturn200_forAssignedMember() throws Exception {
        JobModel job = createTestJob("My Job", memberId, JobStatus.NEW);

        mockMvc.perform(get(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My Job"));
    }

    @Test
    @DisplayName("MEMBER should be forbidden from getting a job not assigned to them")
    void getJob_shouldReturn403_whenMemberNotAssigned() throws Exception {
        JobModel job = createTestJob("Others Job", null, JobStatus.NEW);

        mockMvc.perform(get(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when job does not exist")
    void getJob_shouldReturn404_whenJobNotFound() throws Exception {
        mockMvc.perform(get(ApiPaths.job(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when job belongs to a different project")
    void getJob_shouldReturn404_whenJobInDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        JobModel jobInOtherProject = jobRepository.save(JobModel.builder()
                .projectId(otherProject.getId()).title("Other Job")
                .status(JobStatus.NEW).createdBy(ownerId).build());

        mockMvc.perform(get(ApiPaths.job(projectId, jobInOtherProject.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
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

        mockMvc.perform(put(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.description").value("Updated"));
    }

    @Test
    @DisplayName("Should update job with a valid assignedTo user")
    void updateJob_shouldReturn200_withAssignedUser() throws Exception {
        JobModel job = createTestJob("Old title", null, JobStatus.NEW);

        String body = String.format("""
                {
                  "title": "New title",
                  "assignedTo": "%s"
                }
                """, memberId);

        mockMvc.perform(put(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value(memberId.toString()));
    }

    @Test
    @DisplayName("MEMBER should be forbidden from updating job fields")
    void updateJob_shouldReturn403_forMember() throws Exception {
        JobModel job = createTestJob("Job", memberId, JobStatus.NEW);

        String body = """
                { "title": "Hacked" }
                """;

        mockMvc.perform(put(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when job does not exist on update")
    void updateJob_shouldReturn404_whenJobNotFound() throws Exception {
        String body = """
                { "title": "New title" }
                """;

        mockMvc.perform(put(ApiPaths.job(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when job belongs to a different project on update")
    void updateJob_shouldReturn404_whenJobInDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        JobModel jobInOtherProject = jobRepository.save(JobModel.builder()
                .projectId(otherProject.getId()).title("Other Job")
                .status(JobStatus.NEW).createdBy(ownerId).build());

        String body = """
                { "title": "Hijack" }
                """;

        mockMvc.perform(put(ApiPaths.job(projectId, jobInOtherProject.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when assignedTo user does not exist on update")
    void updateJob_shouldReturn404_whenAssignedUserNotFound() throws Exception {
        JobModel job = createTestJob("Job", null, JobStatus.NEW);

        String body = String.format("""
                {
                  "title": "Updated",
                  "assignedTo": "%s"
                }
                """, UUID.randomUUID());

        mockMvc.perform(put(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("update_shouldChangePriority_whenPriorityProvided")
    void updateJob_shouldChangePriority_whenPriorityProvided() throws Exception {
        JobModel job = createTestJobWithPriority("Job", null, JobPriority.LOW);

        mockMvc.perform(put(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Job","priority":"CRITICAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"));
    }

    @Test
    @DisplayName("update_shouldKeepExistingPriority_whenPriorityOmitted")
    void updateJob_shouldKeepExistingPriority_whenPriorityOmitted() throws Exception {
        JobModel job = createTestJobWithPriority("Job", null, JobPriority.HIGH);

        mockMvc.perform(put(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Job"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    // --- PATCH /api/projects/{projectId}/jobs/{jobId}/status ---

    @Test
    @DisplayName("Should transition NEW → IN_PROGRESS for assigned MEMBER")
    void updateStatus_shouldReturn200_forAssignedMember() throws Exception {
        JobModel job = createTestJob("My Job", memberId, JobStatus.NEW);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
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

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("OWNER should block an IN_PROGRESS job with a reason")
    void updateStatus_shouldReturn200_withBlockedStatus_forOwner() throws Exception {
        JobModel job = createTestJob("Job", null, JobStatus.IN_PROGRESS);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "BLOCKED", "reason": "Waiting for client sign-off" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.blockedBy").value(ownerId.toString()))
                .andExpect(jsonPath("$.blockedReason").value("Waiting for client sign-off"))
                .andExpect(jsonPath("$.blockedAt").exists());
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidStatusBodiesFromInProgress")
    @DisplayName("Should return 400 for invalid status update from IN_PROGRESS")
    void updateStatus_shouldReturn400_fromInProgress(String body, String displayName) throws Exception {
        JobModel job = createTestJob("Job", null, JobStatus.IN_PROGRESS);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    static Stream<Arguments> invalidStatusBodiesFromInProgress() {
        return Stream.of(
                Arguments.of("{ \"status\": \"BLOCKED\" }", "blocking without a reason"),
                Arguments.of("{ \"status\": \"BLOCKED\", \"reason\": \"   \" }", "blocking with a blank reason"),
                Arguments.of("{ \"status\": \"NEW\" }", "invalid transition IN_PROGRESS → NEW")
        );
    }

    @Test
    @DisplayName("Should return 400 for invalid transition BLOCKED → COMPLETED")
    void updateStatus_shouldReturn400_invalidTransition_blockedToCompleted() throws Exception {
        JobModel job = createTestJob("Job", null, JobStatus.BLOCKED);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("Assigned MEMBER should be able to block their own job")
    void updateStatus_shouldReturn200_blockedByAssignedMember() throws Exception {
        JobModel job = createTestJob("Job", memberId, JobStatus.IN_PROGRESS);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "BLOCKED", "reason": "Missing access credentials" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.blockedBy").value(memberId.toString()))
                .andExpect(jsonPath("$.blockedReason").value("Missing access credentials"));
    }

    @Test
    @DisplayName("OWNER should unblock a BLOCKED job and clear blocking metadata")
    void updateStatus_shouldReturn200_unblockJob_forOwner() throws Exception {
        // First block it
        JobModel job = createTestJob("Job", null, JobStatus.IN_PROGRESS);
        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "BLOCKED", "reason": "Waiting for approval" }
                                """))
                .andExpect(status().isOk());

        // Then unblock it
        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.blockedBy").doesNotExist())
                .andExpect(jsonPath("$.blockedReason").doesNotExist())
                .andExpect(jsonPath("$.blockedAt").doesNotExist());
    }

    @Test
    @DisplayName("OWNER should transition IN_PROGRESS → COMPLETED")
    void updateStatus_shouldReturn200_inProgressToCompleted_forOwner() throws Exception {
        JobModel job = createTestJob("Job", null, JobStatus.IN_PROGRESS);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("OWNER should transition COMPLETED → IN_PROGRESS (reopen)")
    void updateStatus_shouldReturn200_completedToInProgress_forOwner() throws Exception {
        JobModel job = createTestJob("Job", null, JobStatus.COMPLETED);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Should return 400 for invalid status transition COMPLETED → NEW")
    void updateStatus_shouldReturn400_invalidTransition_fromCompleted() throws Exception {
        JobModel job = createTestJob("Job", null, JobStatus.COMPLETED);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "NEW" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("ADMIN should transition NEW → IN_PROGRESS")
    void updateStatus_shouldReturn200_forAdmin() throws Exception {
        UUID adminId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(adminId).email("admin@example.com").name("Admin").build());
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.MEMBER);
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(adminId).role(ProjectMemberRole.ADMIN).build());

        JobModel job = createTestJob("Job", null, JobStatus.NEW);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(adminId.toString()).claim("email", "admin@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("MEMBER should be forbidden from reopening a completed job")
    void updateStatus_shouldReturn403_whenMemberReopensCompletedJob() throws Exception {
        JobModel job = createTestJob("Job", memberId, JobStatus.COMPLETED);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEMBER should be forbidden from changing status of a job not assigned to them")
    void updateStatus_shouldReturn403_whenMemberNotAssigned() throws Exception {
        JobModel job = createTestJob("Job", ownerId, JobStatus.NEW);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when job does not exist on status update")
    void updateStatus_shouldReturn404_whenJobNotFound() throws Exception {
        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when job belongs to a different project on status update")
    void updateStatus_shouldReturn404_whenJobInDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        JobModel jobInOtherProject = jobRepository.save(JobModel.builder()
                .projectId(otherProject.getId()).title("Other Job")
                .status(JobStatus.NEW).createdBy(ownerId).build());

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, jobInOtherProject.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/projects/{projectId}/jobs/{jobId} ---

    @Test
    @DisplayName("OWNER should be able to soft delete a job and return 204")
    void deleteJob_shouldReturn204_forOwner() throws Exception {
        JobModel job = createTestJob("To Delete", null, JobStatus.NEW);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().isDeleted()).isFalse();

        mockMvc.perform(delete(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(jobRepository.findByIdAndDeletedAtIsNull(job.getId())).isEmpty();
        assertThat(jobRepository.findById(job.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("MEMBER should be forbidden from deleting a job")
    void deleteJob_shouldReturn403_forMember() throws Exception {
        JobModel job = createTestJob("Job", memberId, JobStatus.NEW);

        mockMvc.perform(delete(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when deleting a non-existent job")
    void deleteJob_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.job(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when job belongs to a different project on delete")
    void deleteJob_shouldReturn404_whenJobInDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        JobModel jobInOtherProject = jobRepository.save(JobModel.builder()
                .projectId(otherProject.getId()).title("Other Job")
                .status(JobStatus.NEW).createdBy(ownerId).build());

        mockMvc.perform(delete(ApiPaths.job(projectId, jobInOtherProject.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/projects/{projectId}/jobs?q= (search) ---

    @Test
    @DisplayName("Empty q param should fall through to normal list — all jobs returned")
    void searchJobs_shouldReturnAllJobs_whenQueryIsEmpty() throws Exception {
        createTestJob("Job Alpha", null, JobStatus.NEW);
        createTestJob("Job Beta", null, JobStatus.IN_PROGRESS);

        mockMvc.perform(get(ApiPaths.jobsSearch(projectId, ""))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Blank q param should fall through to normal list — all jobs returned")
    void searchJobs_shouldReturnAllJobs_whenQueryIsBlank() throws Exception {
        createTestJob("Job Alpha", null, JobStatus.NEW);
        createTestJob("Job Beta", null, JobStatus.IN_PROGRESS);

        mockMvc.perform(get(ApiPaths.jobsSearch(projectId, "   "))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("OWNER search should return jobs matching title")
    void searchJobs_shouldReturnMatchingByTitle_forOwner() throws Exception {
        createTestJob("Fix login bug", null, JobStatus.NEW);
        createTestJob("Deploy to staging", null, JobStatus.IN_PROGRESS);

        mockMvc.perform(get(ApiPaths.jobsSearch(projectId, "login"))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Fix login bug"));
    }

    @Test
    @DisplayName("Search should be case-insensitive")
    void searchJobs_shouldBeCaseInsensitive() throws Exception {
        createTestJob("Fix Login Bug", null, JobStatus.NEW);

        mockMvc.perform(get(ApiPaths.jobsSearch(projectId, "LOGIN"))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("OWNER search should match jobs by client name")
    void searchJobs_shouldReturnMatchingByClient_forOwner() throws Exception {
        jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Quarterly report").client("Acme Corp")
                .status(JobStatus.NEW).createdBy(ownerId).build());
        createTestJob("Internal task", null, JobStatus.NEW);

        mockMvc.perform(get(ApiPaths.jobsSearch(projectId, "acme"))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].client").value("Acme Corp"));
    }

    @Test
    @DisplayName("OWNER search should match jobs by assigned user name")
    void searchJobs_shouldReturnMatchingByAssigneeName_forOwner() throws Exception {
        createTestJob("Assigned task", memberId, JobStatus.NEW);
        createTestJob("Unassigned task", null, JobStatus.NEW);

        mockMvc.perform(get(ApiPaths.jobsSearch(projectId, "Member"))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].assignedToName").value("Member"));
    }

    @Test
    @DisplayName("Search returning no matches should return empty list")
    void searchJobs_shouldReturnEmpty_whenNoMatch() throws Exception {
        createTestJob("Fix login bug", null, JobStatus.NEW);

        mockMvc.perform(get(ApiPaths.jobsSearch(projectId, "xyz123"))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("MEMBER search should only return matching jobs assigned to them")
    void searchJobs_shouldBeScoped_forMember() throws Exception {
        createTestJob("Member login task", memberId, JobStatus.NEW);
        createTestJob("Owner login task", ownerId, JobStatus.NEW);

        mockMvc.perform(get(ApiPaths.jobsSearch(projectId, "login"))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Member login task"));
    }

    // --- GET /api/projects/{projectId}/jobs?priority= (priority filter) ---

    @Test
    @DisplayName("list_shouldReturnOnlyHighPriorityJobs_whenPriorityFilterApplied")
    void list_shouldReturnOnlyHighPriorityJobs_whenPriorityFilterApplied() throws Exception {
        createTestJobWithPriority("High job", null, JobPriority.HIGH);
        createTestJobWithPriority("Low job", null, JobPriority.LOW);

        mockMvc.perform(get(ApiPaths.jobsByPriority(projectId, "HIGH"))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("High job"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"));
    }

    @Test
    @DisplayName("list_shouldReturnEmptyList_whenNoPriorityMatches")
    void list_shouldReturnEmptyList_whenNoPriorityMatches() throws Exception {
        createTestJobWithPriority("Medium job", null, JobPriority.MEDIUM);

        mockMvc.perform(get(ApiPaths.jobsByPriority(projectId, "CRITICAL"))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("list_shouldReturnOnlyMemberAssignedJobs_whenMemberFiltersByPriority")
    void list_shouldReturnOnlyMemberAssignedJobs_whenMemberFiltersByPriority() throws Exception {
        createTestJobWithPriority("Member critical job", memberId, JobPriority.CRITICAL);
        createTestJobWithPriority("Other critical job", ownerId, JobPriority.CRITICAL);

        mockMvc.perform(get(ApiPaths.jobsByPriority(projectId, "CRITICAL"))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Member critical job"));
    }

    @Test
    @DisplayName("create_shouldReturnHighPriority_whenPrioritySpecified")
    void create_shouldReturnHighPriority_whenPrioritySpecified() throws Exception {
        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Urgent task","priority":"HIGH"}
                                """)
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    @DisplayName("create_shouldDefaultToMediumPriority_whenNotSpecified")
    void create_shouldDefaultToMediumPriority_whenNotSpecified() throws Exception {
        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"No priority job"}
                                """)
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    // --- GET /api/projects/{projectId}/jobs?q=&priority= (combined search + priority) ---

    @Test
    @DisplayName("Should return only jobs matching both query and priority")
    void list_shouldReturnJobsMatchingQueryAndPriority() throws Exception {
        createTestJobWithPriority("Critical login bug",   null, JobPriority.CRITICAL);
        createTestJobWithPriority("Critical deploy task", null, JobPriority.CRITICAL);
        createTestJobWithPriority("High login task",      null, JobPriority.HIGH);

        mockMvc.perform(get(ApiPaths.jobsBySearchAndPriority(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Critical login bug"))
                .andExpect(jsonPath("$[0].priority").value("CRITICAL"));
    }

    @Test
    @DisplayName("Should return empty list when query matches but priority does not")
    void list_shouldReturnEmpty_whenQueryMatchesButPriorityDoesNot() throws Exception {
        createTestJobWithPriority("High login task", null, JobPriority.HIGH);

        mockMvc.perform(get(ApiPaths.jobsBySearchAndPriority(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Should return empty list when priority matches but query does not")
    void list_shouldReturnEmpty_whenPriorityMatchesButQueryDoesNot() throws Exception {
        createTestJobWithPriority("Critical deploy task", null, JobPriority.CRITICAL);

        mockMvc.perform(get(ApiPaths.jobsBySearchAndPriority(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("MEMBER combining search and priority should only see their own matching jobs")
    void list_shouldScopeQueryAndPriority_forMember() throws Exception {
        createTestJobWithPriority("Member critical login", memberId, JobPriority.CRITICAL);
        createTestJobWithPriority("Owner critical login",  ownerId,  JobPriority.CRITICAL);

        mockMvc.perform(get(ApiPaths.jobsBySearchAndPriority(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Member critical login"));
    }

    // --- completed project guard ---

    @Test
    @DisplayName("createJob_shouldReturn409_whenProjectIsCompleted")
    void createJob_shouldReturn409_whenProjectIsCompleted() throws Exception {
        completeProject();

        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "New Job" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("updateJobStatus_shouldReturn409_whenProjectIsCompleted")
    void updateJobStatus_shouldReturn409_whenProjectIsCompleted() throws Exception {
        JobModel job = createTestJob("Some Job", memberId, JobStatus.NEW);
        completeProject();

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, job.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("listJobs_shouldReturnOnlyJobsInMilestone_whenMilestoneIdFilter")
    void listJobs_shouldReturnOnlyJobsInMilestone_whenMilestoneIdFilter() throws Exception {
        MilestoneModel milestone = milestoneRepository.save(
                MilestoneModel.builder().projectId(projectId).name("Sprint 1").build());

        JobModel jobInMilestone = jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Scoped task").assignedTo(ownerId)
                .status(JobStatus.NEW).createdBy(ownerId).milestoneId(milestone.getId()).build());
        jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Other task").assignedTo(ownerId)
                .status(JobStatus.NEW).createdBy(ownerId).build());

        mockMvc.perform(get(ApiPaths.jobsByMilestone(projectId, milestone.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(jobInMilestone.getId().toString()));
    }

    // --- helpers ---

    private void completeProject() {
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(project);
    }

    private JobModel createTestJob(String title, UUID assignedTo, JobStatus status) {
        return jobRepository.save(JobModel.builder()
                .projectId(projectId)
                .title(title)
                .assignedTo(assignedTo)
                .status(status)
                .createdBy(ownerId)
                .build());
    }

    private JobModel createTestJobWithPriority(String title, UUID assignedTo, JobPriority priority) {
        return jobRepository.save(JobModel.builder()
                .projectId(projectId)
                .title(title)
                .assignedTo(assignedTo)
                .status(JobStatus.NEW)
                .priority(priority)
                .createdBy(ownerId)
                .build());
    }

    // --- Org enforcement ---

    @Test
    @DisplayName("create_shouldReturn403_whenCallerHasNoOrg")
    void create_shouldReturn403_whenCallerHasNoOrg() throws Exception {
        UUID outsiderId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(outsiderId).email("outsider@example.com").name("Outsider").build());

        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New Job"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("list_shouldReturn403_whenCallerHasNoOrg")
    void list_shouldReturn403_whenCallerHasNoOrg() throws Exception {
        UUID outsiderId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(outsiderId).email("outsider@example.com").name("Outsider").build());

        mockMvc.perform(get(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden());
    }

}
