package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Friendly ID generation")
class FriendlyIdIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUp() throws Exception {
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(userId).email("user@example.com").name("Test User").build());

        // Create org via API so OrganisationService.create() seeds org_settings + org_sequences
        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Test Corp", "slug": "TST" }
                                """))
                .andExpect(status().isCreated());

        // Create a project to use in job / milestone tests
        MvcResult projectResult = mockMvc.perform(post(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Alpha" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        projectId = UUID.fromString(objectMapper
                .readTree(projectResult.getResponse().getContentAsString())
                .get("id").asText());
    }

    // -------------------------------------------------------------------------
    // Project friendly IDs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("First project gets PRJ-001")
    void createProject_shouldAssignFriendlyId_PRJ001_toFirstProject() throws Exception {
        // The project created in setUp() is the first one — verify its friendlyId
        MvcResult result = mockMvc.perform(post(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Second Project" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.friendlyId").value("PRJ-002"))
                .andReturn();

        String friendlyId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("friendlyId").asText();
        assertThat(friendlyId).isEqualTo("PRJ-002");
    }

    @Test
    @DisplayName("Sequential projects get incrementing friendly IDs")
    void createProjects_shouldAssignSequentialFriendlyIds() throws Exception {
        // setUp already created PRJ-001; create two more
        MvcResult r2 = mockMvc.perform(post(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Project B" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult r3 = mockMvc.perform(post(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Project C" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String id2 = objectMapper.readTree(r2.getResponse().getContentAsString()).get("friendlyId").asText();
        String id3 = objectMapper.readTree(r3.getResponse().getContentAsString()).get("friendlyId").asText();

        assertThat(id2).isEqualTo("PRJ-002");
        assertThat(id3).isEqualTo("PRJ-003");
    }

    // -------------------------------------------------------------------------
    // Job friendly IDs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("First job gets JOB-001")
    void createJob_shouldAssignFriendlyId_JOB001_toFirstJob() throws Exception {
        mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "First Task" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.friendlyId").value("JOB-001"));
    }

    @Test
    @DisplayName("Sequential jobs get incrementing friendly IDs")
    void createJobs_shouldAssignSequentialFriendlyIds() throws Exception {
        MvcResult r1 = mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Job A" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult r2 = mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Job B" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String id1 = objectMapper.readTree(r1.getResponse().getContentAsString()).get("friendlyId").asText();
        String id2 = objectMapper.readTree(r2.getResponse().getContentAsString()).get("friendlyId").asText();

        assertThat(id1).isEqualTo("JOB-001");
        assertThat(id2).isEqualTo("JOB-002");
    }

    @Test
    @DisplayName("Job and project sequences are independent (JOB-001 coexists with PRJ-001)")
    void jobAndProjectSequences_shouldBeIndependent() throws Exception {
        MvcResult jobResult = mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Task" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String jobFriendlyId = objectMapper.readTree(jobResult.getResponse().getContentAsString())
                .get("friendlyId").asText();

        // Project created in setUp should be PRJ-001; job should be JOB-001 regardless
        assertThat(jobFriendlyId).isEqualTo("JOB-001");
    }

    // -------------------------------------------------------------------------
    // Milestone friendly IDs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("First milestone gets MIL-001")
    void createMilestone_shouldAssignFriendlyId_MIL001_toFirstMilestone() throws Exception {
        mockMvc.perform(post(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "v1.0" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.friendlyId").value("MIL-001"));
    }

    @Test
    @DisplayName("Sequential milestones get incrementing friendly IDs")
    void createMilestones_shouldAssignSequentialFriendlyIds() throws Exception {
        MvcResult r1 = mockMvc.perform(post(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Sprint 1" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult r2 = mockMvc.perform(post(ApiPaths.milestones(projectId))
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString())
                                .claim("email", "user@example.com").claim("name", "Test User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Sprint 2" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String id1 = objectMapper.readTree(r1.getResponse().getContentAsString()).get("friendlyId").asText();
        String id2 = objectMapper.readTree(r2.getResponse().getContentAsString()).get("friendlyId").asText();

        assertThat(id1).isEqualTo("MIL-001");
        assertThat(id2).isEqualTo("MIL-002");
    }

    // -------------------------------------------------------------------------
    // Org creation seeds sequences
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("New org creation seeds sequences — next entity starts at 001")
    void createOrg_shouldSeedSequences_soFirstEntityGetsFriendlyId001() throws Exception {
        // A fresh org created by a second user should start its own counters at 001
        UUID user2 = UUID.randomUUID();
        userRepository.save(UserModel.builder()
                .id(user2).email("user2@example.com").name("User Two").build());

        mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(jwt -> jwt.subject(user2.toString())
                                .claim("email", "user2@example.com").claim("name", "User Two")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Second Corp", "slug": "SEC" }
                                """))
                .andExpect(status().isCreated());

        MvcResult projectResult = mockMvc.perform(post(ApiPaths.PROJECTS)
                        .with(jwt().jwt(jwt -> jwt.subject(user2.toString())
                                .claim("email", "user2@example.com").claim("name", "User Two")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "First Project" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.friendlyId").value("PRJ-001"))
                .andReturn();

        String friendlyId = objectMapper.readTree(projectResult.getResponse().getContentAsString())
                .get("friendlyId").asText();
        assertThat(friendlyId).isEqualTo("PRJ-001");
    }
}
