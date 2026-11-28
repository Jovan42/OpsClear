package com.opsclear.integration;

import com.opsclear.model.OrgInviteModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApiKeyRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0067 with the lock-out switch off (the default): {@code access} in the organisation response, the trial at
 * creation, and an expired trial that is reported {@code LOCKED} but isn't blocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Organisation access state (ADR-0067), lock-out switch off")
class OrgAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DSLContext dsl;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private OrgInviteRepository inviteRepository;
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

    private SubscriptionTierModel tier(boolean unlimitedProjects) {
        return tierRepository.findAll().stream()
                .filter(t -> (t.getMaxProjects() == null) == unlimitedProjects)
                .findFirst().orElseThrow();
    }

    private void givenPaid(SubscriptionTierModel tier, String status) {
        subscriptionRepository.create(orgId, tier.getId(), "MONTHLY", Set.of());
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, status, null, null);
    }

    private static Instant inDays(long days) {
        return Instant.now().plus(Duration.ofDays(days));
    }

    @Test
    @DisplayName("POST /organisations: a new org starts a 30-day trial, stored and shown in access")
    void create_shouldStartTrial() throws Exception {
        Instant before = Instant.now();

        mockMvc.perform(post(ApiPaths.ORGANISATIONS).with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Brightline\", \"slug\": \"BRT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access.state").value("TRIAL"))
                .andExpect(jsonPath("$.access.trialEndsAt").isNotEmpty())
                .andExpect(jsonPath("$.access.maxMembers").value(5))
                .andExpect(jsonPath("$.access.maxProjects").value(3))
                .andExpect(jsonPath("$.access.memberCount").value(1))
                .andExpect(jsonPath("$.access.pendingInviteCount").value(0))
                .andExpect(jsonPath("$.access.activeProjectCount").value(0))
                .andExpect(jsonPath("$.access.lockoutEnabled").value(false));

        OrganisationModel org = organisationRepository.findByMember(ownerId).orElseThrow();
        assertThat(org.getTrialEndsAt())
                .isBetween(before.plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS),
                        Instant.now().plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("GET /organisations/mine: a trial org's access counts members, pending invites and active projects")
    void mine_shouldShowTrialWithUsage() throws Exception {
        Instant trialEndsAt = inDays(12).truncatedTo(ChronoUnit.SECONDS);
        givenOrg(trialEndsAt);
        UUID memberId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(memberId).email("m@test.com").name("Member").build());
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        inviteRepository.save(OrgInviteModel.builder().organisationId(orgId).email("new@test.com")
                .invitedBy(ownerId).token("tok-pending").expiresAt(inDays(7)).build());
        inviteRepository.save(OrgInviteModel.builder().organisationId(orgId).email("old@test.com")
                .invitedBy(ownerId).token("tok-expired").expiresAt(inDays(-1)).build());
        projectRepository.save(ProjectModel.builder().name("Done").ownerId(ownerId).organisationId(orgId)
                .status(ProjectStatus.COMPLETED).build());

        mockMvc.perform(get(ApiPaths.MY_ORG).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orgId.toString()))
                .andExpect(jsonPath("$.access.state").value("TRIAL"))
                .andExpect(jsonPath("$.access.trialEndsAt").value(trialEndsAt.toString()))
                .andExpect(jsonPath("$.access.memberCount").value(2))
                .andExpect(jsonPath("$.access.pendingInviteCount").value(1))
                .andExpect(jsonPath("$.access.activeProjectCount").value(1))
                .andExpect(jsonPath("$.access.lockoutEnabled").value(false));
    }

    @Test
    @DisplayName("GET /organisations/{id}: an internal org is INTERNAL with no limits, even with a trial")
    void getById_shouldShowInternal() throws Exception {
        givenOrg(inDays(5));
        subscriptionRepository.create(orgId, tier(false).getId(), "MONTHLY", Set.of());
        dsl.update(ORG_SUBSCRIPTIONS).set(ORG_SUBSCRIPTIONS.IS_INTERNAL, true)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId)).execute();

        mockMvc.perform(get(ApiPaths.organisation(orgId)).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.state").value("INTERNAL"))
                .andExpect(jsonPath("$.access.maxMembers").value(nullValue()))
                .andExpect(jsonPath("$.access.maxProjects").value(nullValue()));
    }

    @Test
    @DisplayName("Paid beats a running trial, with the tier's limits")
    void access_shouldShowPaidLimitedTier() throws Exception {
        givenOrg(inDays(5));
        SubscriptionTierModel tier = tier(false);
        givenPaid(tier, "ACTIVE");

        mockMvc.perform(get(ApiPaths.MY_ORG).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.state").value("PAID"))
                .andExpect(jsonPath("$.access.maxMembers").value(tier.getMaxMembers()))
                .andExpect(jsonPath("$.access.maxProjects").value(tier.getMaxProjects()));
    }

    @Test
    @DisplayName("PAST_DUE is paid; a tier with unlimited projects shows maxProjects null")
    void access_shouldShowPaidUnlimitedTier_whenPastDue() throws Exception {
        givenOrg(null);
        SubscriptionTierModel tier = tier(true);
        givenPaid(tier, "PAST_DUE");

        mockMvc.perform(get(ApiPaths.MY_ORG).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.state").value("PAID"))
                .andExpect(jsonPath("$.access.maxMembers").value(tier.getMaxMembers()))
                .andExpect(jsonPath("$.access.maxProjects").value(nullValue()));
    }

    @Test
    @DisplayName("CANCELED with an expired trial is LOCKED, with the trial limits")
    void access_shouldShowLocked_whenCanceledAndTrialOver() throws Exception {
        givenOrg(inDays(-3));
        givenPaid(tier(false), "CANCELED");

        mockMvc.perform(get(ApiPaths.MY_ORG).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.state").value("LOCKED"))
                .andExpect(jsonPath("$.access.maxMembers").value(5))
                .andExpect(jsonPath("$.access.maxProjects").value(3));
    }

    @Test
    @DisplayName("Switch off: an expired trial is LOCKED in access, but gets no 402 and keeps add-on access")
    void switchOff_shouldReportLockedWithoutBlocking() throws Exception {
        givenOrg(inDays(-1));

        mockMvc.perform(get(ApiPaths.MY_ORG).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.state").value("LOCKED"))
                .andExpect(jsonPath("$.access.lockoutEnabled").value(false));
        mockMvc.perform(get(ApiPaths.PROJECTS).with(owner()))
                .andExpect(status().isOk());
        mockMvc.perform(get(ApiPaths.dashboard(projectId)).with(owner()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /organisations/{id}: the response carries access and the trial end doesn't move")
    void update_shouldKeepTrial() throws Exception {
        Instant trialEndsAt = inDays(9).truncatedTo(ChronoUnit.SECONDS);
        givenOrg(trialEndsAt);

        mockMvc.perform(patch(ApiPaths.organisation(orgId)).with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme Renamed\", \"slug\": \"ACM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Renamed"))
                .andExpect(jsonPath("$.access.state").value("TRIAL"))
                .andExpect(jsonPath("$.access.trialEndsAt").value(trialEndsAt.toString()));

        assertThat(organisationRepository.findTrialEndsAt(orgId)).contains(trialEndsAt);
    }
}
