package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.dto.UpdateOrgSettingsRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.MilestoneModel;
import com.opsclear.model.OrgSettingsModel;
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
import com.opsclear.repository.JobTypeRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.OrgSettingsRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.OrgSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Org settings endpoint (ID prefixes, ADR-0060)")
class OrgSettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private RecurringScheduleRepository scheduleRepository;
    @Autowired private JobTemplateRepository jobTemplateRepository;
    @Autowired private JobTypeRepository jobTypeRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSettingsRepository orgSettingsRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrgSettingsService orgSettingsService;

    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID outsiderId;
    private UUID orgId;
    private UUID otherOrgId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        scheduleRepository.deleteAll();
        jobTemplateRepository.deleteAll();
        jobTypeRepository.deleteAll();
        jobRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = saveUser("owner@example.com");
        adminId = saveUser("admin@example.com");
        memberId = saveUser("member@example.com");
        outsiderId = saveUser("outsider@example.com");

        orgId = organisationRepository.save(
                OrganisationModel.builder().name("Acme").slug("ACM").createdBy(ownerId).build()).getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.ADMIN);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        friendlyIdRepository.seedForOrg(orgId);

        otherOrgId = organisationRepository.save(
                OrganisationModel.builder().name("Other").slug("OTH").createdBy(outsiderId).build()).getId();
        organisationRepository.saveMember(otherOrgId, outsiderId, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(otherOrgId);

        projectId = projectRepository.save(ProjectModel.builder().name("Website").ownerId(ownerId)
                .organisationId(orgId).friendlyId("PRJ-001").build()).getId();
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
    }

    private UUID saveUser(String email) {
        UUID id = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(id).email(email).name(email).build());
        return id;
    }

    private JwtRequestPostProcessor as(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("email", userId + "@example.com"));
    }

    private ResultActions patchSettings(UUID userId, UUID org, Map<String, String> body) throws Exception {
        return mockMvc.perform(patch(ApiPaths.orgSettings(org))
                .with(as(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String createJobViaApi(String title) throws Exception {
        String json = mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", title))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("friendlyId").asText();
    }

    private OrgSettingsModel settingsInDb() {
        return orgSettingsRepository.findByOrgId(orgId).orElseThrow();
    }

    // --- GET ---

    @Test
    @DisplayName("GET as a member returns the four prefixes and the next IDs")
    void get_shouldReturnPrefixesAndNextIds_forMember() throws Exception {
        assertThat(createJobViaApi("First")).isEqualTo("JOB-001");

        mockMvc.perform(get(ApiPaths.orgSettings(orgId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectPrefix").value("PRJ"))
                .andExpect(jsonPath("$.jobPrefix").value("JOB"))
                .andExpect(jsonPath("$.milestonePrefix").value("MIL"))
                .andExpect(jsonPath("$.templatePrefix").value("TPL"))
                .andExpect(jsonPath("$.nextProjectId").value("PRJ-001"))
                .andExpect(jsonPath("$.nextJobId").value("JOB-002"))
                .andExpect(jsonPath("$.nextMilestoneId").value("MIL-001"))
                .andExpect(jsonPath("$.nextTemplateId").value("TPL-001"));
    }

    @Test
    @DisplayName("GET by a user of another org gives 403")
    void get_shouldReturn403_forAnotherOrgsUser() throws Exception {
        mockMvc.perform(get(ApiPaths.orgSettings(orgId)).with(as(outsiderId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET for an unknown org gives 404")
    void get_shouldReturn404_forUnknownOrg() throws Exception {
        mockMvc.perform(get(ApiPaths.orgSettings(UUID.randomUUID())).with(as(ownerId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET for an org without a settings row gives 404")
    void get_shouldReturn404_whenSettingsRowMissing() throws Exception {
        UUID bareOrg = organisationRepository.save(
                OrganisationModel.builder().name("Bare").slug("BAR").createdBy(adminId).build()).getId();
        organisationRepository.saveMember(bareOrg, adminId, OrganisationRole.OWNER);

        mockMvc.perform(get(ApiPaths.orgSettings(bareOrg)).with(as(adminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Organisation settings not found"));
    }

    // --- PATCH: happy path ---

    @Test
    @DisplayName("PATCH as Owner changes the job prefix; new jobs use it and the old job still resolves")
    void patch_shouldApplyToNewJobsOnly_andOldIdKeepsResolving() throws Exception {
        assertThat(createJobViaApi("Old job")).isEqualTo("JOB-001");
        OrgSettingsModel before = settingsInDb();

        patchSettings(ownerId, orgId, Map.of("jobPrefix", " tsk "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPrefix").value("TSK"))
                .andExpect(jsonPath("$.projectPrefix").value("PRJ"))
                .andExpect(jsonPath("$.nextJobId").value("TSK-002"));

        OrgSettingsModel saved = settingsInDb();
        assertThat(saved.getJobPrefix()).isEqualTo("TSK");
        assertThat(saved.getProjectPrefix()).isEqualTo("PRJ");
        assertThat(saved.getMilestonePrefix()).isEqualTo("MIL");
        assertThat(saved.getTemplatePrefix()).isEqualTo("TPL");
        assertThat(saved.getUpdatedAt()).isNotEqualTo(before.getUpdatedAt());

        assertThat(createJobViaApi("New job")).isEqualTo("TSK-002");

        mockMvc.perform(get(ApiPaths.job("PRJ-001", "JOB-001")).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Old job"))
                .andExpect(jsonPath("$.friendlyId").value("JOB-001"));
        mockMvc.perform(get(ApiPaths.job("PRJ-001", "TSK-002")).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New job"));
    }

    @Test
    @DisplayName("PATCH back to a prefix the same type used before is allowed (TSK -> JOB)")
    void patch_shouldAllowGoingBackToSameTypesOldPrefix() throws Exception {
        createJobViaApi("Old job");
        patchSettings(ownerId, orgId, Map.of("jobPrefix", "TSK")).andExpect(status().isOk());
        createJobViaApi("Task job");

        patchSettings(ownerId, orgId, Map.of("jobPrefix", "JOB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPrefix").value("JOB"))
                .andExpect(jsonPath("$.nextJobId").value("JOB-003"));

        assertThat(settingsInDb().getJobPrefix()).isEqualTo("JOB");
    }

    @Test
    @DisplayName("PATCH with the current values is a no-op and returns the settings")
    void patch_shouldBeNoOp_forUnchangedValues() throws Exception {
        OrgSettingsModel before = settingsInDb();

        patchSettings(ownerId, orgId, Map.of("jobPrefix", "job", "projectPrefix", "PRJ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPrefix").value("JOB"))
                .andExpect(jsonPath("$.nextJobId").value("JOB-001"));

        assertThat(settingsInDb().getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }

    @Test
    @DisplayName("PATCH allows a prefix equal to the org slug and ignores another org's records")
    void patch_shouldAllowSlug_andIgnoreOtherOrgsRecords() throws Exception {
        UUID otherProject = projectRepository.save(ProjectModel.builder().name("Theirs").ownerId(outsiderId)
                .organisationId(otherOrgId).friendlyId("XYZ-001").build()).getId();
        jobRepository.save(JobModel.builder().projectId(otherProject).title("Theirs").status(JobStatus.NEW)
                .createdBy(outsiderId).friendlyId("ACM-001").build());

        patchSettings(ownerId, orgId, Map.of("projectPrefix", "ACM", "jobPrefix", "XYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectPrefix").value("ACM"))
                .andExpect(jsonPath("$.jobPrefix").value("XYZ"));
    }

    // --- PATCH: permissions ---

    @Test
    @DisplayName("PATCH as Admin gives 403 and leaves the settings unchanged")
    void patch_shouldReturn403_forAdmin() throws Exception {
        patchSettings(adminId, orgId, Map.of("jobPrefix", "TSK"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient permissions: OWNER role required"));

        assertThat(settingsInDb().getJobPrefix()).isEqualTo("JOB");
    }

    @Test
    @DisplayName("PATCH as Member gives 403 and leaves the settings unchanged")
    void patch_shouldReturn403_forMember() throws Exception {
        patchSettings(memberId, orgId, Map.of("jobPrefix", "TSK"))
                .andExpect(status().isForbidden());

        assertThat(settingsInDb().getJobPrefix()).isEqualTo("JOB");
    }

    @Test
    @DisplayName("PATCH by another org's Owner gives 403")
    void patch_shouldReturn403_forAnotherOrgsOwner() throws Exception {
        patchSettings(outsiderId, orgId, Map.of("jobPrefix", "TSK"))
                .andExpect(status().isForbidden());

        assertThat(settingsInDb().getJobPrefix()).isEqualTo("JOB");
    }

    @Test
    @DisplayName("PATCH for an unknown org gives 404")
    void patch_shouldReturn404_forUnknownOrg() throws Exception {
        patchSettings(ownerId, UUID.randomUUID(), Map.of("jobPrefix", "TSK"))
                .andExpect(status().isNotFound());
    }

    // --- PATCH: validation ---

    @Test
    @DisplayName("PATCH with a bad format gives 400")
    void patch_shouldReturn400_forBadFormat() throws Exception {
        patchSettings(ownerId, orgId, Map.of("jobPrefix", "J1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("jobPrefix: Prefix must be 2–3 letters A–Z"));
        patchSettings(ownerId, orgId, Map.of("templatePrefix", "TASK"))
                .andExpect(status().isBadRequest());
        patchSettings(ownerId, orgId, Map.of("milestonePrefix", "M"))
                .andExpect(status().isBadRequest());

        assertThat(settingsInDb().getJobPrefix()).isEqualTo("JOB");
        assertThat(settingsInDb().getTemplatePrefix()).isEqualTo("TPL");
    }

    @Test
    @DisplayName("PATCH with duplicate prefixes gives 400")
    void patch_shouldReturn400_forDuplicates() throws Exception {
        patchSettings(ownerId, orgId, Map.of("jobPrefix", "TSK", "milestonePrefix", "tsk"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prefixes must be different from each other"));
        patchSettings(ownerId, orgId, Map.of("jobPrefix", "PRJ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prefixes must be different from each other"));

        assertThat(settingsInDb().getJobPrefix()).isEqualTo("JOB");
        assertThat(settingsInDb().getMilestonePrefix()).isEqualTo("MIL");
    }

    @Test
    @DisplayName("PATCH to a prefix existing jobs carry, for another type, gives 400")
    void patch_shouldReturn400_whenPrefixUsedByExistingJobs() throws Exception {
        createJobViaApi("Old job");
        patchSettings(ownerId, orgId, Map.of("jobPrefix", "TSK")).andExpect(status().isOk());

        patchSettings(ownerId, orgId, Map.of("milestonePrefix", "JOB"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prefix \"JOB\" is already used by existing jobs"));

        assertThat(settingsInDb().getMilestonePrefix()).isEqualTo("MIL");
    }

    @Test
    @DisplayName("PATCH to a prefix existing milestones carry, for another type, gives 400")
    void patch_shouldReturn400_whenPrefixUsedByExistingMilestones() throws Exception {
        milestoneRepository.save(MilestoneModel.builder()
                .projectId(projectId).name("Sprint 1").friendlyId("MIL-001").build());
        patchSettings(ownerId, orgId, Map.of("milestonePrefix", "PHS")).andExpect(status().isOk());

        patchSettings(ownerId, orgId, Map.of("jobPrefix", "MIL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prefix \"MIL\" is already used by existing milestones"));

        assertThat(settingsInDb().getJobPrefix()).isEqualTo("JOB");
    }

    @Test
    @DisplayName("PATCH to a prefix existing projects carry, for another type, gives 400")
    void patch_shouldReturn400_whenPrefixUsedByExistingProjects() throws Exception {
        patchSettings(ownerId, orgId, Map.of("projectPrefix", "PRO")).andExpect(status().isOk());

        patchSettings(ownerId, orgId, Map.of("templatePrefix", "PRJ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prefix \"PRJ\" is already used by existing projects"));

        assertThat(settingsInDb().getTemplatePrefix()).isEqualTo("TPL");
    }

    @Test
    @DisplayName("PATCH to a prefix existing org-level and project templates carry gives 400")
    void patch_shouldReturn400_whenPrefixUsedByExistingTemplates() throws Exception {
        jobTemplateRepository.save(JobTemplateModel.builder().friendlyId("TPL-001").orgId(orgId)
                .name("Org template").title("Org").assigneeMode("NONE").createdBy(ownerId).build());
        jobTemplateRepository.save(JobTemplateModel.builder().friendlyId("TMP-001").projectId(projectId)
                .name("Project template").title("Proj").assigneeMode("NONE").createdBy(ownerId).build());
        patchSettings(ownerId, orgId, Map.of("templatePrefix", "TPX")).andExpect(status().isOk());

        patchSettings(ownerId, orgId, Map.of("projectPrefix", "TPL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prefix \"TPL\" is already used by existing templates"));
        patchSettings(ownerId, orgId, Map.of("milestonePrefix", "TMP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prefix \"TMP\" is already used by existing templates"));

        assertThat(settingsInDb().getProjectPrefix()).isEqualTo("PRJ");
        assertThat(settingsInDb().getMilestonePrefix()).isEqualTo("MIL");
    }

    @Test
    @DisplayName("PATCH ignores soft-deleted records in the history check")
    void patch_shouldIgnoreSoftDeletedRecords() throws Exception {
        JobModel job = jobRepository.save(JobModel.builder().projectId(projectId).title("Gone")
                .status(JobStatus.NEW).createdBy(ownerId).friendlyId("OLD-001").build());
        job.softDelete();
        jobRepository.save(job);

        patchSettings(ownerId, orgId, Map.of("milestonePrefix", "OLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestonePrefix").value("OLD"));

        assertThat(settingsInDb().getMilestonePrefix()).isEqualTo("OLD");
    }

    @Test
    @DisplayName("The service rejects a bad prefix on its own, without the request DTO's @Pattern")
    void service_shouldRejectBadPrefix_withoutDtoValidation() {
        UpdateOrgSettingsRequest request = UpdateOrgSettingsRequest.builder().jobPrefix("J1").build();

        assertThatThrownBy(() -> orgSettingsService.update(orgId, request, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Prefix must be 2–3 letters A–Z: \"J1\"");

        assertThat(settingsInDb().getJobPrefix()).isEqualTo("JOB");
    }
}
