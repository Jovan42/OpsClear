package com.opsclear.service;

import com.opsclear.dto.ScheduleAssigneeResponse;
import com.opsclear.exception.NotFoundException;
import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.ScheduleAssigneeRepository;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ScheduleJobCreator")
class ScheduleJobCreatorTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Mock private JobTemplateRepository jobTemplateRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ScheduleAssigneeRepository assigneeRepository;
    @Mock private FriendlyIdService friendlyIdService;

    private ScheduleJobCreator creator;

    private UUID projectId;
    private UUID templateId;
    private UUID createdBy;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        creator = new ScheduleJobCreator(
                jobRepository, jobStatusHistoryRepository, jobTemplateRepository,
                organisationRepository, projectRepository, assigneeRepository, friendlyIdService);

        projectId  = UUID.randomUUID();
        templateId = UUID.randomUUID();
        createdBy  = UUID.randomUUID();
        orgId      = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(any()))
                .thenReturn(Optional.of(ProjectModel.builder().name("Test Project").build()));
        when(organisationRepository.findByProject(any())).thenReturn(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // createJob
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createJob — creates job from template on normal run")
    void createJob_shouldCreateJob_fromTemplate() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");
        Instant scheduledFor = Instant.parse("2026-05-11T09:00:00Z");

        JobTemplateModel template = buildTemplate("Deploy checklist", null);
        JobModel savedJob = JobModel.builder().id(UUID.randomUUID()).title("Deploy checklist")
                .projectId(projectId).status(JobStatus.NEW).build();

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(assigneeRepository.findByScheduleId(schedule.getId())).thenReturn(List.of());
        when(jobRepository.save(any())).thenReturn(savedJob);

        creator.createJob(schedule, scheduledFor, ZoneId.of("UTC"), createdBy);

        verify(jobRepository).save(any());
        verify(jobStatusHistoryRepository).insert(eq(savedJob.getId()), eq(null), eq("NEW"),
                eq(createdBy), eq(null));
        verify(jobTemplateRepository).incrementOccurrenceCount(templateId);
    }

    @Test
    @DisplayName("createJob — throws NotFoundException when template is missing")
    void createJob_shouldThrow_whenTemplateNotFound() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creator.createJob(
                schedule, Instant.parse("2026-05-11T09:00:00Z"), ZoneId.of("UTC"), createdBy))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Job template not found");
    }

    @Test
    @DisplayName("createJob — sets sourceScheduleId on created job")
    void createJob_shouldSetSourceScheduleId() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", null)));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .projectId(m.getProjectId()).status(m.getStatus())
                    .sourceScheduleId(m.getSourceScheduleId()).build();
        });

        creator.createJob(schedule, Instant.parse("2026-05-11T09:00:00Z"), ZoneId.of("UTC"), createdBy);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceScheduleId()).isEqualTo(schedule.getId());
    }

    @Test
    @DisplayName("createJob — applies deadline offset when template has deadlineOffsetDays")
    void createJob_shouldApplyDeadlineOffset_whenTemplateHasOffset() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");
        Instant scheduledFor = Instant.parse("2026-05-11T09:00:00Z");

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", 3)));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .deadline(m.getDeadline()).projectId(m.getProjectId())
                    .status(m.getStatus()).build();
        });

        creator.createJob(schedule, scheduledFor, ZoneId.of("UTC"), createdBy);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        // scheduledFor = 2026-05-11 UTC, +3 days = 2026-05-14
        Instant expectedDeadline = LocalDate.parse("2026-05-14").atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(captor.getValue().getDeadline()).isEqualTo(expectedDeadline);
    }

    @Test
    @DisplayName("createJob — deadline is null when template has no offset")
    void createJob_shouldSetNullDeadline_whenNoOffset() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", null)));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(jobRepository.save(any())).thenReturn(
                JobModel.builder().id(UUID.randomUUID()).title("T")
                        .projectId(projectId).status(JobStatus.NEW).build());

        creator.createJob(schedule, Instant.parse("2026-05-11T09:00:00Z"), ZoneId.of("UTC"), createdBy);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getDeadline()).isNull();
    }

    @Test
    @DisplayName("createJob — picks assignee by rotation index")
    void createJob_shouldPickAssignee_byRotationIndex() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");
        schedule.setCurrentRotationIndex(1);

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        ScheduleAssigneeResponse a1 = new ScheduleAssigneeResponse(user1, "Alice", 0);
        ScheduleAssigneeResponse a2 = new ScheduleAssigneeResponse(user2, "Bob", 1);

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", null)));
        when(assigneeRepository.findByScheduleId(schedule.getId())).thenReturn(List.of(a1, a2));
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .assignedTo(m.getAssignedTo()).projectId(m.getProjectId())
                    .status(m.getStatus()).build();
        });

        creator.createJob(schedule, Instant.parse("2026-05-11T09:00:00Z"), ZoneId.of("UTC"), createdBy);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedTo()).isEqualTo(user2);
    }

    @Test
    @DisplayName("createJob — null assignedTo when no assignees configured")
    void createJob_shouldSetNullAssignedTo_whenNoAssignees() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", null)));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(jobRepository.save(any())).thenReturn(
                JobModel.builder().id(UUID.randomUUID()).title("T")
                        .projectId(projectId).status(JobStatus.NEW).build());

        creator.createJob(schedule, Instant.parse("2026-05-11T09:00:00Z"), ZoneId.of("UTC"), createdBy);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedTo()).isNull();
    }

    @Test
    @DisplayName("createJob — falls back to template name when title is null")
    void createJob_shouldFallbackToName_whenTitleNull() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");

        JobTemplateModel template = JobTemplateModel.builder()
                .id(templateId).projectId(projectId)
                .name("Fallback Name")
                .description("Report for {{date}}")
                .assigneeMode("NONE").occurrenceCount(0)
                .createdBy(createdBy).build();

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .description(m.getDescription()).projectId(m.getProjectId())
                    .status(m.getStatus()).build();
        });

        creator.createJob(schedule, Instant.parse("2026-05-12T09:00:00Z"), ZoneId.of("UTC"), createdBy);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Fallback Name");
        assertThat(captor.getValue().getDescription()).contains("Report for ");
    }

    @Test
    @DisplayName("createJob — uses template priority when non-null")
    void createJob_shouldUseTemplatePriority_whenNotNull() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");

        JobTemplateModel template = JobTemplateModel.builder()
                .id(templateId).projectId(projectId).name("Task").title("Task")
                .assigneeMode("NONE").occurrenceCount(0).priority("HIGH")
                .createdBy(createdBy).build();

        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .priority(m.getPriority()).projectId(m.getProjectId())
                    .status(m.getStatus()).build();
        });

        creator.createJob(schedule, Instant.parse("2026-05-12T09:00:00Z"), ZoneId.of("UTC"), createdBy);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(JobPriority.HIGH);
    }

    @Test
    @DisplayName("createJob — generates friendly ID when organisation exists")
    void createJob_shouldGenerateFriendlyId_whenOrgExists() {
        RecurringSchedulesRecord schedule = buildSchedule("0 0 12 * * *", "UTC");
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId))
                .thenReturn(Optional.of(buildTemplate("T", null)));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(organisationRepository.findByProject(any()))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.JOB)).thenReturn("JOB-999");
        when(jobRepository.save(any())).thenAnswer(inv -> {
            JobModel m = inv.getArgument(0);
            return JobModel.builder().id(UUID.randomUUID()).title(m.getTitle())
                    .friendlyId(m.getFriendlyId()).projectId(m.getProjectId())
                    .status(m.getStatus()).build();
        });

        creator.createJob(schedule, Instant.parse("2026-05-11T09:00:00Z"), ZoneId.of("UTC"), createdBy);

        ArgumentCaptor<JobModel> captor = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getFriendlyId()).isEqualTo("JOB-999");
    }

    // -------------------------------------------------------------------------
    // resolve (wildcard substitution)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resolve — replaces all known wildcards")
    void resolve_shouldReplaceKnownWildcards() {
        Map<String, String> vars = Map.of(
                "assignee", "Alice",
                "date", "2026-05-11",
                "month", "May",
                "year", "2026",
                "occurrence", "5",
                "creator", "System"
        );
        String result = creator.resolve(
                "{{assignee}} — {{month}} {{year}} run #{{occurrence}} by {{creator}} on {{date}}", vars);
        assertThat(result).isEqualTo("Alice — May 2026 run #5 by System on 2026-05-11");
    }

    @Test
    @DisplayName("resolve — leaves unknown wildcards as literal text")
    void resolve_shouldLeaveLiteral_forUnknownWildcard() {
        assertThat(creator.resolve("Hello {{client}} and {{unknown}}", Map.of()))
                .isEqualTo("Hello {{client}} and {{unknown}}");
    }

    @Test
    @DisplayName("resolve — returns null when text is null")
    void resolve_shouldReturnNull_whenTextIsNull() {
        assertThat(creator.resolve(null, Map.of())).isNull();
    }

    @Test
    @DisplayName("resolve — returns text unchanged when no wildcards present")
    void resolve_shouldReturnUnchanged_whenNoWildcards() {
        assertThat(creator.resolve("Plain text", Map.of("key", "value"))).isEqualTo("Plain text");
    }

    // -------------------------------------------------------------------------
    // buildWildcardVars
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("buildWildcardVars — includes all expected keys")
    void buildWildcardVars_shouldIncludeAllKeys() {
        ScheduleAssigneeResponse assignee = new ScheduleAssigneeResponse(UUID.randomUUID(), "Alice", 0);
        LocalDate date = LocalDate.parse("2026-05-11");

        Map<String, String> vars = creator.buildWildcardVars(assignee, date, 4, "My Project");

        assertThat(vars).containsKeys("assignee", "date", "month", "year", "occurrence", "creator", "project");
        assertThat(vars.get("assignee")).isEqualTo("Alice");
        assertThat(vars.get("date")).isEqualTo("2026-05-11");
        assertThat(vars.get("month")).isEqualTo("May");
        assertThat(vars.get("year")).isEqualTo("2026");
        assertThat(vars.get("occurrence")).isEqualTo("5"); // occurrenceCount+1
        assertThat(vars.get("project")).isEqualTo("My Project");
    }

    @Test
    @DisplayName("buildWildcardVars — assignee is empty string when null")
    void buildWildcardVars_shouldUseEmptyString_whenAssigneeNull() {
        Map<String, String> vars = creator.buildWildcardVars(null, LocalDate.now(), 0, "P");
        assertThat(vars.get("assignee")).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private RecurringSchedulesRecord buildSchedule(String cron, String timezone) {
        RecurringSchedulesRecord r = new RecurringSchedulesRecord();
        r.setId(UUID.randomUUID());
        r.setProjectId(projectId);
        r.setTemplateId(templateId);
        r.setName("Test Schedule");
        r.setCronExpression(cron);
        r.setTimezone(timezone);
        r.setCurrentRotationIndex(0);
        r.setNextRunAt(Instant.parse("2026-05-11T09:00:00Z").atOffset(ZoneOffset.UTC));
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
