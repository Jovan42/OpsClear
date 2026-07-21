package com.opsclear.integration;

import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectLinkModel;
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
class ProjectLinkIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectLinkRepository projectLinkRepository;
    @Autowired private JobLinkRepository jobLinkRepository;
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
    private UUID projectId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        projectLinkRepository.deleteAll();
        jobLinkRepository.deleteAll();
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

        ownerId  = UUID.randomUUID();
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

        UUID jobLinksAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("JOB_LINKS")).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(jobLinksAddonId));
    }

    // --- POST /api/projects/{projectId}/links ---

    @Test
    @DisplayName("Member should create a project link and receive 201")
    void create_shouldReturn201_forMember() throws Exception {
        mockMvc.perform(post(ApiPaths.projectLinks(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://figma.com/file/abc", "label": "Design"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://figma.com/file/abc"))
                .andExpect(jsonPath("$.label").value("Design"));

        assertThat(projectLinkRepository.findByProjectId(projectId)).hasSize(1);
    }

    @Test
    @DisplayName("Should return 403 when requester is not a project member")
    void create_shouldReturn403_whenNotMember() throws Exception {
        mockMvc.perform(post(ApiPaths.projectLinks(projectId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/projects/{projectId}/links/{linkId} ---

    @Test
    @DisplayName("Owner should update a project link and receive 200")
    void update_shouldReturn200_forOwner() throws Exception {
        ProjectLinkModel link = createLink("https://old.example.com", "Old");

        mockMvc.perform(put(ApiPaths.projectLink(projectId, link.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://new.example.com", "label": "New"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://new.example.com"));
    }

    @Test
    @DisplayName("Should return 403 when member tries to update a project link")
    void update_shouldReturn403_forMember() throws Exception {
        ProjectLinkModel link = createLink("https://old.example.com", "Old");

        mockMvc.perform(put(ApiPaths.projectLink(projectId, link.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://new.example.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    // --- DELETE /api/projects/{projectId}/links/{linkId} ---

    @Test
    @DisplayName("Owner should delete a project link and receive 204")
    void delete_shouldReturn204_forOwner() throws Exception {
        ProjectLinkModel link = createLink("https://example.com", null);

        mockMvc.perform(delete(ApiPaths.projectLink(projectId, link.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        assertThat(projectLinkRepository.findByProjectId(projectId)).isEmpty();
    }

    @Test
    @DisplayName("Should return 404 when link does not exist")
    void delete_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.projectLink(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- GET project/projects — links embedded ---

    @Test
    @DisplayName("Project detail should include links")
    void getProject_shouldIncludeLinks() throws Exception {
        createLink("https://example.com", "Example");

        mockMvc.perform(get(ApiPaths.project(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links.length()").value(1))
                .andExpect(jsonPath("$.links[0].url").value("https://example.com"));
    }

    @Test
    @DisplayName("Project list should include links")
    void listProjects_shouldIncludeLinks() throws Exception {
        createLink("https://example.com", "Example");

        mockMvc.perform(get(ApiPaths.projectsByStatus("ACTIVE"))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].links.length()").value(1));
    }

    // --- Helpers ---

    private ProjectLinkModel createLink(String url, String label) {
        return projectLinkRepository.save(ProjectLinkModel.builder()
                .projectId(projectId).url(url).label(label).createdBy(ownerId).build());
    }
}
