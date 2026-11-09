package com.opsclear.scheduler;

import com.opsclear.event.DeadlineAlertEvent;
import com.opsclear.model.DeadlineAlertKind;
import com.opsclear.model.JobModel;
import com.opsclear.repository.JobDeadlineAlertRepository;
import com.opsclear.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeadlineAlertJob")
class DeadlineAlertJobTest {

    // 06:00 UTC on 2026-11-10, when the daily job runs.
    private static final Instant NOW = Instant.parse("2026-11-10T06:00:00Z");
    private static final Instant START_OF_TODAY = Instant.parse("2026-11-10T00:00:00Z");
    private static final Instant START_OF_TOMORROW = Instant.parse("2026-11-11T00:00:00Z");
    private static final Instant START_OF_DAY_AFTER = Instant.parse("2026-11-12T00:00:00Z");

    @Mock private JobRepository jobRepository;
    @Mock private JobDeadlineAlertRepository alertRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    private DeadlineAlertJob deadlineAlertJob;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        deadlineAlertJob = new DeadlineAlertJob(jobRepository, alertRepository, eventPublisher,
                new TransactionTemplate(transactionManager));
        projectId = UUID.randomUUID();
    }

    private JobModel job(Instant deadline) {
        return JobModel.builder().id(UUID.randomUUID()).projectId(projectId).deadline(deadline).build();
    }

    private void givenDueTomorrow(JobModel... jobs) {
        when(jobRepository.findOpenInActiveProjectsWithDeadlineBetween(START_OF_TOMORROW, START_OF_DAY_AFTER))
                .thenReturn(List.of(jobs));
    }

    // The alert row didn't exist yet, so insertIfAbsent records it.
    private void givenNewAlert(JobModel job, DeadlineAlertKind kind) {
        when(alertRepository.insertIfAbsent(job.getId(), kind, job.getDeadline())).thenReturn(true);
    }

    private void givenOverdue(JobModel... jobs) {
        when(jobRepository.findOpenInActiveProjectsWithDeadlineBefore(START_OF_TODAY)).thenReturn(List.of(jobs));
    }

    @Test
    @DisplayName("Queries tomorrow's UTC date for DEADLINE_SOON and dates before today for OVERDUE")
    void checkDeadlines_shouldQueryUtcDayWindows() {
        givenDueTomorrow();
        givenOverdue();

        // Late in the UTC day: the windows still follow the UTC date, not a rolling 24 hours.
        int sent = deadlineAlertJob.checkDeadlines(Instant.parse("2026-11-10T23:59:59Z"));

        assertThat(sent).isZero();
        verify(jobRepository).findOpenInActiveProjectsWithDeadlineBetween(START_OF_TOMORROW, START_OF_DAY_AFTER);
        verify(jobRepository).findOpenInActiveProjectsWithDeadlineBefore(START_OF_TODAY);
        verifyNoInteractions(alertRepository, eventPublisher, transactionManager);
    }

    @Test
    @DisplayName("A job due tomorrow gets a DEADLINE_SOON alert row and event")
    void checkDeadlines_shouldAlertDueTomorrow() {
        JobModel dueTomorrow = job(START_OF_TOMORROW);
        givenDueTomorrow(dueTomorrow);
        givenOverdue();
        givenNewAlert(dueTomorrow, DeadlineAlertKind.DEADLINE_SOON);

        int sent = deadlineAlertJob.checkDeadlines(NOW);

        assertThat(sent).isEqualTo(1);
        verify(alertRepository)
                .insertIfAbsent(dueTomorrow.getId(), DeadlineAlertKind.DEADLINE_SOON, START_OF_TOMORROW);
        verify(eventPublisher).publishEvent(new DeadlineAlertEvent(
                dueTomorrow.getId(), projectId, DeadlineAlertKind.DEADLINE_SOON, START_OF_TOMORROW));
        // The alert row and its event commit together, in their own transaction.
        verify(transactionManager).commit(any());
    }

    @Test
    @DisplayName("A job whose deadline day has passed gets an OVERDUE alert row and event")
    void checkDeadlines_shouldAlertOverdue() {
        Instant yesterday = Instant.parse("2026-11-09T00:00:00Z");
        JobModel overdue = job(yesterday);
        givenDueTomorrow();
        givenOverdue(overdue);
        givenNewAlert(overdue, DeadlineAlertKind.OVERDUE);

        int sent = deadlineAlertJob.checkDeadlines(NOW);

        assertThat(sent).isEqualTo(1);
        verify(eventPublisher).publishEvent(
                new DeadlineAlertEvent(overdue.getId(), projectId, DeadlineAlertKind.OVERDUE, yesterday));
    }

    @Test
    @DisplayName("A job whose alert row already exists (earlier or overlapping run) gets no event; others still do")
    void checkDeadlines_shouldSkipAlreadyAlerted() {
        Instant lastWeek = Instant.parse("2026-11-03T00:00:00Z");
        JobModel alerted = job(lastWeek);
        JobModel fresh = job(lastWeek);
        givenDueTomorrow();
        givenOverdue(alerted, fresh);
        // insertIfAbsent hits the existing row (ON CONFLICT DO NOTHING) and reports nothing inserted.
        when(alertRepository.insertIfAbsent(alerted.getId(), DeadlineAlertKind.OVERDUE, lastWeek)).thenReturn(false);
        givenNewAlert(fresh, DeadlineAlertKind.OVERDUE);

        int sent = deadlineAlertJob.checkDeadlines(NOW);

        assertThat(sent).isEqualTo(1);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        verify(eventPublisher).publishEvent(
                new DeadlineAlertEvent(fresh.getId(), projectId, DeadlineAlertKind.OVERDUE, lastWeek));
    }

    @Test
    @DisplayName("A moved deadline alerts again: the check is keyed on the current deadline value")
    void checkDeadlines_shouldAlertAgain_whenDeadlineMoved() {
        // Alerted earlier for an older deadline; the insert is keyed on the current one, which has no row yet.
        JobModel moved = job(START_OF_TOMORROW);
        givenDueTomorrow(moved);
        givenOverdue();
        givenNewAlert(moved, DeadlineAlertKind.DEADLINE_SOON);

        int sent = deadlineAlertJob.checkDeadlines(NOW);

        assertThat(sent).isEqualTo(1);
        verify(alertRepository).insertIfAbsent(moved.getId(), DeadlineAlertKind.DEADLINE_SOON, START_OF_TOMORROW);
        verify(eventPublisher).publishEvent(new DeadlineAlertEvent(
                moved.getId(), projectId, DeadlineAlertKind.DEADLINE_SOON, START_OF_TOMORROW));
    }

    @Test
    @DisplayName("A job that fails is logged and skipped; the next job is still alerted and committed")
    void checkDeadlines_shouldContinue_whenOneJobFails() {
        Instant lastWeek = Instant.parse("2026-11-03T00:00:00Z");
        JobModel failing = job(lastWeek);
        JobModel next = job(lastWeek);
        givenDueTomorrow();
        givenOverdue(failing, next);
        when(alertRepository.insertIfAbsent(failing.getId(), DeadlineAlertKind.OVERDUE, lastWeek))
                .thenThrow(new IllegalStateException("DB error"));
        givenNewAlert(next, DeadlineAlertKind.OVERDUE);

        int sent = deadlineAlertJob.checkDeadlines(NOW);

        assertThat(sent).isEqualTo(1);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        verify(eventPublisher).publishEvent(
                new DeadlineAlertEvent(next.getId(), projectId, DeadlineAlertKind.OVERDUE, lastWeek));
        // Each job has its own transaction: the failing one rolls back, the next one commits.
        verify(transactionManager).rollback(any());
        verify(transactionManager).commit(any());
    }

    @Test
    @DisplayName("A failure in the due-tomorrow pass doesn't stop the overdue pass")
    void checkDeadlines_shouldRunOverduePass_whenSoonJobFails() {
        JobModel failing = job(START_OF_TOMORROW);
        JobModel overdue = job(Instant.parse("2026-11-01T00:00:00Z"));
        givenDueTomorrow(failing);
        givenOverdue(overdue);
        when(alertRepository.insertIfAbsent(failing.getId(), DeadlineAlertKind.DEADLINE_SOON, START_OF_TOMORROW))
                .thenThrow(new IllegalStateException("DB error"));
        givenNewAlert(overdue, DeadlineAlertKind.OVERDUE);

        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isEqualTo(1);
        verify(eventPublisher).publishEvent(new DeadlineAlertEvent(
                overdue.getId(), projectId, DeadlineAlertKind.OVERDUE, overdue.getDeadline()));
    }

    @Test
    @DisplayName("Due-tomorrow and overdue jobs are alerted in the same run")
    void checkDeadlines_shouldAlertBothKinds() {
        JobModel dueTomorrow = job(START_OF_TOMORROW);
        JobModel overdue = job(Instant.parse("2026-11-01T00:00:00Z"));
        givenDueTomorrow(dueTomorrow);
        givenOverdue(overdue);
        givenNewAlert(dueTomorrow, DeadlineAlertKind.DEADLINE_SOON);
        givenNewAlert(overdue, DeadlineAlertKind.OVERDUE);

        int sent = deadlineAlertJob.checkDeadlines(NOW);

        assertThat(sent).isEqualTo(2);
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
        verify(transactionManager, times(2)).commit(any());
    }

    @Test
    @DisplayName("run() checks deadlines relative to the current time")
    void run_shouldCheckDeadlinesNow() {
        when(jobRepository.findOpenInActiveProjectsWithDeadlineBetween(any(), any())).thenReturn(List.of());
        when(jobRepository.findOpenInActiveProjectsWithDeadlineBefore(any())).thenReturn(List.of());

        deadlineAlertJob.run();

        verify(jobRepository).findOpenInActiveProjectsWithDeadlineBetween(any(), any());
        verify(jobRepository).findOpenInActiveProjectsWithDeadlineBefore(any());
    }
}
