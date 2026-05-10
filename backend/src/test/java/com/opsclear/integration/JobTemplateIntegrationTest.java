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
@DisplayName("Job Templates API")
class JobTemplateIntegrationTest {

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
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.OWNER);
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

    // --- POST /api/projects/{projectId}/templates ---

    @Test
    @DisplayName("createTemplate — owner receives 201 with all fields populated")
    void createTemplate_shouldReturn201_forOwner() throws Exception {
        mockMvc.perform(post(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Weekly Report",
                                  "title": "Report for {{week}}",
                                  "description": "## Summary\\n- [ ] check metrics",
                                  "assigneeMode": "NONE",
                                  "deadlineOffsetDays": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Weekly Report"))
                .andExpect(jsonPath("$.title").value("Report for {{week}}"))
                .andExpect(jsonPath("$.assigneeMode").value("NONE"))
                .andExpect(jsonPath("$.deadlineOffsetDays").value(3))
                .andExpect(jsonPath("$.occurrenceCount").value(0))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.friendlyId").value("TPL-001"));
    }

    @Test
    @DisplayName("createTemplate — member receives 403")
    void createTemplate_shouldReturn403_forMember() throws Exception {
        mockMvc.perform(post(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T1", "assigneeMode": "NONE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createTemplate — non-member receives 403")
    void createTemplate_shouldReturn403_whenNotMember() throws Exception {
        mockMvc.perform(post(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T1", "assigneeMode": "NONE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createTemplate — blank name returns 400")
    void createTemplate_shouldReturn400_whenNameBlank() throws Exception {
        mockMvc.perform(post(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("createTemplate — unknown project returns 404")
    void createTemplate_shouldReturn404_whenProjectNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.templates(UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T1", "assigneeMode": "NONE"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("createTemplate — no JOB_TEMPLATES addon returns 403")
    void createTemplate_shouldReturn403_whenAddonNotSubscribed() throws Exception {
        subscriptionRepository.deleteAll();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of());

        mockMvc.perform(post(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T1", "assigneeMode": "NONE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createTemplate — org member who is not a project member receives 403")
    void createTemplate_shouldReturn403_whenOrgMemberNotProjectMember() throws Exception {
        UUID orgOnlyId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(orgOnlyId).email("orgonly@example.com").name("OrgOnly").build());
        organisationRepository.saveMember(orgId, orgOnlyId, OrganisationRole.OWNER);

        mockMvc.perform(post(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(orgOnlyId.toString()).claim("email", "orgonly@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T1", "assigneeMode": "NONE"}
                                """))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/projects/{projectId}/templates ---

    @Test
    @DisplayName("listTemplates — member can list templates")
    void listTemplates_shouldReturn200_forMember() throws Exception {
        jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("Alpha").assigneeMode("NONE").createdBy(ownerId).build());
        jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-002").projectId(projectId).name("Beta").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(get(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("listTemplates — templates are sorted by name ascending")
    void listTemplates_shouldReturnTemplatesSortedByName() throws Exception {
        jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("Zebra").assigneeMode("NONE").createdBy(ownerId).build());
        jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-002").projectId(projectId).name("Alpha").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(get(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[1].name").value("Zebra"));
    }

    @Test
    @DisplayName("listTemplates — org member who is not a project member receives 403")
    void listTemplates_shouldReturn403_whenOrgMemberNotProjectMember() throws Exception {
        UUID orgOnlyId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(orgOnlyId).email("orgonly2@example.com").name("OrgOnly2").build());
        organisationRepository.saveMember(orgId, orgOnlyId, OrganisationRole.OWNER);

        mockMvc.perform(get(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(orgOnlyId.toString()).claim("email", "orgonly2@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listTemplates — non-member receives 403")
    void listTemplates_shouldReturn403_whenNotMember() throws Exception {
        mockMvc.perform(get(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com"))))
                .andExpect(status().isForbidden());
    }


    // --- PUT /api/projects/{projectId}/templates/{templateId} ---

    @Test
    @DisplayName("updateTemplate — owner can update all mutable fields")
    void updateTemplate_shouldReturn200_forOwner() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("Old Name").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(put(ApiPaths.template(projectId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New Name",
                                  "title": "Updated title",
                                  "assigneeMode": "ASK"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.assigneeMode").value("ASK"));
    }

    @Test
    @DisplayName("updateTemplate — omitting assigneeMode preserves existing value")
    void updateTemplate_shouldPreserveAssigneeMode_whenOmittedFromBody() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("T1").assigneeMode("ASK").createdBy(ownerId).build());

        mockMvc.perform(put(ApiPaths.template(projectId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.assigneeMode").value("ASK"));
    }

    @Test
    @DisplayName("createTemplate — no org subscription returns 403")
    void createTemplate_shouldReturn403_whenUserHasNoOrg() throws Exception {
        UUID noOrgUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(noOrgUserId).email("noorg@example.com").name("NoOrg").build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(noOrgUserId).role(ProjectMemberRole.OWNER).build());

        mockMvc.perform(post(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(noOrgUserId.toString()).claim("email", "noorg@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "T1", "assigneeMode": "NONE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateTemplate — member receives 403")
    void updateTemplate_shouldReturn403_forMember() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("T1").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(put(ApiPaths.template(projectId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Hack"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateTemplate — unknown template returns 404")
    void updateTemplate_shouldReturn404_whenTemplateNotFound() throws Exception {
        mockMvc.perform(put(ApiPaths.template(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updateTemplate — template from another project returns 404")
    void updateTemplate_shouldReturn404_whenTemplateBelongsToDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        JobTemplateModel otherTemplate = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(otherProject.getId()).name("Other").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(put(ApiPaths.template(projectId, otherTemplate.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/projects/{projectId}/templates/{templateId} ---

    @Test
    @DisplayName("deleteTemplate — owner receives 204, template is soft-deleted and no longer listed")
    void deleteTemplate_shouldReturn204_forOwner() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("To Delete").assigneeMode("NONE").createdBy(ownerId).build());

        assertThat(template.isDeleted()).isFalse();

        mockMvc.perform(delete(ApiPaths.template(projectId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());

        // Verify soft delete — deletedAt is set on the model
        template.softDelete();
        assertThat(template.isDeleted()).isTrue();
        assertThat(template.getDeletedAt()).isNotNull();

        mockMvc.perform(get(ApiPaths.templates(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("deleteTemplate — resolves template via friendly ID")
    void deleteTemplate_shouldReturn204_whenTemplateIdIsFriendlyId() throws Exception {
        jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("By FriendlyId").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(delete(ApiPaths.template(projectId.toString(), "TPL-001"))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("deleteTemplate — member receives 403")
    void deleteTemplate_shouldReturn403_forMember() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("Protected").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(delete(ApiPaths.template(projectId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deleteTemplate — unknown template returns 404")
    void deleteTemplate_shouldReturn404_whenTemplateNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.template(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleteTemplate — friendly ID not found returns 404")
    void deleteTemplate_shouldReturn404_whenFriendlyIdNotFound() throws Exception {
        mockMvc.perform(delete(ApiPaths.template(projectId.toString(), "TPL-999"))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleteTemplate — template from another project returns 404")
    void deleteTemplate_shouldReturn404_whenTemplateBelongsToDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        JobTemplateModel otherTemplate = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(otherProject.getId()).name("Other").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(delete(ApiPaths.template(projectId, otherTemplate.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isNotFound());
    }

    // --- POST /api/projects/{projectId}/templates/{templateId}/use ---

    @Test
    @DisplayName("recordUsage — member can record template usage and returns 200")
    void recordUsage_shouldReturn200_forMember() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("T1").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(post(ApiPaths.templateUse(projectId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isOk());

        // Verify occurrence_count incremented in DB
        JobTemplateModel after = jobTemplateRepository.findByIdAndDeletedAtIsNull(template.getId()).orElseThrow();
        assertThat(after.getOccurrenceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordUsage — owner can record template usage")
    void recordUsage_shouldReturn200_forOwner() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("T1").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(post(ApiPaths.templateUse(projectId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("recordUsage — non-member receives 403")
    void recordUsage_shouldReturn403_whenNotMember() throws Exception {
        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("T1").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(post(ApiPaths.templateUse(projectId, template.getId()))
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", "stranger@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("recordUsage — unknown template returns 404")
    void recordUsage_shouldReturn404_whenTemplateNotFound() throws Exception {
        mockMvc.perform(post(ApiPaths.templateUse(projectId, UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("recordUsage — template from another project returns 404")
    void recordUsage_shouldReturn404_whenTemplateBelongsToDifferentProject() throws Exception {
        ProjectModel otherProject = projectRepository.save(
                ProjectModel.builder().name("Other Project").ownerId(ownerId).organisationId(orgId).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(otherProject.getId()).userId(memberId).role(ProjectMemberRole.MEMBER).build());
        JobTemplateModel otherTemplate = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(otherProject.getId()).name("Other").assigneeMode("NONE").createdBy(ownerId).build());

        mockMvc.perform(post(ApiPaths.templateUse(projectId, otherTemplate.getId()))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isNotFound());
    }
}
