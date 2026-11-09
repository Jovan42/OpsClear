package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrgInviteModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.FeedbackSubmissionRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrgCreditRepository;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static com.opsclear.generated.jooq.Tables.ORGANISATIONS;
import static com.opsclear.generated.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification fan-out (ADR-0051, JOB-279) for PROJECT_MEMBER_ADDED, INVITE_ACCEPTED, FEEDBACK_RESOLVED and
 * SUBSCRIPTION_PAST_DUE: the real endpoints (and a self-signed Paddle webhook, as in
 * {@code PaddleWebhookIntegrationTest}) trigger the events, and results are checked through
 * {@code GET /api/notifications} and the database. The test profile runs the notification executor synchronously.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Notification fan-out: project, organisation and billing events")
class NotificationOrgEventsIntegrationTest {

    private static final String TIMESTAMP = "1700000000";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OrgInviteRepository orgInviteRepository;
    @Autowired private OrgCreditRepository orgCreditRepository;
    @Autowired private FeedbackSubmissionRepository feedbackSubmissionRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    @Value("${paddle.webhook-secret}")
    private String webhookSecret;

    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID newUserId;
    private UUID superUserId;
    private UUID orgId;
    private UUID projectId;
    // The user sync filter overwrites email and name from the JWT, so requests carry the real ones.
    private final Map<UUID, UserModel> users = new HashMap<>();

