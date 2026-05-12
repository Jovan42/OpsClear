package com.opsclear.service;

import com.opsclear.dto.CreateScheduleRequest;
import com.opsclear.dto.PauseScheduleRequest;
import com.opsclear.dto.RecurringScheduleResponse;
import com.opsclear.dto.UpdateScheduleRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleAssigneeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecurringScheduleService")
class RecurringScheduleServiceTest {

    @Mock private RecurringScheduleRepository scheduleRepository;
    @Mock private ScheduleAssigneeRepository assigneeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private JobTemplateRepository jobTemplateRepository;

    private RecurringScheduleService service;

    private UUID projectId;
    private UUID ownerId;
    private UUID memberId;
    private UUID templateId;
    private ProjectModel project;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel memberMembership;
    private JobTemplateModel template;

    // Valid hourly cron (Spring format: second minute hour day month weekday)
    private static final String VALID_CRON = "0 0 * * * *";
    private static final String VALID_TZ = "UTC";

    @BeforeEach
    void setUp() {
        service = new RecurringScheduleService(
                scheduleRepository, assigneeRepository,
                projectRepository, projectMemberRepository, jobTemplateRepository);

        projectId  = UUID.randomUUID();
        ownerId    = UUID.randomUUID();
        memberId   = UUID.randomUUID();
        templateId = UUID.randomUUID();

        project = ProjectModel.builder().id(projectId).name("Test Project").ownerId(ownerId).build();
        ownerMembership  = ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();
        memberMembership = ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();
        template = JobTemplateModel.builder()
                .id(templateId).projectId(projectId).name("Weekly Report").assigneeMode("NONE").build();
    }

    // --- create ---

    @Test
    @DisplayName("create — owner creates schedule with valid cron and no assignees")
    void create_shouldCreateSchedule_forOwner() {
        CreateScheduleRequest req = new CreateScheduleRequest();
        req.setName("Weekly Job");
        req.setTemplateId(templateId);
        req.setCronExpression(VALID_CRON);
        req.setTimezone(VALID_TZ);

        RecurringSchedulesRecord saved = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(scheduleRepository.insert(any())).thenReturn(saved);
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        RecurringScheduleResponse result = service.create(projectId, req, ownerId);

        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(projectId);
        verify(scheduleRepository).insert(any());
    }

