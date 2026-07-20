package com.opsclear.scheduler;

import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleMissedRunRepository;
import com.opsclear.service.ScheduleJobCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SchedulerPoller")
class SchedulerPollerTest {

    @Mock private RecurringScheduleRepository scheduleRepository;
    @Mock private ScheduleMissedRunRepository missedRunRepository;
    @Mock private ScheduleJobCreator scheduleJobCreator;

    private SchedulerPoller poller;

    private UUID projectId;
    private UUID templateId;
    private UUID createdBy;

    @BeforeEach
    void setUp() {
        poller = new SchedulerPoller(scheduleRepository, missedRunRepository, scheduleJobCreator);

        projectId  = UUID.randomUUID();
        templateId = UUID.randomUUID();
        createdBy  = UUID.randomUUID();

        when(scheduleJobCreator.createJob(any(), any(), any(), any()))
                .thenReturn(JobModel.builder().id(UUID.randomUUID()).title("Job")
                        .projectId(projectId).status(JobStatus.NEW).build());
    }

    // -------------------------------------------------------------------------
    // processSchedule — normal run
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("processSchedule — delegates to ScheduleJobCreator on normal run")
    void processSchedule_shouldDelegateToCreator_onNormalRun() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));

        poller.processSchedule(schedule, now);

        verify(scheduleJobCreator).createJob(eq(schedule), any(Instant.class), any(), eq(createdBy));
        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), anyInt());
        verify(missedRunRepository, never()).insert(any(), any());
    }

    @Test
    @DisplayName("processSchedule — passes previousRunAt as scheduledFor to creator")
    void processSchedule_shouldPassPreviousRunAt_asScheduledFor() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        Instant previousRunAt = now.minusSeconds(3600); // 09:00
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", previousRunAt);

        poller.processSchedule(schedule, now);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(scheduleJobCreator).createJob(any(), captor.capture(), any(), any());
        assertThat(captor.getValue()).isEqualTo(previousRunAt);
    }

    @Test
    @DisplayName("processSchedule — increments rotation index in updateAfterRun")
    void processSchedule_shouldIncrementRotationIndex_onNormalRun() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));
        schedule.setCurrentRotationIndex(3);

        poller.processSchedule(schedule, now);

        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), eq(4));
    }

    // -------------------------------------------------------------------------
    // processSchedule — missed run
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("processSchedule — records missed run when nextOccurrence is in the past")
    void processSchedule_shouldRecordMissedRun_whenNextOccurrenceInPast() {
        // nextRunAt = 09:00, now = 14:00, cron fires at noon → 12:00 is before 14:00
        Instant now = Instant.parse("2026-05-11T14:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC",
                Instant.parse("2026-05-11T09:00:00Z"));

        poller.processSchedule(schedule, now);

        verify(missedRunRepository).insert(eq(schedule.getId()), eq(Instant.parse("2026-05-11T12:00:00Z")));
        verify(scheduleJobCreator, never()).createJob(any(), any(), any(), any());
        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), anyInt());
    }

    @Test
    @DisplayName("processSchedule — records missed run when nextOccurrence equals now exactly")
    void processSchedule_shouldRecordMissedRun_whenNextOccurrenceEqualsNow() {
        Instant now = Instant.parse("2026-05-12T12:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC",
                Instant.parse("2026-05-12T09:00:00Z"));

        poller.processSchedule(schedule, now);

        verify(missedRunRepository).insert(eq(schedule.getId()), eq(now));
        verify(scheduleJobCreator, never()).createJob(any(), any(), any(), any());
        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), anyInt());
    }

    @Test
    @DisplayName("processSchedule — advances next_run_at to first future occurrence on missed run")
    void processSchedule_shouldAdvanceToFutureOccurrence_onMissedRun() {
        // nextRunAt = 09:00, now = 13:00, cron fires at noon → missed tick at 12:00, next is 12:00+1d
        Instant now = Instant.parse("2026-05-11T13:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC",
                Instant.parse("2026-05-11T09:00:00Z"));

        poller.processSchedule(schedule, now);

        // next after 12:00 is 12:00 next day (2026-05-12)
        Instant expectedNextRun = Instant.parse("2026-05-12T12:00:00Z");
        ArgumentCaptor<Instant> nextRunCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(scheduleRepository).updateAfterRun(
                eq(schedule.getId()), nextRunCaptor.capture(), any(), anyInt());
        assertThat(nextRunCaptor.getValue()).isEqualTo(expectedNextRun);
    }

    // -------------------------------------------------------------------------
    // processSchedule — skip conditions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("processSchedule — skips when timezone is invalid")
    void processSchedule_shouldSkip_whenTimezoneInvalid() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "Not/ATimezone", now.minusSeconds(3600));

        poller.processSchedule(schedule, now);

        verify(scheduleJobCreator, never()).createJob(any(), any(), any(), any());
        verify(scheduleRepository, never()).updateAfterRun(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("processSchedule — skips when cron has no further occurrences")
    void processSchedule_shouldSkip_whenCronHasNoNextOccurrence() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        // Feb 31 — parse succeeds but .next() returns null
        RecurringSchedulesRecord schedule = buildSchedule("0 0 9 31 2 *", "UTC", now.minusSeconds(3600));

        poller.processSchedule(schedule, now);

        verify(scheduleJobCreator, never()).createJob(any(), any(), any(), any());
        verify(scheduleRepository, never()).updateAfterRun(any(), any(), any(), anyInt());
    }

    // -------------------------------------------------------------------------
    // tick
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tick — does nothing when no schedules are due")
    void tick_shouldDoNothing_whenNoSchedulesDue() {
        when(scheduleRepository.findDue(any())).thenReturn(List.of());

        poller.tick();

        verify(scheduleRepository).findDue(any());
        verify(scheduleJobCreator, never()).createJob(any(), any(), any(), any());
    }

    @Test
    @DisplayName("tick — advances schedule when one is due")
    void tick_shouldAdvanceSchedule_whenScheduleIsDue() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 9 * * *", "UTC",
                Instant.parse("2026-01-01T09:00:00Z"));
        when(scheduleRepository.findDue(any())).thenReturn(List.of(schedule));

        poller.tick();

        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), anyInt());
    }

    @Test
    @DisplayName("tick — records missed run for a due schedule in the past")
    void tick_shouldRecordMissedRun_whenScheduleWasDueInPast() {
        // Fixed past date guarantees the missed-run path regardless of what time the test runs.
        // next("0 0 9 * * *", 2026-01-01T09:00) = 2026-01-02T09:00 — already in the past.
        RecurringSchedulesRecord schedule = buildSchedule("0 0 9 * * *", "UTC",
                Instant.parse("2026-01-01T09:00:00Z"));
        when(scheduleRepository.findDue(any())).thenReturn(List.of(schedule));

        poller.tick();

        verify(missedRunRepository, atLeastOnce()).insert(eq(schedule.getId()), any(Instant.class));
        verify(scheduleJobCreator, never()).createJob(any(), any(), any(), any());
    }

    @Test
    @DisplayName("tick — continues processing remaining schedules when one throws")
    void tick_shouldContinue_whenOneScheduleThrows() {
        RecurringSchedulesRecord schedule1 = buildSchedule("0 0 9 * * *", "UTC",
                Instant.parse("2026-01-01T09:00:00Z"));
        RecurringSchedulesRecord schedule2 = buildSchedule("0 0 9 * * *", "UTC",
                Instant.parse("2026-01-01T09:00:00Z"));

        when(scheduleRepository.findDue(any())).thenReturn(List.of(schedule1, schedule2));
        doThrow(new RuntimeException("DB error"))
                .when(scheduleRepository).updateAfterRun(eq(schedule1.getId()), any(), any(), anyInt());

        poller.tick();

        verify(scheduleRepository).updateAfterRun(eq(schedule2.getId()), any(), any(), anyInt());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private RecurringSchedulesRecord buildSchedule(String cron, String timezone, Instant nextRunAt) {
        RecurringSchedulesRecord r = new RecurringSchedulesRecord();
        r.setId(UUID.randomUUID());
        r.setProjectId(projectId);
        r.setTemplateId(templateId);
        r.setName("Test Schedule");
        r.setCronExpression(cron);
        r.setTimezone(timezone);
        r.setCurrentRotationIndex(0);
        r.setNextRunAt(nextRunAt.atOffset(ZoneOffset.UTC));
        r.setCreatedBy(createdBy);
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return r;
    }
}