    @BeforeEach
    void setUp() {
        cleanUp();

        ownerId = user("owner@example.com", "Olga Owner");
        adminId = user("admin@example.com", "Adam Admin");
        memberId = user("member@example.com", "Mia Member");
        newUserId = user("new@example.com", "Nina New");
        superUserId = user("super@example.com", "Sam Super");
        dsl.update(USERS).set(USERS.SUPER_USER, true).where(USERS.ID.eq(superUserId)).execute();

        orgId = organisationRepository.save(
                OrganisationModel.builder().name("Acme Corp").slug("ACM").createdBy(ownerId).build()).getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.ADMIN);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);

        projectId = projectRepository.save(ProjectModel.builder().name("Website Redesign")
                .friendlyId("PRJ-279").ownerId(ownerId).organisationId(orgId).build()).getId();
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
    }

    // Other integration classes only clean some tables, so leave nothing of ours behind.
    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        orgInviteRepository.deleteAll();
        orgCreditRepository.deleteAll();
        feedbackSubmissionRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- PROJECT_MEMBER_ADDED ---

    @Test
    @DisplayName("PROJECT_MEMBER_ADDED notifies the added user with project and role; the acting Owner isn't notified")
    void memberAdded_shouldNotifyAddedUser_notActingOwner() throws Exception {
        addProjectMember(ownerId, memberId, "ADMIN").andExpect(status().isCreated());

        assertThat(recipients(NotificationType.PROJECT_MEMBER_ADDED)).containsExactly(memberId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("PROJECT_MEMBER_ADDED"))
                .andExpect(jsonPath("$.items[0].actor.id").value(ownerId.toString()))
                .andExpect(jsonPath("$.items[0].actor.name").value("Olga Owner"))
                .andExpect(jsonPath("$.items[0].projectFriendlyId").value("PRJ-279"))
                .andExpect(jsonPath("$.items[0].jobFriendlyId").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload.projectFriendlyId").value("PRJ-279"))
                .andExpect(jsonPath("$.items[0].payload.projectName").value("Website Redesign"))
                .andExpect(jsonPath("$.items[0].payload.actorName").value("Olga Owner"))
                .andExpect(jsonPath("$.items[0].payload.role").value("ADMIN"));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("PROJECT_MEMBER_ADDED respects the added user's mute")
    void memberAdded_shouldRespectMute() throws Exception {
        mute(memberId, "PROJECT_MEMBER_ADDED");

        addProjectMember(ownerId, memberId, "MEMBER").andExpect(status().isCreated());

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("A rejected add-member request (409, already a member) creates no notification")
    void memberAddFailed_shouldCreateNothing() throws Exception {
        addProjectMember(ownerId, memberId, "MEMBER").andExpect(status().isCreated());
        notificationRepository.deleteAll();

        addProjectMember(ownerId, memberId, "MEMBER").andExpect(status().isConflict());

        assertThat(allNotificationCount()).isZero();
    }

    // --- INVITE_ACCEPTED ---

    @Test
    @DisplayName("INVITE_ACCEPTED notifies the inviter (org-level, no project) with the new member's name and email")
    void inviteAccepted_shouldNotifyInviter() throws Exception {
        String token = invite(adminId);

        acceptInvite(token);

        assertThat(recipients(NotificationType.INVITE_ACCEPTED)).containsExactly(adminId);
        assertThat(dsl.select(NOTIFICATIONS.PROJECT_ID).from(NOTIFICATIONS).fetchSingle().value1()).isNull();
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("INVITE_ACCEPTED"))
                .andExpect(jsonPath("$.items[0].actor.id").value(newUserId.toString()))
                .andExpect(jsonPath("$.items[0].projectFriendlyId").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload.orgName").value("Acme Corp"))
                .andExpect(jsonPath("$.items[0].payload.memberName").value("Nina New"))
                .andExpect(jsonPath("$.items[0].payload.memberEmail").value("new@example.com"));
    }

    @Test
    @DisplayName("INVITE_ACCEPTED falls back to the org's Owners/Admins when the inviter has left the org")
    void inviteAccepted_shouldNotifyOwnersAndAdmins_whenInviterLeft() throws Exception {
        UUID formerAdminId = user("former@example.com", "Fred Former");
        organisationRepository.saveMember(orgId, formerAdminId, OrganisationRole.ADMIN);
        String token = invite(formerAdminId);
        organisationRepository.deleteMember(orgId, formerAdminId);

        acceptInvite(token);

        assertThat(recipients(NotificationType.INVITE_ACCEPTED)).containsExactlyInAnyOrder(ownerId, adminId);
    }

    @Test
    @DisplayName("INVITE_ACCEPTED falls back to the org's Owners/Admins when the invite has no inviter")
    void inviteAccepted_shouldNotifyOwnersAndAdmins_whenNoInviter() throws Exception {
        String token = invite(null);

        acceptInvite(token);

        assertThat(recipients(NotificationType.INVITE_ACCEPTED)).containsExactlyInAnyOrder(ownerId, adminId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("INVITE_ACCEPTED"));
    }

    // --- FEEDBACK_RESOLVED ---

    @Test
    @DisplayName("FEEDBACK_RESOLVED (credited) notifies the submitter with outcome and amount, shown as OpsClear")
    void feedbackCredited_shouldNotifySubmitter() throws Exception {
        UUID submissionId = submitFeedback(memberId, "Broken button");

        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_CREDITS_GRANT)
                        .with(as(superUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("orgId", orgId, "amount", 500, "reason", "Great bug report",
                                "submissionId", submissionId))))
                .andExpect(status().isCreated());

        assertThat(recipients(NotificationType.FEEDBACK_RESOLVED)).containsExactly(memberId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("FEEDBACK_RESOLVED"))
                .andExpect(jsonPath("$.items[0].actor").doesNotExist())
                .andExpect(jsonPath("$.items[0].projectFriendlyId").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload.feedbackTitle").value("Broken button"))
                .andExpect(jsonPath("$.items[0].payload.outcome").value("CREDITED"))
                .andExpect(jsonPath("$.items[0].payload.creditAmount").value(500));
    }

    @Test
    @DisplayName("FEEDBACK_RESOLVED (declined) notifies the submitter with the outcome and no amount")
    void feedbackDeclined_shouldNotifySubmitter() throws Exception {
        UUID submissionId = submitFeedback(memberId, "Dark mode");

        mockMvc.perform(patch(ApiPaths.superAdminFeedbackDecline(submissionId)).with(as(superUserId)))
                .andExpect(status().isNoContent());

        assertThat(recipients(NotificationType.FEEDBACK_RESOLVED)).containsExactly(memberId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("FEEDBACK_RESOLVED"))
                .andExpect(jsonPath("$.items[0].actor").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload.feedbackTitle").value("Dark mode"))
                .andExpect(jsonPath("$.items[0].payload.outcome").value("DECLINED"))
                .andExpect(jsonPath("$.items[0].payload.creditAmount").doesNotExist());
    }

    @Test
    @DisplayName("Declining an already resolved submission (409) creates no second FEEDBACK_RESOLVED")
    void feedbackDeclinedTwice_shouldNotifyOnce() throws Exception {
        UUID submissionId = submitFeedback(memberId, "Dark mode");
        mockMvc.perform(patch(ApiPaths.superAdminFeedbackDecline(submissionId)).with(as(superUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch(ApiPaths.superAdminFeedbackDecline(submissionId)).with(as(superUserId)))
                .andExpect(status().isConflict());

        assertThat(recipients(NotificationType.FEEDBACK_RESOLVED)).containsExactly(memberId);
    }

    @Test
    @DisplayName("FEEDBACK_RESOLVED respects the submitter's mute")
    void feedbackResolved_shouldRespectMute() throws Exception {
        UUID submissionId = submitFeedback(memberId, "Dark mode");
        mute(memberId, "FEEDBACK_RESOLVED");

        mockMvc.perform(patch(ApiPaths.superAdminFeedbackDecline(submissionId)).with(as(superUserId)))
                .andExpect(status().isNoContent());

        assertThat(allNotificationCount()).isZero();
    }

    // --- SUBSCRIPTION_PAST_DUE ---

    @Test
    @DisplayName("subscription.past_due notifies the org's Owners/Admins once; a second webhook while past-due doesn't")
    void pastDue_shouldNotifyOwnersAndAdminsOnce() throws Exception {
        String subscriptionId = givenActivePaddleSubscription();

        postWebhook(subscriptionEvent("subscription.past_due", subscriptionId, "past_due"));

        assertThat(recipients(NotificationType.SUBSCRIPTION_PAST_DUE)).containsExactlyInAnyOrder(ownerId, adminId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("SUBSCRIPTION_PAST_DUE"))
                .andExpect(jsonPath("$.items[0].actor").doesNotExist())
                .andExpect(jsonPath("$.items[0].projectFriendlyId").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload.orgName").value("Acme Corp"));

        postWebhook(subscriptionEvent("subscription.past_due", subscriptionId, "past_due"));
        postWebhook(subscriptionEvent("subscription.updated", subscriptionId, "past_due"));

        assertThat(recipients(NotificationType.SUBSCRIPTION_PAST_DUE)).containsExactlyInAnyOrder(ownerId, adminId);
    }

    @Test
    @DisplayName("A subscription that recovers and goes past-due again notifies again")
    void pastDue_shouldNotifyAgain_afterRecovery() throws Exception {
        String subscriptionId = givenActivePaddleSubscription();

        postWebhook(subscriptionEvent("subscription.past_due", subscriptionId, "past_due"));
        postWebhook(subscriptionEvent("subscription.updated", subscriptionId, "active"));
        assertThat(recipients(NotificationType.SUBSCRIPTION_PAST_DUE)).hasSize(2);

        postWebhook(subscriptionEvent("subscription.past_due", subscriptionId, "past_due"));

        assertThat(recipients(NotificationType.SUBSCRIPTION_PAST_DUE)).hasSize(4);
    }

    @Test
    @DisplayName("SUBSCRIPTION_PAST_DUE is delivered even when the user tried to mute it")
    void pastDue_shouldIgnoreMutes() throws Exception {
        String subscriptionId = givenActivePaddleSubscription();
        // The settings endpoint refuses to mute it...
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS)
                        .with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("muted", List.of("SUBSCRIPTION_PAST_DUE")))))
                .andExpect(status().isBadRequest());
        // ...and even a stored mute (e.g. written before the rule existed) is ignored by the fan-out.
        userRepository.updatePreferences(ownerId,
                "{\"notifications\":{\"muted\":[\"SUBSCRIPTION_PAST_DUE\",\"INVITE_ACCEPTED\"]}}");

        postWebhook(subscriptionEvent("subscription.past_due", subscriptionId, "past_due"));

        assertThat(recipients(NotificationType.SUBSCRIPTION_PAST_DUE)).containsExactlyInAnyOrder(ownerId, adminId);
    }

    @Test
    @DisplayName("Other org-level types respect a stored mute (INVITE_ACCEPTED)")
    void inviteAccepted_shouldRespectMute() throws Exception {
        mute(adminId, "INVITE_ACCEPTED");
        String token = invite(adminId);

        acceptInvite(token);

        assertThat(allNotificationCount()).isZero();
    }

    // --- Helpers ---

    private UUID user(String email, String name) {
        UUID id = UUID.randomUUID();
        users.put(id, userRepository.save(UserModel.builder().id(id).email(email).name(name).build()));
        return id;
    }

    private RequestPostProcessor as(UUID userId) {
        UserModel user = users.get(userId);
        return jwt().jwt(j -> j.subject(userId.toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName()));
    }

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private void mute(UUID userId, String type) throws Exception {
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS)
                        .with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("muted", List.of(type)))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions addProjectMember(UUID callerId, UUID userId,
                                                                             String role) throws Exception {
        return mockMvc.perform(post(ApiPaths.members(projectId))
                .with(as(callerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userId", userId, "role", role))));
    }

    // A pending invite for new@example.com, stored directly so no email is sent. invitedBy may be null.
    private String invite(UUID invitedBy) {
        String token = "tok_" + UUID.randomUUID();
        orgInviteRepository.save(OrgInviteModel.builder()
                .organisationId(orgId).email("new@example.com").invitedBy(invitedBy).token(token)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS)).build());
        return token;
    }

    private void acceptInvite(String token) throws Exception {
        mockMvc.perform(post(ApiPaths.acceptInvite(token)).with(as(newUserId)))
                .andExpect(status().isOk());
    }

    private UUID submitFeedback(UUID callerId, String title) throws Exception {
        String response = mockMvc.perform(post(ApiPaths.FEEDBACK)
                        .with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("type", "BUG", "title", title, "description", "Details"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // An org_subscriptions row linked to Paddle and ACTIVE, with the org's Paddle customer id set.
    private String givenActivePaddleSubscription() {
        String subscriptionId = "sub_" + UUID.randomUUID();
        subscriptionRepository.create(orgId, tierRepository.findAll().getFirst().getId(), "MONTHLY", Set.of());
        subscriptionRepository.updateFromPaddleWebhook(orgId, subscriptionId, "ACTIVE", null, null);
        dsl.update(ORGANISATIONS).set(ORGANISATIONS.PADDLE_CUSTOMER_ID, customerId())
                .where(ORGANISATIONS.ID.eq(orgId)).execute();
        return subscriptionId;
    }

    private String customerId() {
        return "ctm_" + orgId;
    }

    private String subscriptionEvent(String eventType, String subscriptionId, String status) {
        return "{\"event_id\":\"evt_" + UUID.randomUUID() + "\",\"event_type\":\"" + eventType + "\","
                + "\"data\":{\"id\":\"" + subscriptionId + "\",\"customer_id\":\"" + customerId() + "\","
                + "\"status\":\"" + status + "\"}}";
    }

    private void postWebhook(String body) throws Exception {
        mockMvc.perform(post(ApiPaths.WEBHOOKS_PADDLE)
                        .header("Paddle-Signature", "ts=" + TIMESTAMP + ";h1=" + hmacSha256Hex(TIMESTAMP + ":" + body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String hmacSha256Hex(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<UUID> recipients(NotificationType type) {
        return dsl.select(NOTIFICATIONS.RECIPIENT_ID).from(NOTIFICATIONS)
                .where(NOTIFICATIONS.TYPE.eq(type.name()))
                .fetch(NOTIFICATIONS.RECIPIENT_ID);
    }

    private int allNotificationCount() {
        return dsl.fetchCount(NOTIFICATIONS);
    }
}