    @Test
    @DisplayName("create — throws BadRequestException for invalid cron expression")
    void create_shouldThrow_whenCronIsInvalid() {
        CreateScheduleRequest req = new CreateScheduleRequest();
        req.setName("Bad Schedule");
        req.setTemplateId(templateId);
        req.setCronExpression("not-a-cron");
        req.setTimezone(VALID_TZ);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.create(projectId, req, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid cron expression");
    }

    @Test
    @DisplayName("create — throws BadRequestException when cron interval is less than 1 hour")
    void create_shouldThrow_whenCronIntervalTooShort() {
        CreateScheduleRequest req = new CreateScheduleRequest();
        req.setName("Sub-hourly");
        req.setTemplateId(templateId);
        req.setCronExpression("0 * * * * *"); // every minute
        req.setTimezone(VALID_TZ);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.create(projectId, req, ownerId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cron expression must have a minimum interval of 1 hour");
    }

    @Test
    @DisplayName("create — member receives ForbiddenException")
    void create_shouldThrow_whenMemberRole() {
        CreateScheduleRequest req = new CreateScheduleRequest();
        req.setName("S1");
        req.setTemplateId(templateId);
        req.setCronExpression(VALID_CRON);
        req.setTimezone(VALID_TZ);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> service.create(projectId, req, memberId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("create — throws NotFoundException when project not found")
    void create_shouldThrow_whenProjectNotFound() {
        CreateScheduleRequest req = new CreateScheduleRequest();
        req.setName("S1");
        req.setTemplateId(templateId);
        req.setCronExpression(VALID_CRON);
        req.setTimezone(VALID_TZ);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(projectId, req, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("create — sets pausedUntil and expiresAt when provided")
    void create_shouldSetPausedUntilAndExpiresAt_whenProvided() {
        Instant pausedUntil = Instant.parse("2030-06-01T00:00:00Z");
        Instant expiresAt   = Instant.parse("2031-01-01T00:00:00Z");

        CreateScheduleRequest req = new CreateScheduleRequest();
        req.setName("Timed");
        req.setTemplateId(templateId);
        req.setCronExpression(VALID_CRON);
        req.setTimezone(VALID_TZ);
        req.setPausedUntil(pausedUntil);
        req.setExpiresAt(expiresAt);

        RecurringSchedulesRecord saved = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(scheduleRepository.insert(any())).thenReturn(saved);
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());

        service.create(projectId, req, ownerId);

        ArgumentCaptor<RecurringSchedulesRecord> captor = ArgumentCaptor.forClass(RecurringSchedulesRecord.class);
        verify(scheduleRepository).insert(captor.capture());
        assertThat(captor.getValue().getPausedUntil().toInstant()).isEqualTo(pausedUntil);
        assertThat(captor.getValue().getExpiresAt().toInstant()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("create — calls replaceForSchedule when assigneeIds provided")
    void create_shouldReplaceAssignees_whenAssigneeIdsProvided() {
        UUID assigneeId = UUID.randomUUID();

        CreateScheduleRequest req = new CreateScheduleRequest();
        req.setName("With Assignees");
        req.setTemplateId(templateId);
        req.setCronExpression(VALID_CRON);
        req.setTimezone(VALID_TZ);
        req.setAssigneeIds(List.of(assigneeId));

        RecurringSchedulesRecord saved = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(scheduleRepository.insert(any())).thenReturn(saved);
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());

        service.create(projectId, req, ownerId);

        verify(assigneeRepository).replaceForSchedule(eq(saved.getId()), eq(List.of(assigneeId)));
    }

    // --- pause ---

    @Test
    @DisplayName("pause — sets paused_until to provided date")
    void pause_shouldSetPausedUntil_whenDateProvided() {
        Instant until = Instant.parse("2030-01-01T00:00:00Z");
        PauseScheduleRequest req = new PauseScheduleRequest();
        req.setUntil(until);

        UUID scheduleId = UUID.randomUUID();
        RecurringSchedulesRecord record = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);
        record.setId(scheduleId);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(record));
        when(scheduleRepository.update(any())).thenReturn(record);
        when(assigneeRepository.findByScheduleId(scheduleId)).thenReturn(List.of());
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        service.pause(projectId, scheduleId, req, ownerId);

        ArgumentCaptor<RecurringSchedulesRecord> captor = ArgumentCaptor.forClass(RecurringSchedulesRecord.class);
        verify(scheduleRepository).update(captor.capture());
        assertThat(captor.getValue().getPausedUntil().toInstant()).isEqualTo(until);
    }

    @Test
    @DisplayName("pause — sets paused_until to sentinel when until is null (indefinite)")
    void pause_shouldSetSentinel_whenUntilIsNull() {
        PauseScheduleRequest req = new PauseScheduleRequest();
        // until is null

        UUID scheduleId = UUID.randomUUID();
        RecurringSchedulesRecord record = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);
        record.setId(scheduleId);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(record));
        when(scheduleRepository.update(any())).thenReturn(record);
        when(assigneeRepository.findByScheduleId(scheduleId)).thenReturn(List.of());
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        service.pause(projectId, scheduleId, req, ownerId);

        ArgumentCaptor<RecurringSchedulesRecord> captor = ArgumentCaptor.forClass(RecurringSchedulesRecord.class);
        verify(scheduleRepository).update(captor.capture());
        assertThat(captor.getValue().getPausedUntil().toInstant())
                .isEqualTo(RecurringScheduleService.INDEFINITE_SENTINEL);
    }

    // --- resume ---

    @Test
    @DisplayName("resume — clears paused_until")
    void resume_shouldClearPausedUntil() {
        UUID scheduleId = UUID.randomUUID();
        RecurringSchedulesRecord record = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);
        record.setId(scheduleId);
        record.setPausedUntil(RecurringScheduleService.INDEFINITE_SENTINEL.atOffset(ZoneOffset.UTC));

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(record));
        when(scheduleRepository.update(any())).thenReturn(record);
        when(assigneeRepository.findByScheduleId(scheduleId)).thenReturn(List.of());
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        service.resume(projectId, scheduleId, ownerId);

        ArgumentCaptor<RecurringSchedulesRecord> captor = ArgumentCaptor.forClass(RecurringSchedulesRecord.class);
        verify(scheduleRepository).update(captor.capture());
        assertThat(captor.getValue().getPausedUntil()).isNull();
    }

    @Test
    @DisplayName("resume — member receives ForbiddenException")
    void resume_shouldThrow_whenMemberRole() {
        UUID scheduleId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> service.resume(projectId, scheduleId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- delete ---

    @Test
    @DisplayName("delete — owner deletes schedule successfully")
    void delete_shouldDeleteSchedule_forOwner() {
        UUID scheduleId = UUID.randomUUID();
        RecurringSchedulesRecord record = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);
        record.setId(scheduleId);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(record));

        service.delete(projectId, scheduleId, ownerId);

        verify(scheduleRepository).delete(scheduleId);
    }

