package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.OrganisationRepository;
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
@DisplayName("Job status history endpoint")
class JobHistoryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;
    private UUID jobId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
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

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        JobModel job = jobRepository.save(JobModel.builder()
                .projectId(projectId)
                .title("Test Job")
                .status(JobStatus.NEW)
                .createdBy(ownerId)
                .build());
        jobId = job.getId();
    }

    @Test
    @DisplayName("getHistory_shouldReturnFirstEntry_whenJobIsCreated")
    void getHistory_shouldReturnFirstEntry_whenJobIsCreated() throws Exception {
        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "New Job"))))
                .andExpect(status().isCreated());

        UUID newJobId = jobRepository.findByFilters(projectId, null, "New Job", null, null)
                .get(0).getId();

        mockMvc.perform(get(ApiPaths.jobHistory(projectId, newJobId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].changedFrom").doesNotExist())
                .andExpect(jsonPath("$[0].changedTo").value("NEW"))
                .andExpect(jsonPath("$[0].changedByName").value("Owner"))
                .andExpect(jsonPath("$[0].blockReason").doesNotExist());
    }

    @Test
    @DisplayName("getHistory_shouldRecordEachTransition_whenStatusChanges")
    void getHistory_shouldRecordEachTransition_whenStatusChanges() throws Exception {
        // NEW → IN_PROGRESS
        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk());

        // IN_PROGRESS → BLOCKED
        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "BLOCKED", "reason", "Waiting for client"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(ApiPaths.jobHistory(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].changedFrom").value("NEW"))
                .andExpect(jsonPath("$[0].changedTo").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].blockReason").doesNotExist())
                .andExpect(jsonPath("$[1].changedFrom").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].changedTo").value("BLOCKED"))
                .andExpect(jsonPath("$[1].blockReason").value("Waiting for client"));
    }

    @Test
    @DisplayName("getHistory_shouldReturn403_whenCallerIsNotMember")
    void getHistory_shouldReturn403_whenCallerIsNotMember() throws Exception {
        UUID outsider = UUID.randomUUID();
        mockMvc.perform(get(ApiPaths.jobHistory(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(outsider.toString()).claim("email", "outsider@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getHistory_shouldReturn403_whenMemberAccessesUnassignedJob")
    void getHistory_shouldReturn403_whenMemberAccessesUnassignedJob() throws Exception {
        mockMvc.perform(get(ApiPaths.jobHistory(projectId, jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getHistory_shouldReturn200_whenMemberAccessesAssignedJob")
    void getHistory_shouldReturn200_whenMemberAccessesAssignedJob() throws Exception {
        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "Assigned Job", "assignedTo", memberId.toString()))))
                .andExpect(status().isCreated());

        UUID assignedJobId = jobRepository.findByFilters(projectId, memberId, null, null, null)
                .get(0).getId();

        mockMvc.perform(get(ApiPaths.jobHistory(projectId, assignedJobId))
                        .with(jwt().jwt(jwt -> jwt.subject(memberId.toString())
                                .claim("email", "member@example.com").claim("name", "Member"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("getHistory_shouldReturn404_whenProjectDoesNotExist")
    void getHistory_shouldReturn404_whenProjectDoesNotExist() throws Exception {
        mockMvc.perform(get(ApiPaths.jobHistory(UUID.randomUUID(), jobId))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getHistory_shouldReturn404_whenJobDoesNotExist")
    void getHistory_shouldReturn404_whenJobDoesNotExist() throws Exception {
        mockMvc.perform(get(ApiPaths.jobHistory(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getHistory_shouldReturn404_whenJobBelongsToDifferentProject")
    void getHistory_shouldReturn404_whenJobBelongsToDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        JobModel otherJob = jobRepository.save(JobModel.builder()
                .projectId(otherProject.getId()).title("Other Job")
                .status(JobStatus.NEW).createdBy(ownerId).build());

        mockMvc.perform(get(ApiPaths.jobHistory(projectId, otherJob.getId()))
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()).claim("email", "owner@example.com").claim("name", "Owner"))))
                .andExpect(status().isNotFound());
    }
}
