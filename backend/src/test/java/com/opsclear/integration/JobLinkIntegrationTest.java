package com.opsclear.integration;

import com.opsclear.model.JobLinkModel;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobLinkRepository;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.ProjectLinkRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
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

import java.util.Set;
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
class JobLinkIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JobLinkRepository jobLinkRepository;
    @Autowired private ProjectLinkRepository projectLinkRepository;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRelationshipRepository jobRelationshipRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID outsiderId;
    private UUID projectId;
    private UUID orgId;
    private JobModel job;

    @BeforeEach
    void setUp() {
        jobLinkRepository.deleteAll();
        projectLinkRepository.deleteAll();
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobRelationshipRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        outsiderId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());
        userRepository.save(UserModel.builder().id(outsiderId).email("outsider@example.com").name("Outsider").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, outsiderId, OrganisationRole.OWNER);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        job = jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Deploy backend").status(JobStatus.NEW).createdBy(ownerId).build());

        UUID jobLinksAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("JOB_LINKS")).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(jobLinksAddonId));
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, "ACTIVE", null, null);
    }

    // --- POST /api/projects/{projectId}/jobs/{jobId}/links ---

    @Test
    @DisplayName("Member should create a job link and receive 201")
    void create_shouldReturn201_forMember() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://github.com/org/repo/pull/1", "label": "PR #1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.url").value("https://github.com/org/repo/pull/1"))
                .andExpect(jsonPath("$.label").value("PR #1"))
                .andExpect(jsonPath("$.createdBy").value(memberId.toString()));

        assertThat(jobLinkRepository.findByJobId(job.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Should return 400 when URL is blank")
    void create_shouldReturn400_whenUrlBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when URL scheme is javascript")
    void create_shouldReturn400_whenSchemeDisallowed() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "javascript:alert(1)"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 403 when requester is not a project member")
    void create_shouldReturn403_whenNotMember() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when requester belongs to the org but not the project")
    void create_shouldReturn403_whenOrgMemberNotProjectMember() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a member of this project"));
    }

    @Test
    @DisplayName("Should return 404 when job does not exist")
    void create_shouldReturn404_whenJobNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when project does not exist")
    void create_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(UUID.randomUUID(), job.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when job belongs to a different project")
    void create_shouldReturn404_whenJobBelongsToDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        JobModel otherJob = jobRepository.save(JobModel.builder()
                .projectId(otherProject.getId()).title("Other job").status(JobStatus.NEW).createdBy(ownerId).build());

        mockMvc.perform(post(ApiPaths.jobLinks(projectId, otherJob.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 400 when URL has no scheme")
    void create_shouldReturn400_whenSchemeMissing() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "not-a-url"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when URL is malformed")
    void create_shouldReturn400_whenUrlMalformed() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "http://exa mple.com"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should create a job link with a null label")
    void create_shouldReturn201_withNullLabel() throws Exception {
        mockMvc.perform(post(ApiPaths.jobLinks(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").doesNotExist());
    }

    // --- PUT /api/projects/{projectId}/jobs/{jobId}/links/{linkId} ---

    @Test
    @DisplayName("Owner should update a job link and receive 200")
    void update_shouldReturn200_forOwner() throws Exception {
        JobLinkModel link = createLink("https://old.example.com", "Old");

        mockMvc.perform(put(ApiPaths.jobLink(projectId, job.getId(), link.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://new.example.com", "label": "New"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://new.example.com"))
                .andExpect(jsonPath("$.label").value("New"));
    }

    @Test
    @DisplayName("Should return 403 when member tries to update a job link")
    void update_shouldReturn403_forMember() throws Exception {
        JobLinkModel link = createLink("https://old.example.com", "Old");

        mockMvc.perform(put(ApiPaths.jobLink(projectId, job.getId(), link.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://new.example.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when requester belongs to the org but not the project on update")
    void update_shouldReturn403_whenOrgMemberNotProjectMember() throws Exception {
        JobLinkModel link = createLink("https://old.example.com", "Old");

        mockMvc.perform(put(ApiPaths.jobLink(projectId, job.getId(), link.getId()))
                        .with(jwt().jwt(j -> j.subject(outsiderId.toString()).claim("email", "outsider@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://new.example.com"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a member of this project"));
    }

    @Test
    @DisplayName("Should update a job link with a null label")
    void update_shouldReturn200_withNullLabel() throws Exception {
        JobLinkModel link = createLink("https://old.example.com", "Old");

        mockMvc.perform(put(ApiPaths.jobLink(projectId, job.getId(), link.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://new.example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").doesNotExist());
    }

    @Test
    @DisplayName("Should return 404 when link belongs to a different job")
    void update_shouldReturn404_whenLinkBelongsToDifferentJob() throws Exception {
        JobModel otherJob = jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Other job").status(JobStatus.NEW).createdBy(ownerId).build());
        JobLinkModel link = createLink("https://old.example.com", "Old");

        mockMvc.perform(put(ApiPaths.jobLink(projectId, otherJob.getId(), link.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://new.example.com"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/projects/{projectId}/jobs/{jobId}/links/{linkId} ---

    @Test
    @DisplayName("Owner should delete a job link and receive 204")
    void delete_shouldReturn204_forOwner() throws Exception {
        JobLinkModel link = createLink("https://example.com", null);

        mockMvc.perform(delete(ApiPaths.jobLink(projectId, job.getId(), link.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(jobLinkRepository.findByJobId(job.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should return 403 when member tries to delete a job link")
    void delete_shouldReturn403_forMember() throws Exception {
        JobLinkModel link = createLink("https://example.com", null);

        mockMvc.perform(delete(ApiPaths.jobLink(projectId, job.getId(), link.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when link does not exist")
    void delete_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.jobLink(projectId, job.getId(), UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- GET job/jobs — links embedded ---

    @Test
    @DisplayName("Job detail should include links")
    void getJob_shouldIncludeLinks() throws Exception {
        createLink("https://example.com", "Example");

        mockMvc.perform(get(ApiPaths.job(projectId, job.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links.length()").value(1))
                .andExpect(jsonPath("$.links[0].url").value("https://example.com"));
    }

    @Test
    @DisplayName("Job list should include links")
    void listJobs_shouldIncludeLinks() throws Exception {
        createLink("https://example.com", "Example");

        mockMvc.perform(get(ApiPaths.jobs(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].links.length()").value(1));
    }

    // --- Helpers ---

    private JobLinkModel createLink(String url, String label) {
        return jobLinkRepository.save(JobLinkModel.builder()
                .jobId(job.getId()).url(url).label(label).createdBy(ownerId).build());
    }
}
