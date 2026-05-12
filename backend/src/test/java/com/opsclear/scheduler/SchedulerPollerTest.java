package com.opsclear.scheduler;

import com.opsclear.dto.ScheduleAssigneeResponse;
import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleAssigneeRepository;
import com.opsclear.service.FriendlyIdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulerPoller")
class SchedulerPollerTest {

    @Mock private RecurringScheduleRepository scheduleRepository;
    @Mock private ScheduleAssigneeRepository assigneeRepository;
    @Mock private JobRepository jobRepository;
    @Mock private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Mock private JobTemplateRepository jobTemplateRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private FriendlyIdService friendlyIdService;

    private SchedulerPoller poller;

    private UUID projectId;
    private UUID templateId;
    private UUID createdBy;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        poller = new SchedulerPoller(
                scheduleRepository, assigneeRepository, jobRepository,
                jobStatusHistoryRepository, jobTemplateRepository,
                organisationRepository, friendlyIdService);

        projectId  = UUID.randomUUID();
        templateId = UUID.randomUUID();
        createdBy  = UUID.randomUUID();
        orgId      = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // processSchedule — normal run
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("processSchedule — creates job from template on normal run")
    void processSchedule_shouldCreateJob_onNormalRun() {
        // nextRunAt = 09:00, now = 10:00, cron fires at noon → nextOccurrence (12:00) is in the future
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));

        JobTemplateModel template = buildTemplate("Deploy checklist", null);
        JobModel savedJob = JobModel.builder().id(UUID.randomUUID()).title("Deploy checklist")
                .projectId(projectId).status(com.opsclear.model.JobStatus.NEW).build();

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(assigneeRepository.findByScheduleId(schedule.getId())).thenReturn(List.of());
        when(organisationRepository.findByProject(projectId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.JOB)).thenReturn("JOB-999");
        when(jobRepository.save(any())).thenReturn(savedJob);

        poller.processSchedule(schedule, now);

        verify(jobRepository).save(any());
        verify(jobStatusHistoryRepository).insert(eq(savedJob.getId()), eq(null), eq("NEW"),
                eq(createdBy), eq(null));
        verify(jobTemplateRepository).incrementOccurrenceCount(templateId);
        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), anyInt());
    }

    @Test
    @DisplayName("processSchedule — sets sourceScheduleId on created job")
    void processSchedule_shouldSetSourceScheduleId_onCreatedJob() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", null)));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(organisationRepository.findByProject(any())).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .projectId(m.getProjectId()).status(m.getStatus())
                    .sourceScheduleId(m.getSourceScheduleId()).build();
        });

        poller.processSchedule(schedule, now);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceScheduleId()).isEqualTo(schedule.getId());
    }

    @Test
    @DisplayName("processSchedule — applies deadline offset when template has deadlineOffsetDays")
    void processSchedule_shouldApplyDeadlineOffset_whenTemplateHasOffset() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));

        JobTemplateModel template = buildTemplate("T", 3);
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(organisationRepository.findByProject(any())).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .deadline(m.getDeadline()).projectId(m.getProjectId())
                    .status(m.getStatus()).build();
        });

        poller.processSchedule(schedule, now);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        // previousRunAt = 09:00 on 2026-05-11 UTC → scheduledForDate = 2026-05-11, +3 days = 2026-05-14
        LocalDate expectedDeadlineDate = LocalDate.parse("2026-05-14");
        Instant expectedDeadline = expectedDeadlineDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(captor.getValue().getDeadline()).isEqualTo(expectedDeadline);
    }

    @Test
    @DisplayName("processSchedule — deadline is null when template has no offset")
    void processSchedule_shouldSetNullDeadline_whenNoOffset() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", null)));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(organisationRepository.findByProject(any())).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenReturn(
                JobModel.builder().id(UUID.randomUUID()).title("T")
                        .projectId(projectId).status(com.opsclear.model.JobStatus.NEW).build());

        poller.processSchedule(schedule, now);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getDeadline()).isNull();
    }

    @Test
    @DisplayName("processSchedule — picks round-robin assignee by rotation index")
    void processSchedule_shouldPickAssignee_byRotationIndex() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));
        schedule.setCurrentRotationIndex(1); // second in list

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        ScheduleAssigneeResponse a1 = new ScheduleAssigneeResponse(user1, "Alice", 0);
        ScheduleAssigneeResponse a2 = new ScheduleAssigneeResponse(user2, "Bob", 1);

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", null)));
        when(assigneeRepository.findByScheduleId(schedule.getId())).thenReturn(List.of(a1, a2));
        when(organisationRepository.findByProject(any())).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .assignedTo(m.getAssignedTo()).projectId(m.getProjectId())
                    .status(m.getStatus()).build();
        });

        poller.processSchedule(schedule, now);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedTo()).isEqualTo(user2);
        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), eq(2));
    }

    @Test
    @DisplayName("processSchedule — job has null assignedTo when no assignees configured")
    void processSchedule_shouldSetNullAssignedTo_whenNoAssignees() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", null)));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(organisationRepository.findByProject(any())).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenReturn(
                JobModel.builder().id(UUID.randomUUID()).title("T")
                        .projectId(projectId).status(com.opsclear.model.JobStatus.NEW).build());

        poller.processSchedule(schedule, now);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedTo()).isNull();
    }

    // -------------------------------------------------------------------------
    // processSchedule — missed run
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("processSchedule — records missed run when nextOccurrence equals now exactly")
    void processSchedule_shouldRecordMissedRun_whenNextOccurrenceEqualsNow() {
        // now = noon exactly; cron fires at noon from 09:00 → nextOccurrence == now → equals(now) = TRUE
        Instant now = Instant.parse("2026-05-12T12:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC",
                Instant.parse("2026-05-12T09:00:00Z"));

        poller.processSchedule(schedule, now);

        verify(jobRepository, never()).save(any());
        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), anyInt());
    }

    @Test
    @DisplayName("processSchedule — records missed run and advances next_run_at without creating job")
    void processSchedule_shouldAdvanceWithoutJob_onMissedRun() {
        // nextRunAt = 09:00, now = 14:00, cron fires at noon → nextOccurrence (12:00) is in the past
        Instant now = Instant.parse("2026-05-11T14:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(18000)); // 09:00

        poller.processSchedule(schedule, now);

        verify(jobRepository, never()).save(any());
        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), anyInt());
    }

    // -------------------------------------------------------------------------
    // processSchedule — skip conditions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("processSchedule — skips when template is missing")
    void processSchedule_shouldSkip_whenTemplateNotFound() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.empty());

        poller.processSchedule(schedule, now);

        verify(jobRepository, never()).save(any());
        verify(scheduleRepository, never()).updateAfterRun(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("processSchedule — skips when timezone is invalid")
    void processSchedule_shouldSkip_whenTimezoneInvalid() {
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "Not/ATimezone", now.minusSeconds(3600));

        poller.processSchedule(schedule, now);

        verify(jobRepository, never()).save(any());
        verify(scheduleRepository, never()).updateAfterRun(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("processSchedule — skips when cron has no further occurrences")
    void processSchedule_shouldSkip_whenCronHasNoNextOccurrence() {
        // Feb 31 cron — parse succeeds but .next() returns null from the previousRunAt date
        Instant now = Instant.parse("2026-05-11T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 9 31 2 *", "UTC", now.minusSeconds(3600));

        poller.processSchedule(schedule, now);

        verify(jobRepository, never()).save(any());
        verify(scheduleRepository, never()).updateAfterRun(any(), any(), any(), anyInt());
    }

    // -------------------------------------------------------------------------
    // resolveWildcards
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resolveWildcards — replaces all known wildcards")
    void resolveWildcards_shouldReplaceKnownWildcards() {
        Map<String, String> vars = Map.of(
                "assignee", "Alice",
                "date", "2026-05-11",
                "month", "May",
                "year", "2026",
                "occurrence", "5",
                "creator", "System"
        );
        String result = poller.resolveWildcards(
                "{{assignee}} — {{month}} {{year}} run #{{occurrence}} by {{creator}} on {{date}}", vars);
        assertThat(result).isEqualTo("Alice — May 2026 run #5 by System on 2026-05-11");
    }

    @Test
    @DisplayName("resolveWildcards — leaves unknown wildcards as literal text")
    void resolveWildcards_shouldLeaveLiteral_forUnknownWildcard() {
        String result = poller.resolveWildcards("Hello {{client}} and {{unknown}}", Map.of());
        assertThat(result).isEqualTo("Hello {{client}} and {{unknown}}");
    }

    @Test
    @DisplayName("resolveWildcards — returns null when text is null")
    void resolveWildcards_shouldReturnNull_whenTextIsNull() {
        assertThat(poller.resolveWildcards(null, Map.of())).isNull();
    }

    @Test
    @DisplayName("resolveWildcards — returns text unchanged when no wildcards present")
    void resolveWildcards_shouldReturnUnchanged_whenNoWildcards() {
        assertThat(poller.resolveWildcards("Plain text", Map.of("key", "value")))
                .isEqualTo("Plain text");
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
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("tick — advances schedule when one is due")
    void tick_shouldAdvanceSchedule_whenScheduleIsDue() {
        // nextRunAt far in past → missed-run path (no template/org stubs needed)
        RecurringSchedulesRecord schedule = buildSchedule("0 0 9 * * *", "UTC",
                Instant.parse("2026-01-01T09:00:00Z"));
        when(scheduleRepository.findDue(any())).thenReturn(List.of(schedule));

        poller.tick();

        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), anyInt());
        verify(jobRepository, never()).save(any());
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

        // schedule2 still processed despite schedule1 throwing
        verify(scheduleRepository).updateAfterRun(eq(schedule2.getId()), any(), any(), anyInt());
    }

    // -------------------------------------------------------------------------
    // processSchedule — additional branches
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("processSchedule — handles null currentRotationIndex in missed-run path")
    void processSchedule_shouldHandleNullRotationIndex_inMissedRun() {
        Instant now = Instant.parse("2026-05-12T14:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(18000));
        schedule.setCurrentRotationIndex(null);

        poller.processSchedule(schedule, now);

        // null → treated as 0, so newIndex = 0 + 1 = 1
        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), eq(1));
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("processSchedule — falls back to template name when title is null, resolves description")
    void processSchedule_shouldFallbackToName_whenTitleNullAndDescriptionPresent() {
        Instant now = Instant.parse("2026-05-12T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));
        schedule.setCurrentRotationIndex(null); // also covers null rotationIndex in normal-run path

        JobTemplateModel template = JobTemplateModel.builder()
                .id(templateId).projectId(projectId)
                .name("Fallback Name")
                // no title — forces fallback to name
                .description("Report for {{date}}")
                .assigneeMode("NONE").occurrenceCount(0)
                .createdBy(createdBy).build();

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(organisationRepository.findByProject(any())).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .description(m.getDescription()).projectId(m.getProjectId())
                    .status(m.getStatus()).build();
        });

        poller.processSchedule(schedule, now);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Fallback Name");
        assertThat(captor.getValue().getDescription()).contains("Report for ");
        // newIndex = (null→0) + 1 = 1
        verify(scheduleRepository).updateAfterRun(eq(schedule.getId()), any(), any(), eq(1));
    }

    @Test
    @DisplayName("processSchedule — uses template priority when non-null")
    void processSchedule_shouldUseTemplatePriority_whenNotNull() {
        Instant now = Instant.parse("2026-05-12T10:00:00Z");
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC", now.minusSeconds(3600));

        JobTemplateModel template = JobTemplateModel.builder()
                .id(templateId).projectId(projectId).name("High Priority Task")
                .title("High Priority Task").assigneeMode("NONE").occurrenceCount(0)
                .priority("HIGH").deadlineOffsetDays(null).createdBy(createdBy).build();

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(organisationRepository.findByProject(any())).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .priority(m.getPriority()).projectId(m.getProjectId())
                    .status(m.getStatus()).build();
        });

        poller.processSchedule(schedule, now);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(com.opsclear.model.JobPriority.HIGH);
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

    private JobTemplateModel buildTemplate(String name, Integer deadlineOffsetDays) {
        return JobTemplateModel.builder()
                .id(templateId)
                .projectId(projectId)
                .name(name)
                .title(name)
                .assigneeMode("NONE")
                .occurrenceCount(0)
                .deadlineOffsetDays(deadlineOffsetDays)
                .createdBy(createdBy)
                .build();
    }
}
