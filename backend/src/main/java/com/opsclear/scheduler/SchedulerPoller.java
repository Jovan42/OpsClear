package com.opsclear.scheduler;

import com.opsclear.dto.ScheduleAssigneeResponse;
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
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleAssigneeRepository;
import com.opsclear.service.FriendlyIdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class SchedulerPoller {

    private static final Pattern WILDCARD_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final RecurringScheduleRepository scheduleRepository;
    private final ScheduleAssigneeRepository assigneeRepository;
    private final JobRepository jobRepository;
    private final JobStatusHistoryRepository jobStatusHistoryRepository;
    private final JobTemplateRepository jobTemplateRepository;
    private final OrganisationRepository organisationRepository;
    private final ProjectRepository projectRepository;
    private final FriendlyIdService friendlyIdService;

    @Scheduled(fixedRate = 60_000)
    public void tick() {
        Instant now = Instant.now();
        List<RecurringSchedulesRecord> due = scheduleRepository.findDue(now);
        if (!due.isEmpty()) {
            log.info("SchedulerPoller: {} schedule(s) due at {}", due.size(), now);
        }
        for (RecurringSchedulesRecord schedule : due) {
            try {
                processSchedule(schedule, now);
            } catch (Exception e) {
                log.error("Failed to process schedule {}: {}", schedule.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void processSchedule(RecurringSchedulesRecord schedule, Instant now) {
        Instant previousRunAt = schedule.getNextRunAt().toInstant();
        String timezone = schedule.getTimezone();
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (Exception e) {
            log.error("Schedule {} has invalid timezone '{}', skipping", schedule.getId(), timezone);
            return;
        }

        ZonedDateTime previousZdt = ZonedDateTime.ofInstant(previousRunAt, zone);
        ZonedDateTime nextOccurrenceZdt = CronExpression.parse(schedule.getCronExpression()).next(previousZdt);
        if (nextOccurrenceZdt == null) {
            log.warn("Schedule {} cron has no further occurrences, skipping", schedule.getId());
            return;
        }
        Instant nextOccurrence = nextOccurrenceZdt.toInstant();

        if (!nextOccurrence.isAfter(now)) {
            // Missed run — advance next_run_at to first future occurrence without creating a job
            ZonedDateTime futureZdt = nextOccurrenceZdt;
            while (!futureZdt.toInstant().isAfter(now)) {
                log.warn("Schedule {} missed run at {}, advancing without job creation",
                        schedule.getId(), futureZdt.toInstant());
                futureZdt = CronExpression.parse(schedule.getCronExpression()).next(futureZdt);
            }
            Instant futureRunAt = futureZdt.toInstant();
            int newIndex = schedule.getCurrentRotationIndex() + 1;
            scheduleRepository.updateAfterRun(schedule.getId(), futureRunAt, previousRunAt, newIndex);
            return;
        }

        // Normal run — create job from template
        UUID templateId = schedule.getTemplateId();
        JobTemplateModel template = jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .orElse(null);
        if (template == null) {
            log.error("Schedule {} references deleted/missing template {}, skipping",
                    schedule.getId(), templateId);
            return;
        }

        List<ScheduleAssigneeResponse> assignees = assigneeRepository.findByScheduleId(schedule.getId());
        int rotationIndex = schedule.getCurrentRotationIndex();

        ScheduleAssigneeResponse pickedAssignee = null;
        if (!assignees.isEmpty()) {
            pickedAssignee = assignees.get(rotationIndex % assignees.size());
        }

        LocalDate scheduledForDate = previousRunAt.atZone(zone).toLocalDate();
        Instant scheduledFor = previousRunAt;
        Instant deadline = template.getDeadlineOffsetDays() != null
                ? scheduledForDate.plusDays(template.getDeadlineOffsetDays())
                        .atStartOfDay(zone).toInstant()
                : null;

        UUID projectId = schedule.getProjectId();
        String projectName = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .map(ProjectModel::getName)
                .orElse("");

        Map<String, String> vars = buildWildcardVars(
                pickedAssignee, scheduledForDate, template.getOccurrenceCount(), projectName);
        String title = resolveWildcards(
                template.getTitle() != null ? template.getTitle() : template.getName(), vars);
        String description = resolveWildcards(template.getDescription(), vars);
        UUID orgId = organisationRepository.findByProject(projectId)
                .map(OrganisationModel::getId)
                .orElse(null);

        String friendlyId = orgId != null
                ? friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.JOB) : null;

        JobModel job = JobModel.builder()
                .friendlyId(friendlyId)
                .projectId(projectId)
                .title(title)
                .description(description)
                .client(template.getClient())
                .assignedTo(pickedAssignee != null ? pickedAssignee.getUserId() : null)
                .deadline(deadline)
                .status(JobStatus.NEW)
                .priority(template.getPriority() != null
                        ? JobPriority.valueOf(template.getPriority()) : JobPriority.MEDIUM)
                .milestoneId(template.getMilestoneId())
                .createdBy(schedule.getCreatedBy())
                .sourceScheduleId(schedule.getId())
                .build();

        JobModel savedJob = jobRepository.save(job);
        jobStatusHistoryRepository.insert(savedJob.getId(), null, JobStatus.NEW.name(),
                schedule.getCreatedBy(), null);

        jobTemplateRepository.incrementOccurrenceCount(templateId);

        int newIndex = rotationIndex + 1;
        scheduleRepository.updateAfterRun(schedule.getId(), nextOccurrence, previousRunAt, newIndex);

        log.info("Schedule {} created job '{}' (id={}) for scheduled_at={}",
                schedule.getId(), title, savedJob.getId(), scheduledFor);
    }

    String resolveWildcards(String text, Map<String, String> vars) {
        if (text == null) {
            return null;
        }
        Matcher m = WILDCARD_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = vars.getOrDefault(key, m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Map<String, String> buildWildcardVars(ScheduleAssigneeResponse assignee,
                                                   LocalDate date, int occurrenceCount, String projectName) {
        Map<String, String> vars = new HashMap<>();
        vars.put("assignee", assignee != null ? assignee.getUserName() : "");
        vars.put("date", date.toString());
        vars.put("month", date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        vars.put("year", String.valueOf(date.getYear()));
        vars.put("occurrence", String.valueOf(occurrenceCount + 1));
        vars.put("creator", "System");
        vars.put("project", projectName);
        return vars;
    }
}
