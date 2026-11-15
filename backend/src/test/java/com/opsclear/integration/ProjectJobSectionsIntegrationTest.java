package com.opsclear.integration;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobSection;
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
import com.opsclear.repository.JobLinkRepository;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectLinkRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Project settings: hidden job-detail sections (ADR-0053, JOB-300)")
class ProjectJobSectionsIntegrationTest {

    private static final String HIDE_NOTES_LINKS = """
            { "hiddenJobSections": ["LINKS", "NOTES"] }
            """;
    private static final String CLEAR_ALL = """
            { "hiddenJobSections": [] }
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private DSLContext dsl;
    @Autowired private JobLinkRepository jobLinkRepository;
    @Autowired private ProjectLinkRepository projectLinkRepository;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRelationshipRepository jobRelationshipRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID orgId;
    private UUID projectId;
    private UUID otherProjectId;

    @AfterEach
    void cleanUp() {
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
    }

    @BeforeEach
    void setUp() {
        cleanUp();

        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(adminId).email("admin@example.com").name("Admin").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);

        projectId = projectRepository.save(ProjectModel.builder().friendlyId("PRJ-007").name("Quiet Project")
                .ownerId(ownerId).organisationId(orgId).build()).getId();
        otherProjectId = projectRepository.save(ProjectModel.builder().friendlyId("PRJ-008").name("Busy Project")
                .ownerId(ownerId).organisationId(orgId).build()).getId();
        for (UUID pid : List.of(projectId, otherProjectId)) {
            addMember(pid, ownerId, ProjectMemberRole.OWNER);
            addMember(pid, adminId, ProjectMemberRole.ADMIN);
            addMember(pid, memberId, ProjectMemberRole.MEMBER);
        }
    }

    // --- Helpers ---

    private void addMember(UUID pid, UUID userId, ProjectMemberRole role) {
        projectMemberRepository.save(ProjectMemberModel.builder().projectId(pid).userId(userId).role(role).build());
    }

    private RequestPostProcessor as(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("email", userId + "@example.com"));
    }

    private MockHttpServletRequestBuilder patchSettings(String path, UUID callerId, String body) {
        return patch(path).with(as(callerId)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void givenSubscriptionWithAddons(String... addonKeys) {
        Set<String> keys = Set.of(addonKeys);
        Set<UUID> addonIds = addonRepository.findAll().stream()
                .filter(a -> keys.contains(a.getKey())).map(a -> a.getId()).collect(Collectors.toSet());
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", addonIds);
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, "ACTIVE", null, null);
    }

    private void givenHidden(UUID pid, JobSection... sections) {
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(pid).orElseThrow();
        project.setHiddenJobSections(Arrays.asList(sections));
        projectRepository.save(project);
    }

    /** Raw column value, read without the repository mapping (which de-duplicates). */
    private List<String> hiddenInDb(UUID pid) {
        String[] raw = dsl.select(PROJECTS.HIDDEN_JOB_SECTIONS).from(PROJECTS).where(PROJECTS.ID.eq(pid))
                .fetchSingle().value1();
        return List.of(raw);
    }

    // --- PATCH /api/projects/{id}/settings ---

    @Test
    @DisplayName("Owner hides sections: 200, response and DB hold them in a fixed order")
    void updateSettings_shouldHideSections_whenOwner() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, HIDE_NOTES_LINKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.hiddenJobSections", contains("NOTES", "LINKS")))
                .andExpect(jsonPath("$.requireCompletionApproval").value(false));

        assertThat(hiddenInDb(projectId)).containsExactly("NOTES", "LINKS");
    }

    @Test
    @DisplayName("Admin hides sections via the friendly ID: 200")
    void updateSettings_shouldHideSections_whenAdmin() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings("PRJ-007"), adminId, """
                        { "hiddenJobSections": ["STATUS_HISTORY", "RELATIONSHIPS"] }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", contains("RELATIONSHIPS", "STATUS_HISTORY")));

