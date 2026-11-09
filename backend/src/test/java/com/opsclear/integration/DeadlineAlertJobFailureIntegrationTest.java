package com.opsclear.integration;

import com.opsclear.model.DeadlineAlertKind;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.JobDeadlineAlertRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.scheduler.DeadlineAlertJob;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOB_DEADLINE_ALERTS;
import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link DeadlineAlertJob} failure isolation (JOB-278): a job whose alert fails is logged and skipped, without
 * stopping the run or rolling back the other jobs' alert rows and notifications. The alert repository is a spy so one
 * job's insert can fail against the real database; kept in its own class because the spy needs its own context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("DeadlineAlertJob: per-job failure isolation")
class DeadlineAlertJobFailureIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T06:00:00Z");

    @MockitoSpyBean private JobDeadlineAlertRepository alertRepository;
    @Autowired private DeadlineAlertJob deadlineAlertJob;
    @Autowired private MockMvc mockMvc;
    @Autowired private DSLContext dsl;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID assigneeId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        cleanUp();
        ownerId = user("owner@example.com", "Olga Owner");
        assigneeId = user("assignee@example.com", "Ann Assignee");
        UUID orgId = organisationRepository.save(
                OrganisationModel.builder().name("Failure Org").slug("FAIL").createdBy(ownerId).build()).getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, assigneeId, OrganisationRole.MEMBER);
        projectId = projectRepository.save(ProjectModel.builder().name("Website Redesign")
                .friendlyId("PRJ-278").ownerId(ownerId).organisationId(orgId).build()).getId();
        addMember(ownerId, ProjectMemberRole.OWNER);
        addMember(assigneeId, ProjectMemberRole.MEMBER);
    }

    @AfterEach
    void cleanUp() {
        alertRepository.deleteAll();
        notificationRepository.deleteAll();
        jobRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("A job whose alert fails is skipped; the jobs before and after it are still alerted and committed")
    void failingJob_shouldNotStopOrRollBackOthers() throws Exception {
        // Overdue jobs are processed oldest deadline first: before, failing, after.
        UUID before = job("JOB-001", Instant.parse("2026-11-01T00:00:00Z"));
        Instant failingDeadline = Instant.parse("2026-11-03T00:00:00Z");
        UUID failing = job("JOB-002", failingDeadline);
        UUID after = job("JOB-003", Instant.parse("2026-11-05T00:00:00Z"));
        doThrow(new DataIntegrityViolationException("simulated failure"))
                .when(alertRepository).insertIfAbsent(eq(failing), any(), any());

        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isEqualTo(2);

        assertThat(dsl.select(JOB_DEADLINE_ALERTS.JOB_ID).from(JOB_DEADLINE_ALERTS).fetch(JOB_DEADLINE_ALERTS.JOB_ID))
                .containsExactlyInAnyOrder(before, after);
        assertThat(dsl.select(NOTIFICATIONS.JOB_ID).from(NOTIFICATIONS)
                .where(NOTIFICATIONS.TYPE.eq(NotificationType.JOB_OVERDUE.name()))
                .fetch(NOTIFICATIONS.JOB_ID))
                .containsExactlyInAnyOrder(before, before, after, after);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(jwt().jwt(j -> j.subject(assigneeId.toString())
                        .claim("email", "assignee@example.com").claim("name", "Ann Assignee"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].jobFriendlyId").value(
                        containsInAnyOrder("JOB-001", "JOB-003")));

        // Nothing was recorded for the failing job, so the next run (once the failure is gone) alerts it.
        reset(alertRepository);
        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isEqualTo(1);
        assertThat(dsl.fetchExists(JOB_DEADLINE_ALERTS, JOB_DEADLINE_ALERTS.JOB_ID.eq(failing)
                .and(JOB_DEADLINE_ALERTS.KIND.eq(DeadlineAlertKind.OVERDUE.name()))
                .and(JOB_DEADLINE_ALERTS.DEADLINE.eq(failingDeadline.atOffset(ZoneOffset.UTC).toLocalDateTime()))))
                .isTrue();
    }

    private UUID user(String email, String name) {
        UUID id = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(id).email(email).name(name).build());
        return id;
    }

    private void addMember(UUID userId, ProjectMemberRole role) {
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(userId).role(role).build());
    }

    private UUID job(String friendlyId, Instant deadline) {
        return jobRepository.save(JobModel.builder().friendlyId(friendlyId).projectId(projectId).title(friendlyId)
                .status(JobStatus.IN_PROGRESS).priority(JobPriority.MEDIUM).assignedTo(assigneeId)
                .deadline(deadline).createdBy(ownerId).build()).getId();
    }
}
