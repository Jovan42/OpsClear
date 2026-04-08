package com.opsclear.integration;

import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID nonMemberId;
    private UUID projectId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        projectMemberRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        nonMemberId = UUID.randomUUID();

        userRepository.save(UserModel.builder().id(ownerId).email("owner@test.com").name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@test.com").name("Member").build());
        userRepository.save(UserModel.builder().id(nonMemberId).email("other@test.com").name("Other").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Test Org").slug("TST").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.OWNER);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Test Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());
    }

    private JobModel saveJob(String title, JobStatus status, UUID assignedTo) {
        return jobRepository.save(JobModel.builder()
                .projectId(projectId).title(title).status(status)
                .assignedTo(assignedTo).createdBy(ownerId).build());
    }

    private void saveJobWithDeadline(String title, JobStatus status, UUID assignedTo, Instant deadline) {
        jobRepository.save(JobModel.builder()
                .projectId(projectId).title(title).status(status)
                .assignedTo(assignedTo).deadline(deadline).createdBy(ownerId).build());
    }

    private void saveBlockedJob(UUID assignedTo) {
        // INSERT does not include blocked_* columns — set them via a subsequent UPDATE
        JobModel job = jobRepository.save(JobModel.builder()
                .projectId(projectId).title("Blocked Job").status(JobStatus.BLOCKED)
                .assignedTo(assignedTo).createdBy(ownerId).build());
        job.setBlockedBy(ownerId);
        job.setBlockedAt(Instant.now().minus(3, ChronoUnit.DAYS));
        jobRepository.save(job);
    }

    // --- Access control ---

    @Test
    @DisplayName("OWNER should receive 200 when requesting the dashboard")
    void getDashboard_shouldReturn200_forOwner() throws Exception {
        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MEMBER should receive 200 when requesting the dashboard")
    void getDashboard_shouldReturn200_forMember() throws Exception {
        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@test.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 403 when requester is not a project member")
    void getDashboard_shouldReturn403_forNonMember() throws Exception {
        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(nonMemberId.toString()).claim("email", "other@test.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated request")
    void getDashboard_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(get(ApiPaths.dashboard(projectId)))
                .andExpect(status().isUnauthorized());
    }

    // --- Summary counts ---

    @Test
    @DisplayName("Summary counts should reflect all job statuses correctly")
    void getDashboard_shouldReturnCorrectSummaryCounts() throws Exception {
        saveJob("New Job", JobStatus.NEW, memberId);
        saveJob("In Progress Job", JobStatus.IN_PROGRESS, memberId);
        saveJob("Completed Job", JobStatus.COMPLETED, memberId);
        saveBlockedJob(memberId);

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(4))
                .andExpect(jsonPath("$.summary.newCount").value(1))
                .andExpect(jsonPath("$.summary.inProgressCount").value(1))
                .andExpect(jsonPath("$.summary.blockedCount").value(1))
                .andExpect(jsonPath("$.summary.completedCount").value(1));
    }

    @Test
    @DisplayName("Summary should show zero counts for an empty project")
    void getDashboard_shouldReturnZeroCounts_whenNoJobs() throws Exception {
        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.blockedCount").value(0))
                .andExpect(jsonPath("$.summary.overdueCount").value(0))
                .andExpect(jsonPath("$.summary.pendingApprovalsCount").value(0))
                .andExpect(jsonPath("$.blockedJobs").isEmpty())
                .andExpect(jsonPath("$.overdueJobs").isEmpty())
                .andExpect(jsonPath("$.pendingApprovals").isEmpty());
    }

    // --- Blocked jobs ---

    @Test
    @DisplayName("Blocked jobs should appear in the blockedJobs list")
    void getDashboard_shouldIncludeBlockedJobs_inBlockedJobsList() throws Exception {
        saveBlockedJob(memberId);
        saveJob("Normal Job", JobStatus.IN_PROGRESS, memberId);

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedJobs.length()").value(1))
                .andExpect(jsonPath("$.blockedJobs[0].title").value("Blocked Job"))
                .andExpect(jsonPath("$.blockedJobs[0].status").value("BLOCKED"));
    }

    // --- Overdue jobs ---

    @Test
    @DisplayName("Jobs with past deadline and non-completed status should appear in overdueJobs")
    void getDashboard_shouldIncludeOverdueJobs_whenDeadlinePastAndNotCompleted() throws Exception {
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        saveJobWithDeadline("Overdue Job", JobStatus.IN_PROGRESS, memberId, yesterday);

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueJobs.length()").value(1))
                .andExpect(jsonPath("$.overdueJobs[0].title").value("Overdue Job"))
                .andExpect(jsonPath("$.summary.overdueCount").value(1));
    }

    @Test
    @DisplayName("Completed jobs with past deadline should not appear in overdueJobs")
    void getDashboard_shouldNotIncludeCompletedJobs_inOverdueList() throws Exception {
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        saveJobWithDeadline("Completed Late Job", JobStatus.COMPLETED, memberId, yesterday);
        saveJobWithDeadline("Overdue Job", JobStatus.IN_PROGRESS, memberId, yesterday);

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueJobs.length()").value(1))
                .andExpect(jsonPath("$.overdueJobs[0].title").value("Overdue Job"));
    }

    @Test
    @DisplayName("Jobs with future deadline should not appear in overdueJobs")
    void getDashboard_shouldNotIncludeJobs_withFutureDeadline_inOverdueList() throws Exception {
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
        saveJobWithDeadline("Future Deadline Job", JobStatus.IN_PROGRESS, memberId, tomorrow);

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueJobs").isEmpty())
                .andExpect(jsonPath("$.summary.overdueCount").value(0));
    }

    // --- Pending approvals ---

    @Test
    @DisplayName("Pending approvals should appear in the dashboard for OWNER")
    void getDashboard_shouldIncludePendingApprovals_forOwner() throws Exception {
        JobModel job = saveJob("Job With Approval", JobStatus.IN_PROGRESS, memberId);
        approvalRepository.insert(job.getId(), memberId, "Need approval for purchase");

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingApprovals.length()").value(1))
                .andExpect(jsonPath("$.pendingApprovals[0].description").value("Need approval for purchase"))
                .andExpect(jsonPath("$.summary.pendingApprovalsCount").value(1));
    }

    @Test
    @DisplayName("Pending approvals should be empty for MEMBER")
    void getDashboard_shouldReturnEmptyPendingApprovals_forMember() throws Exception {
        JobModel job = saveJob("Job With Approval", JobStatus.IN_PROGRESS, memberId);
        approvalRepository.insert(job.getId(), memberId, "Need approval for purchase");

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingApprovals").isEmpty())
                .andExpect(jsonPath("$.summary.pendingApprovalsCount").value(0));
    }

    @Test
    @DisplayName("Decided approvals should not appear in pending approvals")
    void getDashboard_shouldNotIncludeDecidedApprovals_inPendingList() throws Exception {
        JobModel job = saveJob("Job", JobStatus.IN_PROGRESS, memberId);
        ApprovalModel approval = approvalRepository.insert(job.getId(), memberId, "Need approval");
        approvalRepository.updateDecision(approval.getId(), ownerId, ApprovalStatus.APPROVED, null,
                Instant.now());

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingApprovals").isEmpty())
                .andExpect(jsonPath("$.summary.pendingApprovalsCount").value(0));
    }

    // --- MEMBER visibility scoping ---

    @Test
    @DisplayName("MEMBER should see only their own jobs in the dashboard")
    void getDashboard_shouldScopeDashboard_toMembersOwnJobs() throws Exception {
        saveJob("Owner Job", JobStatus.BLOCKED, ownerId);
        saveJob("Member Job", JobStatus.IN_PROGRESS, memberId);

        mockMvc.perform(get(ApiPaths.dashboard(projectId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.summary.blockedCount").value(0))
                .andExpect(jsonPath("$.summary.inProgressCount").value(1));
    }
}