        assertThat(hiddenInDb(projectId)).containsExactly("RELATIONSHIPS", "STATUS_HISTORY");
    }

    @Test
    @DisplayName("Owner clears all hidden sections with an empty list")
    void updateSettings_shouldClearSections_whenOwnerSendsEmptyList() throws Exception {
        givenHidden(projectId, JobSection.NOTES, JobSection.APPROVALS);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, CLEAR_ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", empty()));

        assertThat(hiddenInDb(projectId)).isEmpty();
    }

    @Test
    @DisplayName("Admin clears all hidden sections with an empty list")
    void updateSettings_shouldClearSections_whenAdminSendsEmptyList() throws Exception {
        givenHidden(projectId, JobSection.LINKS);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), adminId, CLEAR_ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", empty()));

        assertThat(hiddenInDb(projectId)).isEmpty();
    }

    @Test
    @DisplayName("Member gets 403 and the hidden sections are unchanged")
    void updateSettings_shouldReturn403_whenMember() throws Exception {
        givenHidden(projectId, JobSection.LINKS);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), memberId, HIDE_NOTES_LINKS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN));

        assertThat(hiddenInDb(projectId)).containsExactly("LINKS");
    }

    @Test
    @DisplayName("An unknown section name gets 400 and nothing is stored")
    void updateSettings_shouldReturn400_whenSectionUnknown() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, """
                        { "hiddenJobSections": ["NOTES", "MILESTONES"] }
                        """))
                .andExpect(status().isBadRequest());

        assertThat(hiddenInDb(projectId)).isEmpty();
    }

    @Test
    @DisplayName("A null entry gets 400 with the unknown-section message and nothing is stored")
    void updateSettings_shouldReturn400_whenSectionNull() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, """
                        { "hiddenJobSections": ["NOTES", null] }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Project.UNKNOWN_JOB_SECTION));

        assertThat(hiddenInDb(projectId)).isEmpty();
    }

    @Test
    @DisplayName("Duplicate sections are stored once")
    void updateSettings_shouldStoreDuplicatesOnce() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, """
                        { "hiddenJobSections": ["NOTES", "LINKS", "NOTES", "NOTES", "LINKS"] }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", contains("NOTES", "LINKS")));

        assertThat(hiddenInDb(projectId)).containsExactly("NOTES", "LINKS");
    }

    @Test
    @DisplayName("Duplicates already in the column (the check constraint allows them) are returned once")
    void getProject_shouldDeduplicate_whenColumnHoldsDuplicates() throws Exception {
        dsl.update(PROJECTS).set(PROJECTS.HIDDEN_JOB_SECTIONS, new String[] {"LINKS", "NOTES", "LINKS"})
                .where(PROJECTS.ID.eq(projectId)).execute();

        mockMvc.perform(get(ApiPaths.project(projectId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", contains("NOTES", "LINKS")));
    }

    @Test
    @DisplayName("Re-sending the same sections in another order is a no-op: 200 and the row isn't rewritten")
    void updateSettings_shouldNotRewrite_whenSectionsUnchanged() throws Exception {
        givenHidden(projectId, JobSection.NOTES, JobSection.LINKS);
        var updatedBefore = dsl.select(PROJECTS.UPDATED_AT).from(PROJECTS).where(PROJECTS.ID.eq(projectId))
                .fetchSingle().value1();

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, HIDE_NOTES_LINKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", contains("NOTES", "LINKS")));

        assertThat(hiddenInDb(projectId)).containsExactly("NOTES", "LINKS");
        assertThat(dsl.select(PROJECTS.UPDATED_AT).from(PROJECTS).where(PROJECTS.ID.eq(projectId))
                .fetchSingle().value1()).isEqualTo(updatedBefore);
    }

    @Test
    @DisplayName("Absent hiddenJobSections leaves the stored sections as they are")
    void updateSettings_shouldLeaveSections_whenFieldAbsent() throws Exception {
        givenHidden(projectId, JobSection.APPROVALS);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, """
                        { "requireCompletionApproval": false }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", contains("APPROVALS")));

        assertThat(hiddenInDb(projectId)).containsExactly("APPROVALS");
    }

    @Test
    @DisplayName("Hiding sections leaves the completion gate unchanged")
    void updateSettings_shouldLeaveGate_whenOnlySectionsSent() throws Exception {
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setRequireCompletionApproval(true);
        projectRepository.save(project);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, """
                        { "hiddenJobSections": ["APPROVALS"] }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(true))
                .andExpect(jsonPath("$.hiddenJobSections", contains("APPROVALS")));

        ProjectModel stored = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        assertThat(stored.isRequireCompletionApproval()).isTrue();
        assertThat(stored.getHiddenJobSections()).containsExactly(JobSection.APPROVALS);
    }

    @Test
    @DisplayName("Gate and sections are both updated in one request")
    void updateSettings_shouldUpdateGateAndSections_together() throws Exception {
        givenSubscriptionWithAddons("APPROVALS");

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, """
                        { "requireCompletionApproval": true, "hiddenJobSections": ["NOTES"] }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(true))
                .andExpect(jsonPath("$.hiddenJobSections", contains("NOTES")));

        ProjectModel stored = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        assertThat(stored.isRequireCompletionApproval()).isTrue();
        assertThat(stored.getHiddenJobSections()).containsExactly(JobSection.NOTES);
    }

    @Test
    @DisplayName("Hiding sections on one project leaves other projects unaffected")
    void updateSettings_shouldNotAffectOtherProjects() throws Exception {
        givenHidden(otherProjectId, JobSection.APPROVALS);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, HIDE_NOTES_LINKS))
                .andExpect(status().isOk());

        assertThat(hiddenInDb(otherProjectId)).containsExactly("APPROVALS");
        mockMvc.perform(get(ApiPaths.project(otherProjectId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", contains("APPROVALS")));
    }

    @Test
    @DisplayName("Hiding a section whose add-on the org lacks is accepted (no subscription at all)")
    void updateSettings_shouldAcceptSectionsWithoutAddons() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, """
                        { "hiddenJobSections": ["NOTES", "APPROVALS", "RELATIONSHIPS", "LINKS", "STATUS_HISTORY"] }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections",
                        contains("NOTES", "APPROVALS", "RELATIONSHIPS", "LINKS", "STATUS_HISTORY")));

        assertThat(hiddenInDb(projectId))
                .containsExactly("NOTES", "APPROVALS", "RELATIONSHIPS", "LINKS", "STATUS_HISTORY");
    }

    @Test
    @DisplayName("A completed project gets 409 and the sections are unchanged")
    void updateSettings_shouldReturn409_whenProjectCompleted() throws Exception {
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(project);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, HIDE_NOTES_LINKS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Project.COMPLETED_NO_MUTATIONS));

        assertThat(hiddenInDb(projectId)).isEmpty();
    }

    // --- hiddenJobSections in project responses ---

    @Test
    @DisplayName("GET /api/projects/{id} returns an empty list by default")
    void getProject_shouldReturnEmptyList_byDefault() throws Exception {
        mockMvc.perform(get(ApiPaths.project(projectId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", empty()));
    }

    @Test
    @DisplayName("A value set via the settings endpoint round-trips in GET /api/projects/{id} and GET /api/projects")
    void getProjects_shouldReturnHiddenSections_afterSettingsUpdate() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, HIDE_NOTES_LINKS))
                .andExpect(status().isOk());

        mockMvc.perform(get(ApiPaths.project("PRJ-007")).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", contains("NOTES", "LINKS")));

        mockMvc.perform(get(ApiPaths.PROJECTS).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '" + projectId + "')].hiddenJobSections[0]").value("NOTES"))
                .andExpect(jsonPath("$[?(@.id == '" + projectId + "')].hiddenJobSections[1]").value("LINKS"))
                .andExpect(jsonPath("$[?(@.id == '" + otherProjectId + "')].hiddenJobSections[0]").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/projects/{id} (name/description) leaves the hidden sections as they are")
    void updateProject_shouldNotResetHiddenSections() throws Exception {
        givenHidden(projectId, JobSection.NOTES);

        mockMvc.perform(put(ApiPaths.project(projectId)).with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Renamed", "description": "New" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenJobSections", contains("NOTES")));

        assertThat(hiddenInDb(projectId)).containsExactly("NOTES");
    }

    // --- No backend enforcement (ADR-0053) ---

    @Test
    @DisplayName("Notes and links endpoints still work on a project that hides those sections")
    void sectionEndpoints_shouldStillWork_whenSectionsHidden() throws Exception {
        givenSubscriptionWithAddons("NOTES", "JOB_LINKS");
        givenHidden(projectId, JobSection.NOTES, JobSection.LINKS);
        JobModel job = jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Deploy").status(JobStatus.NEW).createdBy(ownerId).build());

        mockMvc.perform(post(ApiPaths.notes(projectId, job.getId())).with(as(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Agreed with the client" }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(get(ApiPaths.notes(projectId, job.getId())).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("Agreed with the client"));

        mockMvc.perform(post(ApiPaths.jobLinks(projectId, job.getId())).with(as(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://github.com/org/repo/pull/1", "label": "PR #1" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("PR #1"));
        mockMvc.perform(get(ApiPaths.job(projectId, job.getId())).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links.length()").value(1))
                .andExpect(jsonPath("$.links[0].label").value("PR #1"));
        assertThat(noteRepository.findByJobId(job.getId())).hasSize(1);
        assertThat(jobLinkRepository.findByJobId(job.getId())).hasSize(1);
    }
}
