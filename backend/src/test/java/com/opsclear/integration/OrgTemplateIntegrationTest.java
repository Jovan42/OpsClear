package com.opsclear.integration;

import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
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
@DisplayName("Org Templates API")
class OrgTemplateIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobTemplateRepository jobTemplateRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID projectId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobTemplateRepository.deleteAll();
        jobRepository.deleteAll();
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
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        UUID templateAddonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals("JOB_TEMPLATES")).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(templateAddonId));
    }

    // --- POST /api/organisations/{orgId}/templates ---

    @Test
    @DisplayName("createOrgTemplate — owner receives 201 with scope ORG and no projectId")
    void createOrgTemplate_shouldReturn201_forOwner() throws Exception {
        mockMvc.perform(post(ApiPaths.orgTemplates(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Onboarding Call",
                                  "title": "Onboarding with {{creator}}",
                                  "assigneeMode": "ASK"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Onboarding Call"))
                .andExpect(jsonPath("$.scope").value("ORG"))
                .andExpect(jsonPath("$.orgId").value(orgId.toString()))
                .andExpect(jsonPath("$.projectId").doesNotExist())
                .andExpect(jsonPath("$.friendlyId").value("TPL-001"));
    }

    @Test
    @DisplayName("createOrgTemplate — org member (not admin) receives 403")
    void createOrgTemplate_shouldReturn403_forOrgMember() throws Exception {
        mockMvc.perform(post(ApiPaths.orgTemplates(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T1", "assigneeMode": "NONE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createOrgTemplate — non-member receives 403")
    void createOrgTemplate_shouldReturn403_whenNotOrgMember() throws Exception {
        mockMvc.perform(post(ApiPaths.orgTemplates(orgId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T1", "assigneeMode": "NONE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createOrgTemplate — blank name returns 400")
    void createOrgTemplate_shouldReturn400_whenNameBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.orgTemplates(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("createOrgTemplate — no JOB_TEMPLATES addon returns 403")
    void createOrgTemplate_shouldReturn403_whenAddonNotSubscribed() throws Exception {
        subscriptionRepository.deleteAll();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of());

        mockMvc.perform(post(ApiPaths.orgTemplates(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T1", "assigneeMode": "NONE"}
                                """))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/organisations/{orgId}/templates ---

    @Test
    @DisplayName("listOrgTemplates — org member can list templates")
    void listOrgTemplates_shouldReturn200_forOrgMember() throws Exception {
        jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").orgId(orgId).name("Onboarding").assigneeMode("NONE").createdBy(ownerId).build());
        jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-002").orgId(orgId).name("Bug Report").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(get(ApiPaths.orgTemplates(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].scope").value("ORG"));
    }

    @Test
    @DisplayName("listOrgTemplates — non-member receives 403")
    void listOrgTemplates_shouldReturn403_whenNotOrgMember() throws Exception {
        mockMvc.perform(get(ApiPaths.orgTemplates(orgId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com"))))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/organisations/{orgId}/templates/{templateId} ---

    @Test
    @DisplayName("updateOrgTemplate — owner can update")
    void updateOrgTemplate_shouldReturn200_forOwner() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").orgId(orgId).name("Old Name").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(put(ApiPaths.orgTemplate(orgId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New Name", "assigneeMode": "ASK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.assigneeMode").value("ASK"))
                .andExpect(jsonPath("$.scope").value("ORG"));
    }

    @Test
    @DisplayName("updateOrgTemplate — member receives 403")
    void updateOrgTemplate_shouldReturn403_forOrgMember() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").orgId(orgId).name("T1").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(put(ApiPaths.orgTemplate(orgId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Hack"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateOrgTemplate — unknown template returns 404")
    void updateOrgTemplate_shouldReturn404_whenTemplateNotFound() throws Exception {
        mockMvc.perform(put(ApiPaths.orgTemplate(orgId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updateOrgTemplate — template from another org returns 404")
    void updateOrgTemplate_shouldReturn404_whenTemplateBelongsToDifferentOrg() throws Exception {
        UUID otherOrgId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(otherOrgId).email("other@example.com").name("Other").build());
        OrganisationModel otherOrg = organisationRepository.save(
                OrganisationModel.builder().name("Other Org").slug("OTH").createdBy(otherOrgId).build());
        friendlyIdRepository.seedForOrg(otherOrg.getId());
        JobTemplateModel otherTemplate = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").orgId(otherOrg.getId()).name("Other").assigneeMode("NONE").createdBy(otherOrgId).build());

        mockMvc.perform(put(ApiPaths.orgTemplate(orgId, otherTemplate.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/organisations/{orgId}/templates/{templateId} ---

    @Test
    @DisplayName("deleteOrgTemplate — owner receives 204, template is no longer listed")
    void deleteOrgTemplate_shouldReturn204_forOwner() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").orgId(orgId).name("To Delete").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(delete(ApiPaths.orgTemplate(orgId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiPaths.orgTemplates(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("deleteOrgTemplate — member receives 403")
    void deleteOrgTemplate_shouldReturn403_forOrgMember() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").orgId(orgId).name("Protected").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(delete(ApiPaths.orgTemplate(orgId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deleteOrgTemplate — unknown template returns 404")
    void deleteOrgTemplate_shouldReturn404_whenTemplateNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.orgTemplate(orgId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- Combined listing via project endpoint ---

    @Test
    @DisplayName("listTemplates — org-scoped templates appear in combined project listing with scope ORG")
    void listProjectTemplates_shouldIncludeOrgTemplates_withScopeTag() throws Exception {
        jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("Project Template").assigneeMode("NONE").createdBy(ownerId).build());
        jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-002").orgId(orgId).name("Org Template").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(get(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name == 'Project Template')].scope").value("PROJECT"))
                .andExpect(jsonPath("$[?(@.name == 'Org Template')].scope").value("ORG"));
    }

    @Test
    @DisplayName("recordUsage — org template from a different org returns 404")
    void recordUsage_shouldReturn404_whenOrgTemplateBelongsToDifferentOrg() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(otherUserId).email("other@example.com").name("Other").build());
        OrganisationModel otherOrg = organisationRepository.save(
                OrganisationModel.builder().name("Other Org").slug("OTH").createdBy(otherUserId).build());
        friendlyIdRepository.seedForOrg(otherOrg.getId());

        JobTemplateModel otherOrgTemplate = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").orgId(otherOrg.getId()).name("Other Org Template").assigneeMode("NONE").createdBy(otherUserId).build());

        mockMvc.perform(post(ApiPaths.templateUse(projectId, otherOrgTemplate.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("recordUsage — succeeds for an org-scoped template used in a project context")
    void recordUsage_shouldReturn200_forOrgScopedTemplate() throws Exception {
        JobTemplateModel orgTemplate = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").orgId(orgId).name("Shared").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(post(ApiPaths.templateUse(projectId, orgTemplate.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk());

        JobTemplateModel after = jobTemplateRepository.findByIdAndDeletedAtIsNull(orgTemplate.getId()).orElseThrow();
        assertThat(after.getOccurrenceCount()).isEqualTo(1);
    }
}
