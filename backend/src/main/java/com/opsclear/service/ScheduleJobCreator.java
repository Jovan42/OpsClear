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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleJobCreator {

    private static final Pattern WILDCARD_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final JobRepository jobRepository;
    private final JobStatusHistoryRepository jobStatusHistoryRepository;
    private final JobTemplateRepository jobTemplateRepository;
    private final OrganisationRepository organisationRepository;
    private final ProjectRepository projectRepository;
    private final ScheduleAssigneeRepository assigneeRepository;
    private final FriendlyIdService friendlyIdService;

    /**
     * Materialises a job from the given schedule using {@code scheduledFor} as the reference
     * date for wildcard resolution and deadline calculation.
     * <p>
     * Used by both {@link com.opsclear.scheduler.SchedulerPoller} (normal runs)
     * and {@link RecurringScheduleService} (materialising missed runs).
     */
    public JobModel createJob(RecurringSchedulesRecord schedule, Instant scheduledFor,
                              ZoneId zone, UUID createdBy) {
        UUID templateId = schedule.getTemplateId();
        JobTemplateModel template = jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .orElseThrow(() -> new NotFoundException("Job template not found"));

        List<ScheduleAssigneeResponse> assignees = assigneeRepository.findByScheduleId(schedule.getId());
        int rotationIndex = schedule.getCurrentRotationIndex();
        ScheduleAssigneeResponse pickedAssignee = assignees.isEmpty()
                ? null : assignees.get(rotationIndex % assignees.size());

        LocalDate scheduledForDate = scheduledFor.atZone(zone).toLocalDate();
        Instant deadline = template.getDeadlineOffsetDays() != null
                ? scheduledForDate.plusDays(template.getDeadlineOffsetDays()).atStartOfDay(zone).toInstant()
                : null;

        UUID projectId = schedule.getProjectId();
        String projectName = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .map(ProjectModel::getName).orElse("");

        Map<String, String> vars = buildWildcardVars(
                pickedAssignee, scheduledForDate, template.getOccurrenceCount(), projectName);
        String title = resolve(
                template.getTitle() != null ? template.getTitle() : template.getName(), vars);
        String description = resolve(template.getDescription(), vars);

        UUID orgId = organisationRepository.findByProject(projectId)
                .map(OrganisationModel::getId).orElse(null);
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
                .createdBy(createdBy)
                .sourceScheduleId(schedule.getId())
                .build();

        JobModel saved = jobRepository.save(job);
        jobStatusHistoryRepository.insert(saved.getId(), null, JobStatus.NEW.name(), createdBy, null);
        jobTemplateRepository.incrementOccurrenceCount(templateId);

        log.info("Created job '{}' (id={}) from schedule {} for scheduledFor={}",
                title, saved.getId(), schedule.getId(), scheduledFor);
        return saved;
    }

    public String resolve(String text, Map<String, String> vars) {
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

    public Map<String, String> buildWildcardVars(ScheduleAssigneeResponse assignee,
                                                   LocalDate date, int occurrenceCount,
                                                   String projectName) {
        Map<String, String> vars = new HashMap<>();
        vars.put("assignee", assignee != null ? assignee.getUserName() : "");
        vars.put("date", date.toString());
        vars.put("day", String.valueOf(date.getDayOfMonth()));
        vars.put("month", date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        vars.put("year", String.valueOf(date.getYear()));
        vars.put("week", String.valueOf(date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        vars.put("quarter", "Q" + ((date.getMonthValue() - 1) / 3 + 1));
        vars.put("occurrence", String.valueOf(occurrenceCount + 1));
        vars.put("creator", "System");
        vars.put("project", projectName);
        return vars;
    }
}
