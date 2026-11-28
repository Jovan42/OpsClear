package com.opsclear.integration;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.ApiKeyModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApiKeyRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.ApiKeyService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-0067 lock-out with the switch on: 402 {@code OrgLocked} for locked orgs, the allowlist, API keys. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "opsclear.lockout.enabled=true")
@DisplayName("Organisation lock-out (ADR-0067), lock-out switch on")
class OrgLockoutIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DSLContext dsl;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID orgId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        projectMemberRepository.deleteAll();
        jobRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(ownerId).email("owner@test.com").name("Owner").build());
    }

    private RequestPostProcessor owner() {
        return jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com").claim("name", "Owner"));
    }

    private void givenOrg(Instant trialEndsAt) {
        orgId = organisationRepository.save(OrganisationModel.builder()
                .name("Acme").slug("ACM").createdBy(ownerId).trialEndsAt(trialEndsAt).build()).getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        projectId = projectRepository.save(ProjectModel.builder()
                .name("Kitchen fit-out").ownerId(ownerId).organisationId(orgId).build()).getId();
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
    }

    private void givenLockedOrg() {
        givenOrg(Instant.now().minus(Duration.ofDays(1)));
    }

    private void givenSubscription(String status, Set<UUID> addonIds) {
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", addonIds);
        if (status != null) {
            subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, status, null, null);
        }
    }

    private UUID addonId(String key) {
        return addonRepository.findAll().stream().filter(a -> a.getKey().equals(key)).findFirst().orElseThrow().getId();
    }

    private static void expectOrgLocked(ResultActions result) throws Exception {
        result.andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("OrgLocked"))
                .andExpect(jsonPath("$.message").value(ErrorMessages.Organisation.LOCKED));
    }

    private static ResultMatcher notLocked() {
        return result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(402);
    }

    // --- 402 ---

    @Test
    @DisplayName("Locked org: 402 OrgLocked on an ungated endpoint")
    void locked_shouldGet402_onUngatedEndpoint() throws Exception {
        givenLockedOrg();

        expectOrgLocked(mockMvc.perform(get(ApiPaths.PROJECTS).with(owner())));
        expectOrgLocked(mockMvc.perform(post(ApiPaths.PROJECTS).with(owner())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"New\"}")));
        assertThat(projectRepository.countActiveByOrgId(orgId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Locked org: 402 OrgLocked on an add-on endpoint")
    void locked_shouldGet402_onGatedEndpoint() throws Exception {
        givenLockedOrg();

        expectOrgLocked(mockMvc.perform(get(ApiPaths.dashboard(projectId)).with(owner())));
    }

    @Test
    @DisplayName("Org that never had a trial (NULL), not internal or paid: 402")
    void neverHadTrial_shouldGet402() throws Exception {
        givenOrg(null);

        expectOrgLocked(mockMvc.perform(get(ApiPaths.PROJECTS).with(owner())));
    }

    @Test
    @DisplayName("CANCELED subscription with an expired trial: 402")
    void canceled_shouldGet402() throws Exception {
        givenLockedOrg();
        givenSubscription("CANCELED", Set.of(addonId("DASHBOARD")));

        expectOrgLocked(mockMvc.perform(get(ApiPaths.dashboard(projectId)).with(owner())));
    }

    @Test
    @DisplayName("JOB-200: a staged add-on without real billing doesn't make the org paid, an expired trial gets 402")
    void stagedAddon_shouldNotUnlock() throws Exception {
        givenLockedOrg();
        givenSubscription(null, Set.of(addonId("DASHBOARD")));

        expectOrgLocked(mockMvc.perform(get(ApiPaths.dashboard(projectId)).with(owner())));
    }

    @Test
    @DisplayName("An API key of a locked org gets 402")
    void apiKey_shouldGet402_whenLocked() throws Exception {
        givenLockedOrg();
        String rawKey = "opck_lockouttest_" + UUID.randomUUID().toString().replace("-", "");
        apiKeyRepository.save(ApiKeyModel.builder().userId(ownerId).name("ci")
                .keyHash(ApiKeyService.sha256(rawKey)).keyPrefix(rawKey.substring(0, 12)).build());

        expectOrgLocked(mockMvc.perform(get(ApiPaths.PROJECTS).header("X-Api-Key", rawKey)));
    }

    // --- allowlist ---

    @Test
    @DisplayName("Locked org: the allowlist passes, and the org shows LOCKED with lockoutEnabled true")
    void locked_shouldPassAllowlist() throws Exception {
        givenLockedOrg();

        mockMvc.perform(get(ApiPaths.MY_ORG).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.state").value("LOCKED"))
                .andExpect(jsonPath("$.access.lockoutEnabled").value(true));
        mockMvc.perform(get(ApiPaths.organisation(orgId)).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.state").value("LOCKED"));
        mockMvc.perform(get(ApiPaths.USER_PREFERENCES).with(owner())).andExpect(status().isOk());
        mockMvc.perform(get(ApiPaths.usersSearch("own")).with(owner())).andExpect(notLocked());
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG).with(owner())).andExpect(status().isOk());
        mockMvc.perform(get(ApiPaths.HEALTH).with(owner())).andExpect(status().isOk());
        mockMvc.perform(get(ApiPaths.orgSubscription(orgId)).with(owner())).andExpect(notLocked());
        mockMvc.perform(get(ApiPaths.orgCreditsBalance(orgId)).with(owner())).andExpect(status().isOk());
        mockMvc.perform(get(ApiPaths.SUPER_ADMIN_ORGANISATIONS).with(owner())).andExpect(status().isForbidden());
        mockMvc.perform(get(ApiPaths.publicInvite("no-such-token"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Locked org: Paddle checkout and subscription changes are allowlisted (no 402)")
    void locked_shouldReachPaddleCheckout() throws Exception {
        givenLockedOrg();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancel(orgId)).with(owner())).andExpect(notLocked());
        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId)).with(owner())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(notLocked());
    }

    @Test
    @DisplayName("Locked org: GET-only allowlist entries still block other methods on the same path")
    void locked_shouldBlockNonGetOnGetOnlyEntry() throws Exception {
        givenLockedOrg();

        expectOrgLocked(mockMvc.perform(post(ApiPaths.acceptInvite("some-token")).with(owner())));
    }

    // --- not locked ---

    @Test
    @DisplayName("A user without an org passes (onboarding): creating an org works")
    void noOrg_shouldPass() throws Exception {
        mockMvc.perform(post(ApiPaths.ORGANISATIONS).with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Brightline\", \"slug\": \"BRT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access.state").value("TRIAL"))
                .andExpect(jsonPath("$.access.lockoutEnabled").value(true));
    }

    @Test
    @DisplayName("A trial org passes and gets add-on endpoints")
    void trial_shouldPass() throws Exception {
        givenOrg(Instant.now().plus(Duration.ofDays(10)));

        mockMvc.perform(get(ApiPaths.PROJECTS).with(owner())).andExpect(status().isOk());
        mockMvc.perform(get(ApiPaths.dashboard(projectId)).with(owner())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A PAST_DUE org with an expired trial is paid, not locked")
    void pastDue_shouldPass() throws Exception {
        givenLockedOrg();
        givenSubscription("PAST_DUE", Set.of(addonId("DASHBOARD")));

        mockMvc.perform(get(ApiPaths.PROJECTS).with(owner())).andExpect(status().isOk());
        mockMvc.perform(get(ApiPaths.dashboard(projectId)).with(owner())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("An internal org with an expired trial is never locked")
    void internal_shouldPass() throws Exception {
        givenLockedOrg();
        givenSubscription(null, Set.of());
        dsl.update(ORG_SUBSCRIPTIONS).set(ORG_SUBSCRIPTIONS.IS_INTERNAL, true)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId)).execute();

        mockMvc.perform(get(ApiPaths.PROJECTS).with(owner())).andExpect(status().isOk());
        mockMvc.perform(get(ApiPaths.dashboard(projectId)).with(owner())).andExpect(status().isOk());
    }
}
