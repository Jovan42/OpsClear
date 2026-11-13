package com.opsclear.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.ApprovalStatus;
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
import com.opsclear.service.ApprovalService;
import com.opsclear.service.JobStatusTransitioner;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.opsclear.generated.jooq.Tables.APPROVALS;
import static com.opsclear.generated.jooq.Tables.JOB_STATUS_HISTORY;
import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gated completion (ADR-0052, JOB-286): a Member completing a job on a gated project creates a completion approval
 * (202), and approving it completes the job if it's still In Progress. The test profile runs the notification
 * executor synchronously, so notifications exist when a request returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Completion approvals: request and decision (ADR-0052, JOB-286)")
class CompletionApprovalIntegrationTest {

    private static final String DEFAULT_DESCRIPTION = "Requesting to complete this job";

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
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JobStatusTransitioner jobStatusTransitioner;
    @Autowired private ApprovalService approvalService;

    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
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

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Gate Org").slug("GATE").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        friendlyIdRepository.seedForOrg(orgId);

        projectId = projectRepository.save(ProjectModel.builder().friendlyId("PRJ-286").name("Gated Project")
                .ownerId(ownerId).organisationId(orgId).requireCompletionApproval(true).build()).getId();
        addMember(ownerId, ProjectMemberRole.OWNER);
        addMember(adminId, ProjectMemberRole.ADMIN);
        addMember(memberId, ProjectMemberRole.MEMBER);

        jobId = job("JOB-286", "Install solar panel", JobStatus.IN_PROGRESS);
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

    // --- Request: PATCH …/jobs/{jobId}/status ---

    @Test
    @DisplayName("Member completes on a gated project: 202, job still In Progress, pending approval kind COMPLETION")
    void complete_shouldReturn202AndCreateCompletionApproval_whenGatedMember() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");

