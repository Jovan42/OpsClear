package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApprovalIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID unassignedMemberId;
    private UUID projectId;
    private UUID jobId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
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
        unassignedMemberId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());
        userRepository.save(UserModel.builder()
                .id(unassignedMemberId).email("other@example.com").name("Other").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, unassignedMemberId, OrganisationRole.OWNER);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(unassignedMemberId).role(ProjectMemberRole.MEMBER).build());

        JobModel job = jobRepository.save(JobModel.builder()
                .projectId(projectId)
                .title("Install solar panel")
                .status(JobStatus.IN_PROGRESS)
                .assignedTo(memberId)
                .createdBy(ownerId)
                .build());
        jobId = job.getId();
    }

    // --- POST /api/projects/{projectId}/jobs/{jobId}/approvals ---

    @Test
    @DisplayName("request should return 201 when owner requests approval on any job")
    void request_shouldReturn201_whenOwnerRequests() throws Exception {
        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Need a decision on this."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.jobTitle").value("Install solar panel"))
                .andExpect(jsonPath("$.requesterId").value(ownerId.toString()))
                .andExpect(jsonPath("$.description").value("Need a decision on this."))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedAt").exists())
                .andExpect(jsonPath("$.approverId").doesNotExist())
                .andExpect(jsonPath("$.decidedAt").doesNotExist());
    }

    @Test
    @DisplayName("request should return 201 when assigned member requests approval on their job")
    void request_shouldReturn201_whenAssignedMemberRequests() throws Exception {
        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Client is pushing for delivery."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requesterId").value(memberId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("request should return 403 when unassigned member requests approval on a job they don't own")
    void request_shouldReturn403_whenUnassignedMemberRequests() throws Exception {
        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(unassignedMemberId.toString())
                                .claim("email", "other@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Need approval."))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("request should return 400 when description is blank")
    void request_shouldReturn400_whenDescriptionIsBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("request should return 400 when description exceeds 2000 characters")
    void request_shouldReturn400_whenDescriptionTooLong() throws Exception {
        String tooLong = "x".repeat(2001);
        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", tooLong))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("request should return 403 when caller is not a project member")
    void request_shouldReturn403_whenNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();
        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Need approval."))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("request should return 404 when job does not exist")
    void request_shouldReturn404_whenJobNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.approvals(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Need approval."))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("request should return 404 when job belongs to a different project")
    void request_shouldReturn404_whenJobInDifferentProject() throws Exception {
        UUID otherOwnerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(otherOwnerId).email("otherowner@example.com").name("Other Owner").build());
        organisationRepository.saveMember(orgId, otherOwnerId, OrganisationRole.OWNER);
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(otherOwnerId).organisationId(orgId).build());
        JobModel jobInOtherProject = jobRepository.save(JobModel.builder()
                .projectId(otherProject.getId())
                .title("Other job")
                .status(JobStatus.NEW)
                .createdBy(otherOwnerId)
                .build());

        mockMvc.perform(post(ApiPaths.approvals(projectId, jobInOtherProject.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Need approval."))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("request should return 404 when project does not exist")
    void request_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.approvals(UUID.randomUUID(), jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Need approval."))))
                .andExpect(status().isNotFound());
    }

    // --- PATCH /api/projects/{projectId}/jobs/{jobId}/approvals/{approvalId} ---

    @Test
    @DisplayName("decide should return 200 with updated approval when owner approves")
    void decide_shouldReturn200_whenOwnerApproves() throws Exception {
        UUID approvalId = approvalRepository.insert(jobId, memberId, "Need a go-ahead.").getId();

        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED", "comment", "Looks good."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approverId").value(ownerId.toString()))
                .andExpect(jsonPath("$.comment").value("Looks good."))
                .andExpect(jsonPath("$.decidedAt").exists());
    }

    @Test
    @DisplayName("decide should return 200 with updated approval when owner rejects")
    void decide_shouldReturn200_whenOwnerRejects() throws Exception {
        UUID approvalId = approvalRepository.insert(jobId, memberId, "Need a go-ahead.").getId();

        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "REJECTED", "comment", "Not ready."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.approverId").value(ownerId.toString()));
    }

    @Test
    @DisplayName("decide should return 409 when approval has already been decided")
    void decide_shouldReturn409_whenAlreadyDecided() throws Exception {
        UUID approvalId = approvalRepository.insert(jobId, memberId, "Need a decision.").getId();
        approvalRepository.updateDecision(approvalId, ownerId, com.opsclear.model.ApprovalStatus.APPROVED, "OK", Instant.now());

        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "REJECTED"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("decide should return 400 when status is PENDING")
    void decide_shouldReturn400_whenStatusIsPending() throws Exception {
        UUID approvalId = approvalRepository.insert(jobId, memberId, "Need a decision.").getId();

        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PENDING"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("decide should return 403 when a member tries to decide")
    void decide_shouldReturn403_whenMemberDecides() throws Exception {
        UUID approvalId = approvalRepository.insert(jobId, memberId, "Need a go-ahead.").getId();

        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("decide should return 404 when approval does not exist")
    void decide_shouldReturn404_whenApprovalNotFound() throws Exception {
        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/projects/{projectId}/jobs/{jobId}/approvals ---

    @Test
    @DisplayName("listByJob should return 200 with approvals ordered newest first")
    void listByJob_shouldReturn200_withApprovals() throws Exception {
        approvalRepository.insert(jobId, memberId, "First request");
        approvalRepository.insert(jobId, ownerId, "Second request");

        mockMvc.perform(get(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].description").value("Second request"))
                .andExpect(jsonPath("$[1].description").value("First request"));
    }

    @Test
    @DisplayName("listByJob should return empty array when job has no approvals")
    void listByJob_shouldReturnEmptyArray_whenNone() throws Exception {
        mockMvc.perform(get(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("listByJob should return 403 when caller is not a project member")
    void listByJob_shouldReturn403_whenNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();
        mockMvc.perform(get(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listByJob should return 404 when job does not exist")
    void listByJob_shouldReturn404_whenJobNotFound() throws Exception {
        mockMvc.perform(get(ApiPaths.approvals(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/projects/{projectId}/approvals/pending ---

    @Test
    @DisplayName("listPendingByProject should return 200 with only PENDING approvals for the project")
    void listPending_shouldReturn200_withPendingApprovals() throws Exception {
        UUID pendingId = approvalRepository.insert(jobId, memberId, "Needs a decision.").getId();
        UUID decidedId = approvalRepository.insert(jobId, ownerId, "Already handled.").getId();
        approvalRepository.updateDecision(
                decidedId, ownerId, com.opsclear.model.ApprovalStatus.APPROVED, null, Instant.now());

        mockMvc.perform(get(ApiPaths.pendingApprovals(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(pendingId.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].jobTitle").value("Install solar panel"));
    }

    @Test
    @DisplayName("listPendingByProject should return empty array when no pending approvals exist")
    void listPending_shouldReturnEmptyArray_whenNone() throws Exception {
        mockMvc.perform(get(ApiPaths.pendingApprovals(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("listPendingByProject should return 403 when a member requests the pending queue")
    void listPending_shouldReturn403_whenMemberRequests() throws Exception {
        mockMvc.perform(get(ApiPaths.pendingApprovals(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listPendingByProject should return 404 when project does not exist")
    void listPending_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(get(ApiPaths.pendingApprovals(UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- completed project guard ---

    @Test
    @DisplayName("request_shouldReturn409_whenProjectIsCompleted")
    void request_shouldReturn409_whenProjectIsCompleted() throws Exception {
        completeProject();

        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Need a decision."))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("decide_shouldReturn409_whenProjectIsCompleted")
    void decide_shouldReturn409_whenProjectIsCompleted() throws Exception {
        UUID approvalId = approvalRepository.insert(jobId, memberId, "Need a decision.").getId();
        completeProject();

        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    private void completeProject() {
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(project);
    }
}