    @Test
    @DisplayName("delete — throws NotFoundException when schedule not found")
    void delete_shouldThrow_whenScheduleNotFound() {
        UUID scheduleId = UUID.randomUUID();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(projectId, scheduleId, ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Recurring schedule not found");
    }

    // --- onAssigneeRemoved ---

    @Test
    @DisplayName("onAssigneeRemoved — auto-pauses schedule when last assignee removed")
    void onAssigneeRemoved_shouldAutoPause_whenLastAssigneeRemoved() {
        UUID scheduleId = UUID.randomUUID();
        RecurringSchedulesRecord record = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);
        record.setId(scheduleId);

        when(assigneeRepository.deleteByUserId(memberId)).thenReturn(List.of(scheduleId));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(record));
        when(scheduleRepository.update(any())).thenReturn(record);

        service.onAssigneeRemoved(projectId, memberId);

        ArgumentCaptor<RecurringSchedulesRecord> captor = ArgumentCaptor.forClass(RecurringSchedulesRecord.class);
        verify(scheduleRepository).update(captor.capture());
        assertThat(captor.getValue().getPausedUntil().toInstant())
                .isEqualTo(RecurringScheduleService.INDEFINITE_SENTINEL);
    }

    @Test
    @DisplayName("onAssigneeRemoved — no-op when user has no schedule assignments")
    void onAssigneeRemoved_shouldDoNothing_whenNoSchedulesAffected() {
        when(assigneeRepository.deleteByUserId(memberId)).thenReturn(List.of());

        service.onAssigneeRemoved(projectId, memberId);

        verify(scheduleRepository, never()).update(any());
    }

    // --- update ---

    @Test
    @DisplayName("update — auto-pauses schedule when assigneeIds updated to empty list")
    void update_shouldAutoPause_whenAssigneesSetToEmpty() {
        UUID scheduleId = UUID.randomUUID();
        RecurringSchedulesRecord record = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);
        record.setId(scheduleId);

        UpdateScheduleRequest req = new UpdateScheduleRequest();
        req.setName("Updated Name");
        req.setTemplateId(templateId);
        req.setCronExpression(VALID_CRON);
        req.setTimezone(VALID_TZ);
        req.setAssigneeIds(List.of());

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(record));
        when(scheduleRepository.update(any())).thenReturn(record);
        when(assigneeRepository.findByScheduleId(scheduleId)).thenReturn(List.of());
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        service.update(projectId, scheduleId, req, ownerId);

        ArgumentCaptor<RecurringSchedulesRecord> captor = ArgumentCaptor.forClass(RecurringSchedulesRecord.class);
        verify(scheduleRepository).update(captor.capture());
        assertThat(captor.getValue().getPausedUntil().toInstant())
                .isEqualTo(RecurringScheduleService.INDEFINITE_SENTINEL);
    }

    @Test
    @DisplayName("update — sets all fields and returns response when assignees non-empty")
    void update_shouldReturnResponse_whenAssigneesNonEmpty() {
        UUID scheduleId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        RecurringSchedulesRecord row = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);
        row.setId(scheduleId);

        UpdateScheduleRequest req = new UpdateScheduleRequest();
        req.setName("Updated Name");
        req.setTemplateId(templateId);
        req.setCronExpression(VALID_CRON);
        req.setTimezone(VALID_TZ);
        req.setAssigneeIds(List.of(assigneeId));
        // pausedUntil and expiresAt intentionally null — exercises the null branches

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(row));
        when(scheduleRepository.update(any())).thenReturn(row);
        when(assigneeRepository.findByScheduleId(scheduleId)).thenReturn(List.of());
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        RecurringScheduleResponse result = service.update(projectId, scheduleId, req, ownerId);

        ArgumentCaptor<RecurringSchedulesRecord> captor = ArgumentCaptor.forClass(RecurringSchedulesRecord.class);
        verify(scheduleRepository).update(captor.capture());
        assertThat(captor.getValue().getPausedUntil()).isNull();
        assertThat(captor.getValue().getExpiresAt()).isNull();
        assertThat(result).isNotNull();
        verify(assigneeRepository).replaceForSchedule(eq(scheduleId), eq(List.of(assigneeId)));
    }

    @Test
    @DisplayName("update — sets pausedUntil and expiresAt when provided")
    void update_shouldSetPausedUntilAndExpiresAt_whenProvided() {
        UUID scheduleId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Instant pausedUntil = Instant.parse("2030-06-01T00:00:00Z");
        Instant expiresAt   = Instant.parse("2031-01-01T00:00:00Z");
        RecurringSchedulesRecord row = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);
        row.setId(scheduleId);

        UpdateScheduleRequest req = new UpdateScheduleRequest();
        req.setName("With Dates");
        req.setTemplateId(templateId);
        req.setCronExpression(VALID_CRON);
        req.setTimezone(VALID_TZ);
        req.setPausedUntil(pausedUntil);
        req.setExpiresAt(expiresAt);
        req.setAssigneeIds(List.of(assigneeId));

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(row));
        when(scheduleRepository.update(any())).thenReturn(row);
        when(assigneeRepository.findByScheduleId(scheduleId)).thenReturn(List.of());
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        service.update(projectId, scheduleId, req, ownerId);

        ArgumentCaptor<RecurringSchedulesRecord> captor = ArgumentCaptor.forClass(RecurringSchedulesRecord.class);
        verify(scheduleRepository).update(captor.capture());
        assertThat(captor.getValue().getPausedUntil().toInstant()).isEqualTo(pausedUntil);
        assertThat(captor.getValue().getExpiresAt().toInstant()).isEqualTo(expiresAt);
    }

    // --- list / get ---

    @Test
    @DisplayName("get — returns schedule for member")
    void get_shouldReturnSchedule_forMember() {
        UUID scheduleId = UUID.randomUUID();
        RecurringSchedulesRecord row = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);
        row.setId(scheduleId);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(row));
        when(assigneeRepository.findByScheduleId(scheduleId)).thenReturn(List.of());
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        RecurringScheduleResponse result = service.get(projectId, scheduleId, memberId);

        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(projectId);
    }



    @Test
    @DisplayName("list — member can list schedules")
    void list_shouldReturnSchedules_forMember() {
        RecurringSchedulesRecord record = buildRecord(projectId, templateId, VALID_CRON, VALID_TZ);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(scheduleRepository.findByProjectId(projectId)).thenReturn(List.of(record));
        when(assigneeRepository.findByScheduleId(any())).thenReturn(List.of());
        when(jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        List<RecurringScheduleResponse> result = service.list(projectId, memberId);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("list — non-member receives ForbiddenException")
    void list_shouldThrow_whenNotMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(projectId, memberId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("get — throws NotFoundException when schedule belongs to different project")
    void get_shouldThrow_whenScheduleBelongsToDifferentProject() {
        UUID scheduleId = UUID.randomUUID();
        RecurringSchedulesRecord record = buildRecord(UUID.randomUUID(), templateId, VALID_CRON, VALID_TZ);
        record.setId(scheduleId);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.get(projectId, scheduleId, memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Recurring schedule not found");
    }

    // --- validateCron ---

    @Test
    @DisplayName("validateCron — valid hourly cron passes")
    void validateCron_shouldPass_forValidHourlyCron() {
        service.validateCron("0 0 * * * *", "UTC");
    }

    @Test
    @DisplayName("validateCron — daily cron passes")
    void validateCron_shouldPass_forDailyCron() {
        service.validateCron("0 0 9 * * *", "Europe/London");
    }

    @Test
    @DisplayName("validateCron — invalid expression throws BadRequestException")
    void validateCron_shouldThrow_forInvalidExpression() {
        assertThatThrownBy(() -> service.validateCron("not-valid", "UTC"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid cron expression");
    }

    @Test
    @DisplayName("validateCron — every-minute cron throws BadRequestException")
    void validateCron_shouldThrow_forEveryMinuteCron() {
        assertThatThrownBy(() -> service.validateCron("0 * * * * *", "UTC"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cron expression must have a minimum interval of 1 hour");
    }

    @Test
    @DisplayName("validateCron — impossible date (Feb 31) throws BadRequestException")
    void validateCron_shouldThrow_whenCronNeverFires() {
        // Feb 31 never exists — first is null, second is set to null via null-propagation, triggering second == null guard
        assertThatThrownBy(() -> service.validateCron("0 0 9 31 2 *", "UTC"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cron expression must have a minimum interval of 1 hour");
    }

    @Test
    @DisplayName("validateCron — invalid timezone throws BadRequestException")
    void validateCron_shouldThrow_forInvalidTimezone() {
        assertThatThrownBy(() -> service.validateCron("0 0 * * * *", "Not/ATimezone"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid timezone");
    }

    // --- helper ---

    private RecurringSchedulesRecord buildRecord(UUID pid, UUID tid, String cron, String tz) {
        RecurringSchedulesRecord r = new RecurringSchedulesRecord();
        r.setId(UUID.randomUUID());
        r.setProjectId(pid);
        r.setTemplateId(tid);
        r.setName("Test Schedule");
        r.setCronExpression(cron);
        r.setTimezone(tz);
        r.setCurrentRotationIndex(0);
        r.setNextRunAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        r.setCreatedBy(ownerId);
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return r;
    }
}