        complete(memberId, null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.pendingCompletionApproval.id").exists())
                .andExpect(jsonPath("$.pendingCompletionApproval.requesterId").value(memberId.toString()))
                .andExpect(jsonPath("$.pendingCompletionApproval.requesterName").value("Max Member"))
                .andExpect(jsonPath("$.pendingCompletionApproval.requestedAt").exists())
                .andExpect(jsonPath("$.pendingCompletionApproval.message").value(DEFAULT_DESCRIPTION));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
        Record approval = onlyApproval();
        assertThat(approval.get(APPROVALS.KIND)).isEqualTo("COMPLETION");
        assertThat(approval.get(APPROVALS.STATUS)).isEqualTo("PENDING");
        assertThat(approval.get(APPROVALS.REQUESTER_ID)).isEqualTo(memberId);
        assertThat(approval.get(APPROVALS.DESCRIPTION)).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(approval.get(APPROVALS.COMPLETION_APPLIED)).isNull();
        assertThat(dsl.fetchCount(JOB_STATUS_HISTORY)).isZero();

        // APPROVAL_REQUESTED to the Owner and Admin, with kind COMPLETION
        assertThat(recipients(NotificationType.APPROVAL_REQUESTED)).containsExactlyInAnyOrder(ownerId, adminId);
        JsonNode payload = payload(NotificationType.APPROVAL_REQUESTED, ownerId);
        assertThat(payload.get("kind").asText()).isEqualTo("COMPLETION");
        assertThat(payload.get("description").asText()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.TYPE.eq(NotificationType.JOB_STATUS_CHANGED.name())))
                .isZero();
    }

    @Test
    @DisplayName("The optional message becomes the approval's description and the pending request's message")
    void complete_shouldUseMessageAsDescription() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");

        complete(memberId, "  All panels installed, please check  ")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pendingCompletionApproval.message")
                        .value("All panels installed, please check"));

        assertThat(onlyApproval().get(APPROVALS.DESCRIPTION)).isEqualTo("All panels installed, please check");
    }

    @Test
    @DisplayName("A blank message falls back to the default description")
    void complete_shouldUseDefaultDescription_whenMessageBlank() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");

        complete(memberId, "   ")
                .andExpect(status().isAccepted());

        assertThat(onlyApproval().get(APPROVALS.DESCRIPTION)).isEqualTo(DEFAULT_DESCRIPTION);
    }

    @Test
    @DisplayName("A message over 2000 characters is rejected with 400")
    void complete_shouldReturn400_whenMessageTooLong() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");

        complete(memberId, "x".repeat(2001))
                .andExpect(status().isBadRequest());

        assertThat(dsl.fetchCount(APPROVALS)).isZero();
    }

    @Test
    @DisplayName("A second request while one is pending gets 409 and creates nothing")
    void complete_shouldReturn409_whenAlreadyPending() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        complete(memberId, null).andExpect(status().isAccepted());

        complete(memberId, "again")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Approval.COMPLETION_ALREADY_PENDING));

        assertThat(dsl.fetchCount(APPROVALS)).isEqualTo(1);
        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Race guard: concurrent completion requests create exactly one approval, the rest get 409")
    void complete_shouldCreateExactlyOneApproval_whenConcurrentRequests() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        int requests = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return complete(memberId, null).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnlyOnce(202);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(requests - 1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(dsl.fetchCount(APPROVALS, APPROVALS.KIND.eq("COMPLETION").and(APPROVALS.STATUS.eq("PENDING"))))
                .isEqualTo(1);
        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Ungated project: Member completes directly with 200")
    void complete_shouldReturn200_whenUngated() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        givenGate(false);

        complete(memberId, "ignored")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.pendingCompletionApproval").value(nullValue()));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
        assertThat(dsl.fetchCount(APPROVALS)).isZero();
    }

    @Test
    @DisplayName("Gate on but the org lacks the APPROVALS add-on: gate not enforced, 200 completed")
    void complete_shouldReturn200_whenAddonMissing() throws Exception {
        givenSubscription("NOTES", "ACTIVE");

        complete(memberId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
        assertThat(dsl.fetchCount(APPROVALS)).isZero();
    }

    @Test
    @DisplayName("Gate on, APPROVALS add-on but subscription PAST_DUE: gate paused, 200 completed")
    void complete_shouldReturn200_whenSubscriptionPastDue() throws Exception {
        givenSubscription("APPROVALS", "PAST_DUE");

        complete(memberId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
        assertThat(dsl.fetchCount(APPROVALS)).isZero();
    }

    @Test
    @DisplayName("Gated project: the Owner completes directly with 200")
    void complete_shouldReturn200_whenOwnerOnGatedProject() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");

        complete(ownerId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
        assertThat(dsl.fetchCount(APPROVALS)).isZero();
    }

    @Test
    @DisplayName("Gated project: an Admin completes directly with 200")
    void complete_shouldReturn200_whenAdminOnGatedProject() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");

        complete(adminId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
        assertThat(dsl.fetchCount(APPROVALS)).isZero();
    }

    @Test
    @DisplayName("Gated Member: an illegal transition (New → Completed) is still 400 and creates no approval")
    void complete_shouldReturn400_whenGatedMemberCompletesNewJob() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID newJobId = job("JOB-287", "Not started", JobStatus.NEW);

        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, newJobId)).with(as(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "COMPLETED"))))
                .andExpect(status().isBadRequest());

        assertThat(dsl.fetchCount(APPROVALS)).isZero();
    }

    @Test
    @DisplayName("Completed project: a completion request is rejected with 409 as today")
    void complete_shouldReturn409_whenProjectCompleted() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        givenProjectCompleted();

        complete(memberId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Project.COMPLETED_NO_MUTATIONS));

        assertThat(dsl.fetchCount(APPROVALS)).isZero();
    }

    // --- Reads: pendingCompletionApproval ---

    @Test
    @DisplayName("Job detail and job list show pendingCompletionApproval (null on jobs without one)")
    void reads_shouldIncludePendingCompletionApproval() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID otherJobId = job("JOB-288", "Other job", JobStatus.IN_PROGRESS);
        complete(memberId, "Ready for review").andExpect(status().isAccepted());
        UUID approvalId = onlyApproval().get(APPROVALS.ID);

        mockMvc.perform(get(ApiPaths.job(projectId, jobId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCompletionApproval.id").value(approvalId.toString()))
                .andExpect(jsonPath("$.pendingCompletionApproval.requesterName").value("Max Member"))
                .andExpect(jsonPath("$.pendingCompletionApproval.message").value("Ready for review"));

        mockMvc.perform(get(ApiPaths.jobs(projectId)).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '" + jobId + "')].pendingCompletionApproval.id")
                        .value(approvalId.toString()))
                .andExpect(jsonPath("$[?(@.id == '" + jobId + "')].pendingCompletionApproval.requesterId")
                        .value(memberId.toString()))
                .andExpect(jsonPath("$[?(@.id == '" + jobId + "')].pendingCompletionApproval.message")
                        .value("Ready for review"))
                .andExpect(jsonPath("$[?(@.id == '" + otherJobId + "')].pendingCompletionApproval")
                        .value((Object) null));
    }

    @Test
    @DisplayName("Approval lists (job, project queue, cross-project queue) carry kind COMPLETION")
    void approvalLists_shouldCarryKind() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        complete(memberId, null).andExpect(status().isAccepted());

        mockMvc.perform(get(ApiPaths.approvals(projectId, jobId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("COMPLETION"))
                .andExpect(jsonPath("$[0].completionApplied").value(nullValue()));
        mockMvc.perform(get(ApiPaths.pendingApprovals(projectId)).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("COMPLETION"));
        mockMvc.perform(get(ApiPaths.PENDING_APPROVALS_ACROSS_ORGS).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("COMPLETION"))
                .andExpect(jsonPath("$[0].completionApplied").value(nullValue()))
                .andExpect(jsonPath("$[0].completionNotAppliedReason").value(nullValue()));
    }

    // --- Decision: PATCH …/approvals/{approvalId}/status ---

    @Test
    @DisplayName("Approve while In Progress: job Completed, history changed_by approver, completionApplied true")
    void approve_shouldCompleteJob_whenInProgress() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID approvalId = requestCompletion();

        decide(ownerId, approvalId, "APPROVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.kind").value("COMPLETION"))
                .andExpect(jsonPath("$.completionApplied").value(true))
                .andExpect(jsonPath("$.completionNotAppliedReason").value(nullValue()))
                .andExpect(jsonPath("$.approverId").value(ownerId.toString()));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
        Record history = dsl.selectFrom(JOB_STATUS_HISTORY).where(JOB_STATUS_HISTORY.JOB_ID.eq(jobId)).fetchSingle();
        assertThat(history.get(JOB_STATUS_HISTORY.CHANGED_FROM)).isEqualTo("IN_PROGRESS");
        assertThat(history.get(JOB_STATUS_HISTORY.CHANGED_TO)).isEqualTo("COMPLETED");
        assertThat(history.get(JOB_STATUS_HISTORY.CHANGED_BY)).isEqualTo(ownerId);
        Record approval = onlyApproval();
        assertThat(approval.get(APPROVALS.STATUS)).isEqualTo("APPROVED");
        assertThat(approval.get(APPROVALS.COMPLETION_APPLIED)).isTrue();
        assertThat(approval.get(APPROVALS.COMPLETION_NOT_APPLIED_REASON)).isNull();

        // APPROVAL_DECIDED to the requester; JOB_STATUS_CHANGED with the approver as actor
        assertThat(recipients(NotificationType.APPROVAL_DECIDED)).containsExactly(memberId);
        JsonNode decided = payload(NotificationType.APPROVAL_DECIDED, memberId);
        assertThat(decided.get("decision").asText()).isEqualTo("APPROVED");
        assertThat(decided.get("kind").asText()).isEqualTo("COMPLETION");
        assertThat(decided.get("completionApplied").asBoolean()).isTrue();
        assertThat(decided.has("completionNotAppliedReason")).isFalse();
        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).containsExactlyInAnyOrder(memberId, adminId);
        assertThat(dsl.selectDistinct(NOTIFICATIONS.ACTOR_ID).from(NOTIFICATIONS)
                .where(NOTIFICATIONS.TYPE.eq(NotificationType.JOB_STATUS_CHANGED.name()))
                .fetch(NOTIFICATIONS.ACTOR_ID)).containsExactly(ownerId);
        JsonNode statusChanged = payload(NotificationType.JOB_STATUS_CHANGED, memberId);
        assertThat(statusChanged.get("toStatus").asText()).isEqualTo("COMPLETED");
        assertThat(statusChanged.get("actorName").asText()).isEqualTo("Olga Owner");

        // The pending request is gone from the job
        mockMvc.perform(get(ApiPaths.job(projectId, jobId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.pendingCompletionApproval").value(nullValue()));
    }

    @Test
    @DisplayName("Job blocked meanwhile: approve records completionApplied false, reason BLOCKED, job stays Blocked")
    void approve_shouldNotApply_whenJobBlockedMeanwhile() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID approvalId = requestCompletion();
        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, jobId)).with(as(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "BLOCKED", "reason", "Waiting for parts"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.pendingCompletionApproval.id").value(approvalId.toString()));

        decide(adminId, approvalId, "APPROVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.completionApplied").value(false))
                .andExpect(jsonPath("$.completionNotAppliedReason").value("BLOCKED"));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.BLOCKED);
        Record approval = onlyApproval();
        assertThat(approval.get(APPROVALS.COMPLETION_APPLIED)).isFalse();
        assertThat(approval.get(APPROVALS.COMPLETION_NOT_APPLIED_REASON)).isEqualTo("BLOCKED");
        assertThat(dsl.fetchCount(JOB_STATUS_HISTORY, JOB_STATUS_HISTORY.CHANGED_TO.eq("COMPLETED"))).isZero();

        JsonNode decided = payload(NotificationType.APPROVAL_DECIDED, memberId);
        assertThat(decided.get("kind").asText()).isEqualTo("COMPLETION");
        assertThat(decided.get("completionApplied").asBoolean()).isFalse();
        assertThat(decided.get("completionNotAppliedReason").asText()).isEqualTo("BLOCKED");
        assertThat(dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.TYPE.eq(NotificationType.JOB_STATUS_CHANGED.name())))
                .isZero();
    }

    @Test
    @DisplayName("Reject: job stays In Progress, no completion outcome, the request can be made again")
    void reject_shouldLeaveJobInProgress() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID approvalId = requestCompletion();

        decide(ownerId, approvalId, "REJECTED", "Panels not tested yet")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.kind").value("COMPLETION"))
                .andExpect(jsonPath("$.comment").value("Panels not tested yet"))
                .andExpect(jsonPath("$.completionApplied").value(nullValue()));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(onlyApproval().get(APPROVALS.COMPLETION_APPLIED)).isNull();
        JsonNode decided = payload(NotificationType.APPROVAL_DECIDED, memberId);
        assertThat(decided.get("decision").asText()).isEqualTo("REJECTED");
        assertThat(decided.get("kind").asText()).isEqualTo("COMPLETION");
        assertThat(decided.has("completionApplied")).isFalse();

        complete(memberId, null)
                .andExpect(status().isAccepted());
        assertThat(dsl.fetchCount(APPROVALS)).isEqualTo(2);
    }

    @Test
    @DisplayName("Turning the gate off leaves a pending request approvable, and approving it completes the job")
    void approve_shouldStillComplete_afterGateTurnedOff() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID approvalId = requestCompletion();
        mockMvc.perform(patch(ApiPaths.projectSettings(projectId)).with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("requireCompletionApproval", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireCompletionApproval").value(false));

        decide(ownerId, approvalId, "APPROVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionApplied").value(true));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("Completed project: deciding a completion approval is rejected with 409 as today")
    void decide_shouldReturn409_whenProjectCompleted() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID approvalId = requestCompletion();
        givenProjectCompleted();

        decide(ownerId, approvalId, "APPROVED", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Project.COMPLETED_NO_MUTATIONS));

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");
        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("A Member can't decide a completion approval (403)")
    void decide_shouldReturn403_whenMember() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID approvalId = requestCompletion();

        decide(memberId, approvalId, "APPROVED", null)
                .andExpect(status().isForbidden());

        assertThat(onlyApproval().get(APPROVALS.STATUS)).isEqualTo("PENDING");
        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Approving a manual approval on a gated project never changes the job's status")
    void approveManual_shouldNotChangeJobStatus() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        String response = mockMvc.perform(post(ApiPaths.approvals(projectId, jobId)).with(as(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("description", "Can I order more panels?"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        UUID approvalId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        assertThat(payload(NotificationType.APPROVAL_REQUESTED, ownerId).get("kind").asText()).isEqualTo("MANUAL");

        decide(ownerId, approvalId, "APPROVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("MANUAL"))
                .andExpect(jsonPath("$.completionApplied").value(nullValue()));

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(dsl.fetchCount(JOB_STATUS_HISTORY)).isZero();
        JsonNode decided = payload(NotificationType.APPROVAL_DECIDED, memberId);
        assertThat(decided.get("kind").asText()).isEqualTo("MANUAL");
        assertThat(decided.has("completionApplied")).isFalse();
        mockMvc.perform(get(ApiPaths.job(projectId, jobId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCompletionApproval").value(nullValue()));
    }

    // --- Row locks (ADR-0052: approve vs block race) ---

    @Test
    @DisplayName("Row lock: a decision waits for an uncommitted block, then records 'not applied' with reason BLOCKED")
    void approve_shouldWaitForConcurrentBlock_andNotApply() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID approvalId = requestCompletion();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // A: locks the job, blocks it, and holds the transaction open
            Future<?> blocker = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                JobModel job = jobRepository.findByIdForUpdate(jobId).orElseThrow();
                jobStatusTransitioner.transition(job, JobStatus.BLOCKED, "Waiting for parts", membership(memberId));
                locked.countDown();
                awaitQuietly(commit);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // B: approves while A is uncommitted; it must wait on the job row lock
            Future<String> decision = pool.submit(() -> decide(ownerId, approvalId, "APPROVED", null)
                    .andReturn().getResponse().getContentAsString());
            awaitLockWaiter();
            assertThat(decision.isDone()).isFalse();

            commit.countDown();
            blocker.get(30, TimeUnit.SECONDS);
            JsonNode response = objectMapper.readTree(decision.get(30, TimeUnit.SECONDS));
            assertThat(response.get("status").asText()).isEqualTo("APPROVED");
            assertThat(response.get("completionApplied").asBoolean()).isFalse();
            assertThat(response.get("completionNotAppliedReason").asText()).isEqualTo("BLOCKED");
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.BLOCKED);
        assertThat(dsl.fetchCount(JOB_STATUS_HISTORY, JOB_STATUS_HISTORY.CHANGED_TO.eq("COMPLETED"))).isZero();
    }

    @Test
    @DisplayName("Row lock: a block waits for an uncommitted approval, then fails (400) instead of overwriting it")
    void block_shouldWaitForConcurrentApproval_andNotOverwriteCompletion() throws Exception {
        givenSubscription("APPROVALS", "ACTIVE");
        UUID approvalId = requestCompletion();
        CountDownLatch decided = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // A: approves (locks the job and auto-completes it) and holds the transaction open
            Future<?> approver = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                approvalService.decide(projectId, jobId, approvalId, ApprovalStatus.APPROVED, null, ownerId);
                decided.countDown();
                awaitQuietly(commit);
            }));
            assertThat(decided.await(10, TimeUnit.SECONDS)).isTrue();

            // B: an Admin blocks the job while A is uncommitted; it must wait on the job row lock. (Not the Member:
            // A's approval holds a key-share lock on the requester's user row, so the Member's request would wait in
            // the user sync filter before ever reading the job.)
            Future<Integer> block = pool.submit(() -> mockMvc.perform(patch(ApiPaths.jobStatus(projectId, jobId))
                            .with(as(adminId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("status", "BLOCKED", "reason", "Waiting for parts"))))
                    .andReturn().getResponse().getStatus());
            awaitLockWaiter();
            assertThat(block.isDone()).isFalse();

            commit.countDown();
            approver.get(30, TimeUnit.SECONDS);
            // B now sees COMPLETED, and COMPLETED -> BLOCKED is illegal
            assertThat(block.get(30, TimeUnit.SECONDS)).isEqualTo(400);
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }

        assertThat(jobStatusInDb()).isEqualTo(JobStatus.COMPLETED);
        assertThat(onlyApproval().get(APPROVALS.COMPLETION_APPLIED)).isTrue();
        assertThat(dsl.fetchCount(JOB_STATUS_HISTORY, JOB_STATUS_HISTORY.CHANGED_TO.eq("BLOCKED"))).isZero();
    }

    // --- Helpers ---

    private ProjectMemberModel membership(UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId).orElseThrow();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // Waits until another session in this database is blocked on a lock (the request under test).
    private void awaitLockWaiter() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = dsl.fetchOne("select count(*)::int from pg_stat_activity "
                    + "where datname = current_database() and wait_event_type = 'Lock'").get(0, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No session started waiting on a lock within 10 seconds");
    }

    private UUID user(String email, String name) {
        UUID id = UUID.randomUUID();
        users.put(id, userRepository.save(UserModel.builder().id(id).email(email).name(name).build()));
        return id;
    }

    private void addMember(UUID userId, ProjectMemberRole role) {
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(userId).role(role).build());
    }

    private UUID job(String friendlyId, String title, JobStatus status) {
        return jobRepository.save(JobModel.builder().friendlyId(friendlyId).projectId(projectId).title(title)
                .status(status).priority(JobPriority.MEDIUM).assignedTo(memberId)
                .deadline(Instant.parse("2026-12-01T00:00:00Z")).createdBy(ownerId).build()).getId();
    }

    private void givenSubscription(String addonKey, String subscriptionStatus) {
        UUID addonId = addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals(addonKey)).findFirst().orElseThrow().getId();
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", Set.of(addonId));
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, subscriptionStatus, null, null);
    }

    private void givenGate(boolean on) {
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setRequireCompletionApproval(on);
        projectRepository.save(project);
    }

    private void givenProjectCompleted() {
        ProjectModel project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow();
        project.setStatus(ProjectStatus.COMPLETED);
        projectRepository.save(project);
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

    private ResultActions complete(UUID callerId, String message) throws Exception {
        Map<String, Object> fields = new HashMap<>();
        fields.put("status", "COMPLETED");
        fields.put("message", message);
        return mockMvc.perform(patch(ApiPaths.jobStatus(projectId, jobId)).with(as(callerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fields)));
    }

    private UUID requestCompletion() throws Exception {
        complete(memberId, null).andExpect(status().isAccepted());
        notificationRepository.deleteAll();
        return onlyApproval().get(APPROVALS.ID);
    }

    private ResultActions decide(UUID callerId, UUID approvalId, String decision, String comment) throws Exception {
        Map<String, Object> fields = new HashMap<>();
        fields.put("status", decision);
        fields.put("comment", comment);
        return mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId)).with(as(callerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fields)));
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
