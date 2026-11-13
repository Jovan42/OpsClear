package com.opsclear.integration;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
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

import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Project settings: completion approval gate (ADR-0052, JOB-285)")
class ProjectSettingsIntegrationTest {

    private static final String GATE_ON = """
            { "requireCompletionApproval": true }
            """;
    private static final String GATE_OFF = """
            { "requireCompletionApproval": false }
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private DSLContext dsl;
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

    @AfterEach
    void cleanUp() {
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

        projectId = projectRepository.save(ProjectModel.builder().friendlyId("PRJ-007").name("Gated Project")
                .ownerId(ownerId).organisationId(orgId).build()).getId();
        addMember(ownerId, ProjectMemberRole.OWNER);
        addMember(adminId, ProjectMemberRole.ADMIN);
        addMember(memberId, ProjectMemberRole.MEMBER);
    }

    // --- Helpers ---

    private void addMember(UUID userId, ProjectMemberRole role) {
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(userId).role(role).build());
    }

    private RequestPostProcessor as(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("email", userId + "@example.com"));
    }

    private MockHttpServletRequestBuilder patchSettings(String path, UUID callerId, String body) {
        return patch(path).with(as(callerId)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void givenSubscriptionWithAddon(String addonKey) {
        UUID addonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals(addonKey)).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(addonId));
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, "ACTIVE", null, null);
    }

    private void givenGate(boolean on) {
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setRequireCompletionApproval(on);
        projectRepository.save(project);
    }

    private boolean gateInDb() {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow().isRequireCompletionApproval();
    }

    // --- PATCH /api/projects/{id}/settings ---

    @Test
    @DisplayName("Owner turns the gate on with the APPROVALS add-on: 200, response and DB updated")
    void updateSettings_shouldEnableGate_whenOwnerWithAddon() throws Exception {
        givenSubscriptionWithAddon("APPROVALS");

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, GATE_ON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.name").value("Gated Project"))
                .andExpect(jsonPath("$.requireCompletionApproval").value(true));

        assertThat(gateInDb()).isTrue();
    }

    @Test
    @DisplayName("Admin turns the gate on with the APPROVALS add-on: 200")
    void updateSettings_shouldEnableGate_whenAdminWithAddon() throws Exception {
        givenSubscriptionWithAddon("APPROVALS");

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), adminId, GATE_ON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(true));

        assertThat(gateInDb()).isTrue();
    }

    @Test
    @DisplayName("Member gets 403 and the gate is unchanged")
    void updateSettings_shouldReturn403_whenMember() throws Exception {
        givenSubscriptionWithAddon("APPROVALS");

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), memberId, GATE_ON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN));

        assertThat(gateInDb()).isFalse();
    }

    @Test
    @DisplayName("An org member outside the project gets 403")
    void updateSettings_shouldReturn403_whenNotProjectMember() throws Exception {
        UUID outsiderId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(outsiderId).email("out@example.com").name("Outsider").build());
        organisationRepository.saveMember(orgId, outsiderId, OrganisationRole.MEMBER);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), outsiderId, GATE_OFF))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Member.NOT_A_MEMBER));
    }

    @Test
    @DisplayName("Turning the gate on without the APPROVALS add-on gets 403 and the gate stays off")
    void updateSettings_shouldReturn403_whenEnablingWithoutAddon() throws Exception {
        givenSubscriptionWithAddon("NOTES");

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, GATE_ON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value(ErrorMessages.Project.COMPLETION_GATE_REQUIRES_APPROVALS_ADDON));

        assertThat(gateInDb()).isFalse();
    }

    @Test
    @DisplayName("Turning the gate on with no subscription at all gets 403")
    void updateSettings_shouldReturn403_whenNoSubscription() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, GATE_ON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value(ErrorMessages.Project.COMPLETION_GATE_REQUIRES_APPROVALS_ADDON));

        assertThat(gateInDb()).isFalse();
    }

    @Test
    @DisplayName("An internal org turns the gate on without a paid APPROVALS add-on")
    void updateSettings_shouldEnableGate_whenOrgInternal() throws Exception {
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of());
        dsl.update(ORG_SUBSCRIPTIONS).set(ORG_SUBSCRIPTIONS.IS_INTERNAL, true)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId)).execute();

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, GATE_ON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(true));

        assertThat(gateInDb()).isTrue();
    }

    @Test
    @DisplayName("Turning the gate off is allowed without the APPROVALS add-on")
    void updateSettings_shouldDisableGate_withoutAddon() throws Exception {
        givenGate(true);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), adminId, GATE_OFF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(false));

        assertThat(gateInDb()).isFalse();
    }

    @Test
    @DisplayName("An empty body changes nothing: 200 with the current value")
    void updateSettings_shouldLeaveGateUnchanged_whenBodyEmpty() throws Exception {
        givenGate(true);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(true));

        assertThat(gateInDb()).isTrue();
    }

    @Test
    @DisplayName("Re-sending true on an already gated project succeeds even after the add-on lapsed")
    void updateSettings_shouldKeepGateOn_whenAlreadyOnWithoutAddon() throws Exception {
        givenGate(true);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, GATE_ON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(true));

        assertThat(gateInDb()).isTrue();
    }

    @Test
    @DisplayName("Accepts the project's friendly ID in the path")
    void updateSettings_shouldResolveFriendlyId() throws Exception {
        givenSubscriptionWithAddon("APPROVALS");

        mockMvc.perform(patchSettings(ApiPaths.projectSettings("PRJ-007"), ownerId, GATE_ON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendlyId").value("PRJ-007"))
                .andExpect(jsonPath("$.requireCompletionApproval").value(true));

        assertThat(gateInDb()).isTrue();
    }

    @Test
    @DisplayName("An unknown friendly ID gets 404")
    void updateSettings_shouldReturn404_whenFriendlyIdUnknown() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings("PRJ-999"), ownerId, GATE_OFF))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Project.NOT_FOUND));
    }

    @Test
    @DisplayName("An unknown project ID gets 404")
    void updateSettings_shouldReturn404_whenProjectUnknown() throws Exception {
        mockMvc.perform(patchSettings(ApiPaths.projectSettings(UUID.randomUUID()), ownerId, GATE_OFF))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Project.NOT_FOUND));
    }

    @Test
    @DisplayName("A completed project gets 409 and the gate is unchanged")
    void updateSettings_shouldReturn409_whenProjectCompleted() throws Exception {
        givenGate(true);
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(project);

        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, GATE_OFF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Project.COMPLETED_NO_MUTATIONS));

        assertThat(gateInDb()).isTrue();
    }

    @Test
    @DisplayName("Without authentication gets 401")
    void updateSettings_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(patch(ApiPaths.projectSettings(projectId))
                        .contentType(MediaType.APPLICATION_JSON).content(GATE_ON))
                .andExpect(status().isUnauthorized());

        assertThat(gateInDb()).isFalse();
    }

    // --- requireCompletionApproval in project responses ---

    @Test
    @DisplayName("GET /api/projects includes requireCompletionApproval")
    void listProjects_shouldIncludeGate() throws Exception {
        givenGate(true);

        mockMvc.perform(get(ApiPaths.PROJECTS).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requireCompletionApproval").value(true));
    }

    @Test
    @DisplayName("GET /api/projects/{id} includes requireCompletionApproval, false by default")
    void getProject_shouldIncludeGate_defaultFalse() throws Exception {
        mockMvc.perform(get(ApiPaths.project(projectId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(false));
    }

    @Test
    @DisplayName("GET /api/projects/{id} reflects a gate turned on via the settings endpoint")
    void getProject_shouldReflectGate_afterSettingsUpdate() throws Exception {
        givenSubscriptionWithAddon("APPROVALS");
        mockMvc.perform(patchSettings(ApiPaths.projectSettings(projectId), ownerId, GATE_ON))
                .andExpect(status().isOk());

        mockMvc.perform(get(ApiPaths.project("PRJ-007")).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(true));
    }

    @Test
    @DisplayName("PUT /api/projects/{id} (name/description) leaves the gate as it is")
    void updateProject_shouldNotResetGate() throws Exception {
        givenGate(true);

        mockMvc.perform(put(ApiPaths.project(projectId)).with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Renamed", "description": "New" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.requireCompletionApproval").value(true));

        assertThat(gateInDb()).isTrue();
    }
}
