package com.opsclear.service;

import com.opsclear.dto.CreateScheduleRequest;
import com.opsclear.dto.PauseScheduleRequest;
import com.opsclear.dto.RecurringScheduleResponse;
import com.opsclear.dto.ScheduleAssigneeResponse;
import com.opsclear.dto.UpdateScheduleRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleAssigneeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringScheduleService {

    static final Instant INDEFINITE_SENTINEL = Instant.parse("9999-01-01T00:00:00Z");

    private final RecurringScheduleRepository scheduleRepository;
    private final ScheduleAssigneeRepository assigneeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final JobTemplateRepository jobTemplateRepository;

    @Transactional
    public RecurringScheduleResponse create(UUID projectId, CreateScheduleRequest req, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        requireTemplate(req.getTemplateId());

        validateCron(req.getCronExpression(), req.getTimezone());

        Instant nextRunAt = Objects.requireNonNull(
                CronExpression.parse(req.getCronExpression())
                        .next(ZonedDateTime.now(resolveZone(req.getTimezone())))).toInstant();

        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(projectId);
        row.setTemplateId(req.getTemplateId());
        row.setName(req.getName().strip());
        row.setCronExpression(req.getCronExpression());
        row.setTimezone(req.getTimezone());
        row.setPausedUntil(req.getPausedUntil() != null
                ? req.getPausedUntil().atOffset(ZoneOffset.UTC) : null);
        row.setExpiresAt(req.getExpiresAt() != null
                ? req.getExpiresAt().atOffset(ZoneOffset.UTC) : null);
        row.setNextRunAt(nextRunAt.atOffset(ZoneOffset.UTC));
        row.setCreatedBy(requesterId);

        RecurringSchedulesRecord saved = scheduleRepository.insert(row);

        List<UUID> assigneeIds = req.getAssigneeIds();
        if (assigneeIds != null && !assigneeIds.isEmpty()) {
            assigneeRepository.replaceForSchedule(saved.getId(), assigneeIds);
        }

        log.info("Created recurring schedule '{}' in project {} by user {}", saved.getName(), projectId, requesterId);

        List<ScheduleAssigneeResponse> assignees = assigneeRepository.findByScheduleId(saved.getId());
        String templateName = loadTemplateName(saved.getTemplateId());
        return RecurringScheduleResponse.from(saved, assignees, templateName);
    }

    @Transactional(readOnly = true)
    public List<RecurringScheduleResponse> list(UUID projectId, UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);

        List<RecurringSchedulesRecord> records = scheduleRepository.findByProjectId(projectId);
        return records.stream()
                .map(r -> {
                    List<ScheduleAssigneeResponse> assignees = assigneeRepository.findByScheduleId(r.getId());
                    String templateName = loadTemplateName(r.getTemplateId());
                    return RecurringScheduleResponse.from(r, assignees, templateName);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public RecurringScheduleResponse get(UUID projectId, UUID scheduleId, UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);
        RecurringSchedulesRecord row = requireScheduleInProject(projectId, scheduleId);
        List<ScheduleAssigneeResponse> assignees = assigneeRepository.findByScheduleId(scheduleId);
        String templateName = loadTemplateName(row.getTemplateId());
        return RecurringScheduleResponse.from(row, assignees, templateName);
    }

    @Transactional
    public RecurringScheduleResponse update(UUID projectId, UUID scheduleId,
                                            UpdateScheduleRequest req, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        RecurringSchedulesRecord row = requireScheduleInProject(projectId, scheduleId);

        requireTemplate(req.getTemplateId());
        validateCron(req.getCronExpression(), req.getTimezone());

        Instant nextRunAt = Objects.requireNonNull(
                CronExpression.parse(req.getCronExpression())
                        .next(ZonedDateTime.now(resolveZone(req.getTimezone())))).toInstant();

        row.setName(req.getName().strip());
        row.setTemplateId(req.getTemplateId());
        row.setCronExpression(req.getCronExpression());
        row.setTimezone(req.getTimezone());
        row.setPausedUntil(req.getPausedUntil() != null ? req.getPausedUntil().atOffset(ZoneOffset.UTC) : null);
        row.setExpiresAt(req.getExpiresAt() != null ? req.getExpiresAt().atOffset(ZoneOffset.UTC) : null);
        row.setNextRunAt(nextRunAt.atOffset(ZoneOffset.UTC));

        assigneeRepository.replaceForSchedule(scheduleId, req.getAssigneeIds());
        if (req.getAssigneeIds().isEmpty()) {
            row.setPausedUntil(INDEFINITE_SENTINEL.atOffset(ZoneOffset.UTC));
        }

        RecurringSchedulesRecord updated = scheduleRepository.update(row);
        log.info("Updated recurring schedule {} in project {} by user {}", scheduleId, projectId, requesterId);

        List<ScheduleAssigneeResponse> assignees = assigneeRepository.findByScheduleId(scheduleId);
        String templateName = loadTemplateName(updated.getTemplateId());
        return RecurringScheduleResponse.from(updated, assignees, templateName);
    }

    @Transactional
    public void delete(UUID projectId, UUID scheduleId, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        requireScheduleInProject(projectId, scheduleId);
        scheduleRepository.delete(scheduleId);
        log.info("Deleted recurring schedule {} from project {} by user {}", scheduleId, projectId, requesterId);
    }

    @Transactional
    public RecurringScheduleResponse pause(UUID projectId, UUID scheduleId,
                                           PauseScheduleRequest req, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        RecurringSchedulesRecord row = requireScheduleInProject(projectId, scheduleId);

        Instant until = req.getUntil() != null ? req.getUntil() : INDEFINITE_SENTINEL;
        row.setPausedUntil(until.atOffset(ZoneOffset.UTC));
        RecurringSchedulesRecord updated = scheduleRepository.update(row);
        log.info("Paused recurring schedule {} until {} by user {}", scheduleId, until, requesterId);

        List<ScheduleAssigneeResponse> assignees = assigneeRepository.findByScheduleId(scheduleId);
        String templateName = loadTemplateName(updated.getTemplateId());
        return RecurringScheduleResponse.from(updated, assignees, templateName);
    }

    @Transactional
    public RecurringScheduleResponse resume(UUID projectId, UUID scheduleId, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        RecurringSchedulesRecord row = requireScheduleInProject(projectId, scheduleId);

        row.setPausedUntil(null);
        RecurringSchedulesRecord updated = scheduleRepository.update(row);
        log.info("Resumed recurring schedule {} in project {} by user {}", scheduleId, projectId, requesterId);

        List<ScheduleAssigneeResponse> assignees = assigneeRepository.findByScheduleId(scheduleId);
        String templateName = loadTemplateName(updated.getTemplateId());
        return RecurringScheduleResponse.from(updated, assignees, templateName);
    }

    @Transactional
    public void onAssigneeRemoved(UUID projectId, UUID userId) {
        List<UUID> nowEmptyScheduleIds = assigneeRepository.deleteByUserId(userId);
        for (UUID sid : nowEmptyScheduleIds) {
            scheduleRepository.findById(sid).ifPresent(row -> {
                row.setPausedUntil(INDEFINITE_SENTINEL.atOffset(ZoneOffset.UTC));
                scheduleRepository.update(row);
                log.info("Auto-paused schedule {} (no assignees) after user {} left project {}",
                        sid, userId, projectId);
            });
        }
    }

    // --- Cron validation ---

    void validateCron(String expression, String timezone) {
        CronExpression cron;
        try {
            cron = CronExpression.parse(expression);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ErrorMessages.RecurringSchedule.INVALID_CRON + e.getMessage());
        }
        ZoneId zone = resolveZone(timezone);
        ZonedDateTime first = cron.next(ZonedDateTime.now(zone));
        ZonedDateTime second = first != null ? cron.next(first) : null;
        if (second == null || second.toEpochSecond() - first.toEpochSecond() < 3600) {
            throw new BadRequestException(ErrorMessages.RecurringSchedule.CRON_INTERVAL_TOO_SHORT);
        }
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            throw new BadRequestException(ErrorMessages.RecurringSchedule.INVALID_TIMEZONE + timezone);
        }
    }

    private String loadTemplateName(UUID templateId) {
        return jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .map(JobTemplateModel::getName)
                .orElse(null);
    }

    // --- Guards ---

    private void requireProject(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private void requireMember(UUID projectId, UUID userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel requester = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }

    private void requireTemplate(UUID templateId) {
        jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.JobTemplate.NOT_FOUND));
    }

    private RecurringSchedulesRecord requireScheduleInProject(UUID projectId, UUID scheduleId) {
        RecurringSchedulesRecord row = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.RecurringSchedule.NOT_FOUND));
        if (!projectId.equals(row.getProjectId())) {
            throw new NotFoundException(ErrorMessages.RecurringSchedule.NOT_FOUND);
        }
        return row;
    }
}
