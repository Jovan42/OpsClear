package com.opsclear.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.jooq.Record;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.APPROVALS;
import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Withdrawing and auto-cancelling completion approvals (ADR-0052, JOB-287). The test profile runs the notification
 * executor synchronously, so notifications exist when a request returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Completion approvals: withdrawal and auto-cancel (ADR-0052, JOB-287)")
class CompletionApprovalCancelIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID otherMemberId;
    private UUID orgId;
    private UUID projectId;
    private UUID jobId;
    // The user sync filter overwrites email and name from the JWT, so requests carry the real ones.
    private final Map<UUID, UserModel> users = new HashMap<>();

    @BeforeEach
    void setUp() {
        cleanUp();

        ownerId = user("owner@example.com", "Olga Owner");
        adminId = user("admin@example.com", "Adam Admin");
        memberId = user("member@example.com", "Max Member");
        otherMemberId = user("other@example.com", "Otto Other");

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Cancel Org").slug("CNCL").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, otherMemberId, OrganisationRole.MEMBER);
        friendlyIdRepository.seedForOrg(orgId);

        projectId = projectRepository.save(ProjectModel.builder().friendlyId("PRJ-287").name("Gated Project")
                .ownerId(ownerId).organisationId(orgId).requireCompletionApproval(true).build()).getId();
        addMember(ownerId, ProjectMemberRole.OWNER);
        addMember(adminId, ProjectMemberRole.ADMIN);
        addMember(memberId, ProjectMemberRole.MEMBER);
        addMember(otherMemberId, ProjectMemberRole.MEMBER);

        jobId = jobRepository.save(JobModel.builder().friendlyId("JOB-287").projectId(projectId)
                .title("Install solar panel").status(JobStatus.IN_PROGRESS).priority(JobPriority.MEDIUM)
                .assignedTo(memberId).deadline(Instant.parse("2026-12-01T00:00:00Z")).createdBy(ownerId)
                .build()).getId();

        givenSubscription("APPROVALS", "ACTIVE");
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        approvalRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();
        users.clear();
    }

    // --- Withdraw: POST …/approvals/{approvalId}/cancel ---

    @Test
    @DisplayName("Requester withdraws: 200 CANCELLED, job In Progress, nobody notified, a new request is possible")
    void cancel_shouldWithdraw_whenRequester() throws Exception {
        UUID approvalId = requestCompletion();

        cancel(memberId, approvalId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.kind").value("COMPLETION"))
                .andExpect(jsonPath("$.approverId").value(memberId.toString()))
                .andExpect(jsonPath("$.decidedAt").exists())
                .andExpect(jsonPath("$.comment").value(nullValue()))
                .andExpect(jsonPath("$.completionApplied").value(nullValue()));

        Record approval = onlyApproval();
        assertThat(approval.get(APPROVALS.STATUS)).isEqualTo("CANCELLED");
        assertThat(approval.get(APPROVALS.APPROVER_ID)).isEqualTo(memberId);
        assertThat(approval.get(APPROVALS.DECIDED_AT)).isNotNull();
        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(dsl.fetchCount(NOTIFICATIONS)).isZero();

        // The job no longer shows a pending request, and the Member can request completion again
        mockMvc.perform(get(ApiPaths.job(projectId, jobId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCompletionApproval").value(nullValue()));
        complete(memberId).andExpect(status().isAccepted());
        assertThat(dsl.fetchCount(APPROVALS, APPROVALS.STATUS.eq("PENDING"))).isEqualTo(1);
    }

    @Test
    @DisplayName("Owner withdraws: CANCELLED, requester gets APPROVAL_DECIDED with decision CANCELLED and kind")
    void cancel_shouldNotifyRequester_whenOwnerWithdraws() throws Exception {
        UUID approvalId = requestCompletion();

        cancel(ownerId, approvalId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.approverId").value(ownerId.toString()));

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("CANCELLED");
        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(recipients(NotificationType.APPROVAL_DECIDED)).containsExactly(memberId);
        JsonNode payload = payload(NotificationType.APPROVAL_DECIDED, memberId);
        assertThat(payload.get("decision").asText()).isEqualTo("CANCELLED");
        assertThat(payload.get("kind").asText()).isEqualTo("COMPLETION");
        assertThat(payload.has("completionApplied")).isFalse();
    }

    @Test
    @DisplayName("Admin withdraws: CANCELLED and the requester is notified")
    void cancel_shouldWithdraw_whenAdmin() throws Exception {
        UUID approvalId = requestCompletion();

        cancel(adminId, approvalId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(recipients(NotificationType.APPROVAL_DECIDED)).containsExactly(memberId);
    }

    @Test
    @DisplayName("Another Member: 403, approval stays pending")
    void cancel_shouldReturn403_whenOtherMember() throws Exception {
        UUID approvalId = requestCompletion();

        cancel(otherMemberId, approvalId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Approval.CANCEL_DENIED));

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");
        assertThat(dsl.fetchCount(NOTIFICATIONS)).isZero();
    }

    @Test
    @DisplayName("Manual approval: 409, even for its requester, and it stays pending")
    void cancel_shouldReturn409_whenManualApproval() throws Exception {
        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId)).with(as(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("description", "Can I order parts?"))))
                .andExpect(status().isCreated());
        UUID approvalId = onlyApproval().get(APPROVALS.ID);

        cancel(memberId, approvalId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Approval.ONLY_COMPLETION_CANCELLABLE));

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Already decided (rejected): 409, status unchanged")
    void cancel_shouldReturn409_whenAlreadyDecided() throws Exception {
        UUID approvalId = requestCompletion();
        decide(ownerId, approvalId, "REJECTED").andExpect(status().isOk());
        notificationRepository.deleteAll();

        cancel(memberId, approvalId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Approval.ALREADY_DECIDED));

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("REJECTED");
        assertThat(dsl.fetchCount(NOTIFICATIONS)).isZero();
    }

    @Test
    @DisplayName("Already cancelled: a second withdrawal is 409")
    void cancel_shouldReturn409_whenAlreadyCancelled() throws Exception {
        UUID approvalId = requestCompletion();
        cancel(memberId, approvalId).andExpect(status().isOk());

        cancel(ownerId, approvalId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Approval.ALREADY_DECIDED));

        assertThat(onlyApproval().get(APPROVALS.APPROVER_ID)).isEqualTo(memberId);
        assertThat(dsl.fetchCount(NOTIFICATIONS)).isZero();
    }

    @Test
    @DisplayName("A cancelled approval can't be decided afterwards (409)")
    void decide_shouldReturn409_whenCancelled() throws Exception {
        UUID approvalId = requestCompletion();
        cancel(memberId, approvalId).andExpect(status().isOk());

        decide(ownerId, approvalId, "APPROVED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Approval.ALREADY_DECIDED));

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("CANCELLED");
        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Deciding with status CANCELLED is a 400: withdrawal has its own endpoint")
    void decide_shouldReturn400_whenStatusCancelled() throws Exception {
        UUID approvalId = requestCompletion();

        decide(ownerId, approvalId, "CANCELLED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Approval.CANNOT_SET_CANCELLED));

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Completed project: withdrawal rejected with 409 as today")
    void cancel_shouldReturn409_whenProjectCompleted() throws Exception {
        UUID approvalId = requestCompletion();
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(project);

        cancel(memberId, approvalId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Project.COMPLETED_NO_MUTATIONS));

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Approval lists show the cancelled approval with status CANCELLED; pending queues drop it")
    void lists_shouldShowCancelled() throws Exception {
        UUID approvalId = requestCompletion();
        cancel(memberId, approvalId).andExpect(status().isOk());

        mockMvc.perform(get(ApiPaths.approvals(projectId, jobId)).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(approvalId.toString()))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
        mockMvc.perform(get(ApiPaths.pendingApprovals(projectId)).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- Auto-cancel ---

    @Test
    @DisplayName("Owner completes directly with a pending request: job Completed, approval CANCELLED with comment")
    void complete_shouldCancelPendingRequest_whenOwnerCompletesDirectly() throws Exception {
        UUID approvalId = requestCompletion();

        complete(ownerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.pendingCompletionApproval").value(nullValue()));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
        Record approval = onlyApproval();
        assertThat(approval.get(APPROVALS.ID)).isEqualTo(approvalId);
        assertThat(approval.get(APPROVALS.STATUS)).isEqualTo("CANCELLED");
        assertThat(approval.get(APPROVALS.COMMENT)).isEqualTo("Completed directly by Olga Owner");
        assertThat(approval.get(APPROVALS.APPROVER_ID)).isEqualTo(ownerId);
        assertThat(approval.get(APPROVALS.DECIDED_AT)).isNotNull();

        assertThat(recipients(NotificationType.APPROVAL_DECIDED)).containsExactly(memberId);
        JsonNode payload = payload(NotificationType.APPROVAL_DECIDED, memberId);
        assertThat(payload.get("decision").asText()).isEqualTo("CANCELLED");
        assertThat(payload.get("kind").asText()).isEqualTo("COMPLETION");
        assertThat(payload.get("comment").asText()).isEqualTo("Completed directly by Olga Owner");
    }

    @Test
    @DisplayName("Owner completes a job with no pending request: nothing is cancelled")
    void complete_shouldNotTouchOtherApprovals_whenNoPendingRequest() throws Exception {
        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId)).with(as(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("description", "Manual one"))))
                .andExpect(status().isCreated());
        notificationRepository.deleteAll();

        complete(ownerId).andExpect(status().isOk());

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");
        assertThat(dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.TYPE.eq(NotificationType.APPROVAL_DECIDED.name())))
                .isZero();
    }

    @Test
    @DisplayName("Blocking a job with a pending request leaves the request pending")
    void block_shouldKeepPendingRequest() throws Exception {
        requestCompletion();

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, jobId)).with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "BLOCKED", "reason", "Waiting for parts"))))
                .andExpect(status().isOk());

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Job deleted with a pending request: approval CANCELLED with comment")
    void delete_shouldCancelPendingRequest() throws Exception {
        UUID approvalId = requestCompletion();

        mockMvc.perform(delete(ApiPaths.job(projectId, jobId)).with(as(ownerId)))
                .andExpect(status().isNoContent());

        Record approval = onlyApproval();
        assertThat(approval.get(APPROVALS.ID)).isEqualTo(approvalId);
        assertThat(approval.get(APPROVALS.STATUS)).isEqualTo("CANCELLED");
        assertThat(approval.get(APPROVALS.COMMENT)).isEqualTo("Job deleted by Olga Owner");
        assertThat(approval.get(APPROVALS.APPROVER_ID)).isEqualTo(ownerId);
        assertThat(jobRepository.findByIdAndDeletedAtIsNull(jobId)).isEmpty();
        // The job is gone, so the APPROVAL_DECIDED fan-out skips it (as for every event about a deleted job)
        assertThat(dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.TYPE.eq(NotificationType.APPROVAL_DECIDED.name())))
                .isZero();
    }

    @Test
    @DisplayName("Add-on lapsed: cancel endpoint 403; the Member completes directly and their request is cancelled")
    void addonLapsed_shouldLockCancel_andCancelOnMemberCompletion() throws Exception {
        UUID approvalId = requestCompletion();
        subscriptionRepository.deleteAll();
        givenSubscription("NOTES", "ACTIVE");

        cancel(memberId, approvalId).andExpect(status().isForbidden());
        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");

        complete(memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.pendingCompletionApproval").value(nullValue()));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
        Record approval = onlyApproval();
        assertThat(approval.get(APPROVALS.STATUS)).isEqualTo("CANCELLED");
        assertThat(approval.get(APPROVALS.COMMENT)).isEqualTo("Completed directly by Max Member");
        assertThat(approval.get(APPROVALS.APPROVER_ID)).isEqualTo(memberId);
        // The Member cancelled their own request: no APPROVAL_DECIDED
        assertThat(dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.TYPE.eq(NotificationType.APPROVAL_DECIDED.name())))
                .isZero();
    }

    @Test
    @DisplayName("Subscription PAST_DUE: cancel endpoint 403; the Member completes directly and their request is cancelled")
    void pastDue_shouldLockCancel_andCancelOnMemberCompletion() throws Exception {
        UUID approvalId = requestCompletion();
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, "PAST_DUE", null, null);

        cancel(memberId, approvalId).andExpect(status().isForbidden());
        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");

        complete(memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Record approval = onlyApproval();
        assertThat(approval.get(APPROVALS.STATUS)).isEqualTo("CANCELLED");
        assertThat(approval.get(APPROVALS.COMMENT)).isEqualTo("Completed directly by Max Member");
        assertThat(dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.TYPE.eq(NotificationType.APPROVAL_DECIDED.name())))
                .isZero();
    }

    // --- Helpers ---

    private UUID user(String email, String name) {
        UUID id = UUID.randomUUID();
        users.put(id, userRepository.save(UserModel.builder().id(id).email(email).name(name).build()));
        return id;
    }

    private void addMember(UUID userId, ProjectMemberRole role) {
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(userId).role(role).build());
    }

    private void givenSubscription(String addonKey, String subscriptionStatus) {
        UUID addonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals(addonKey)).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(addonId));
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, subscriptionStatus, null, null);
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

    private ResultActions complete(UUID callerId) throws Exception {
        return mockMvc.perform(patch(ApiPaths.jobStatus(projectId, jobId)).with(as(callerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("status", "COMPLETED"))));
    }

    private UUID requestCompletion() throws Exception {
        complete(memberId).andExpect(status().isAccepted());
        notificationRepository.deleteAll();
        return onlyApproval().get(APPROVALS.ID);
    }

    private ResultActions cancel(UUID callerId, UUID approvalId) throws Exception {
        return mockMvc.perform(post(ApiPaths.cancelApproval(projectId, jobId, approvalId)).with(as(callerId)));
    }

    private ResultActions decide(UUID callerId, UUID approvalId, String decision) throws Exception {
        return mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId)).with(as(callerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("status", decision))));
    }

    private JobStatus jobStatusInDb() {
        return jobRepository.findByIdAndDeletedAtIsNull(jobId).orElseThrow().getStatus();
    }

    private Record onlyApproval() {
        return dsl.selectFrom(APPROVALS).where(APPROVALS.JOB_ID.eq(jobId)).fetchSingle();
    }

    private List<UUID> recipients(NotificationType type) {
        return dsl.select(NOTIFICATIONS.RECIPIENT_ID).from(NOTIFICATIONS)
                .where(NOTIFICATIONS.TYPE.eq(type.name()))
                .fetch(NOTIFICATIONS.RECIPIENT_ID);
    }

    private JsonNode payload(NotificationType type, UUID recipientId) throws Exception {
        String json = dsl.select(NOTIFICATIONS.PAYLOAD).from(NOTIFICATIONS)
                .where(NOTIFICATIONS.TYPE.eq(type.name()))
                .and(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
                .fetchSingle(NOTIFICATIONS.PAYLOAD).data();
        return objectMapper.readTree(json);
    }
}
