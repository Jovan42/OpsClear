package com.opsclear.integration;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.MilestoneModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.FriendlyIdRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Friendly ID routing — resolve friendly IDs in path parameters")
class FriendlyIdRoutingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID projectId;
    private String projectFriendlyId;
    private UUID jobId;
    private String jobFriendlyId;
    private UUID milestoneId;
    private String milestoneFriendlyId;

    @BeforeEach
    void setUp() {
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Acme").slug("ACM").createdBy(ownerId).build());
        UUID orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId)
                        .friendlyId("PRJ-001").build());
        projectId = project.getId();
        projectFriendlyId = "PRJ-001";
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());

        JobModel job = jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Test Job").status(JobStatus.NEW)
                .createdBy(ownerId).friendlyId("JOB-001").build());
        jobId = job.getId();
        jobFriendlyId = "JOB-001";

        MilestoneModel milestone = milestoneRepository.save(MilestoneModel.builder()
                .projectId(projectId).name("Sprint 1").friendlyId("MIL-001").build());
        milestoneId = milestone.getId();
        milestoneFriendlyId = "MIL-001";
    }

    // --- project resolution ---

    @Test
    @DisplayName("GET project by friendly ID resolves to correct project")
    void getProject_byFriendlyId_shouldReturn200() throws Exception {
        mockMvc.perform(get(ApiPaths.project(projectFriendlyId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.friendlyId").value(projectFriendlyId));
    }

    @Test
    @DisplayName("GET project by UUID still works (backward compat)")
    void getProject_byUuid_shouldReturn200() throws Exception {
        mockMvc.perform(get(ApiPaths.project(projectId.toString()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()));
    }

    @Test
    @DisplayName("GET project by friendly ID is case-insensitive")
    void getProject_byFriendlyIdLowercase_shouldReturn200() throws Exception {
        mockMvc.perform(get(ApiPaths.project("prj-001"))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()));
    }

    @Test
    @DisplayName("GET project with unknown friendly ID returns 404")
    void getProject_byUnknownFriendlyId_shouldReturn404() throws Exception {
        mockMvc.perform(get(ApiPaths.project("PRJ-999"))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- job resolution ---

    @Test
    @DisplayName("GET job by friendly IDs for both project and job resolves correctly")
    void getJob_byFriendlyIds_shouldReturn200() throws Exception {
        mockMvc.perform(get(ApiPaths.job(projectFriendlyId, jobFriendlyId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.friendlyId").value(jobFriendlyId));
    }

    @Test
    @DisplayName("GET job by UUID paths still works (backward compat)")
    void getJob_byUuids_shouldReturn200() throws Exception {
        mockMvc.perform(get(ApiPaths.job(projectId.toString(), jobId.toString()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()));
    }

    @Test
    @DisplayName("GET job by friendly ID is case-insensitive")
    void getJob_byFriendlyIdLowercase_shouldReturn200() throws Exception {
        mockMvc.perform(get(ApiPaths.job("prj-001", "job-001"))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()));
    }

    @Test
    @DisplayName("GET job with unknown friendly ID returns 404")
    void getJob_byUnknownFriendlyId_shouldReturn404() throws Exception {
        mockMvc.perform(get(ApiPaths.job(projectFriendlyId, "JOB-999"))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- milestone resolution ---

    @Test
    @DisplayName("List milestones via project friendly ID resolves correctly")
    void listMilestones_byProjectFriendlyId_shouldReturn200() throws Exception {
        mockMvc.perform(get(ApiPaths.milestones(projectFriendlyId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].friendlyId").value(milestoneFriendlyId));
    }

    @Test
    @DisplayName("PUT milestone by friendly IDs resolves correctly")
    void updateMilestone_byFriendlyIds_shouldReturn200() throws Exception {
        mockMvc.perform(put(ApiPaths.milestone(projectFriendlyId, milestoneFriendlyId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sprint 1 Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(milestoneId.toString()))
                .andExpect(jsonPath("$.name").value("Sprint 1 Updated"));
    }

    @Test
    @DisplayName("GET job by friendly ID — user with no org membership returns 403")
    void getJob_byFriendlyId_noOrg_shouldReturn403() throws Exception {
        UUID noOrgUser = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(noOrgUser).email("noorgjob@example.com").name("No Org Job").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(noOrgUser).role(ProjectMemberRole.OWNER).build());

        // UUID projectId bypasses org check, friendly jobId triggers requireOrgId → 403
        mockMvc.perform(get(ApiPaths.job(projectId.toString(), jobFriendlyId))
                        .with(jwt().jwt(j -> j.subject(noOrgUser.toString()).claim("email", "noorgjob@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET milestone by friendly ID — user with no org membership returns 403")
    void getMilestone_byFriendlyId_noOrg_shouldReturn403() throws Exception {
        UUID noOrgUser = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(noOrgUser).email("noorgmil@example.com").name("No Org Mil").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(noOrgUser).role(ProjectMemberRole.OWNER).build());

        // UUID projectId bypasses org check, friendly milestoneId triggers requireOrgId → 403
        mockMvc.perform(put(ApiPaths.milestone(projectId.toString(), milestoneFriendlyId))
                        .with(jwt().jwt(j -> j.subject(noOrgUser.toString()).claim("email", "noorgmil@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Updated"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Friendly ID from a different org returns 404")
    void getProject_friendlyIdFromAnotherOrg_shouldReturn404() throws Exception {
        UUID stranger = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(stranger).email("stranger@example.com").name("Stranger").build());
        OrganisationModel otherOrg = organisationRepository.save(
                OrganisationModel.builder().name("Other Corp").slug("OTH").createdBy(stranger).build());
        organisationRepository.saveMember(otherOrg.getId(), stranger, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(otherOrg.getId());

        // PRJ-001 exists in ownerId's org, but stranger belongs to otherOrg — cannot see it
        mockMvc.perform(get(ApiPaths.project("PRJ-001"))
                        .with(jwt().jwt(j -> j.subject(stranger.toString()).claim("email", "stranger@example.com"))))
                .andExpect(status().isNotFound());
    }
}
