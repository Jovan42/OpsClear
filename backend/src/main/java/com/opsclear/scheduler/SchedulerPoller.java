package com.opsclear.scheduler;

import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleMissedRunRepository;
import com.opsclear.service.ScheduleJobCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SchedulerPoller {

    private final RecurringScheduleRepository scheduleRepository;
    private final ScheduleMissedRunRepository missedRunRepository;
    private final ScheduleJobCreator scheduleJobCreator;

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
            // Missed run — record each skipped tick and advance next_run_at to first future occurrence
            ZonedDateTime futureZdt = nextOccurrenceZdt;
            while (!futureZdt.toInstant().isAfter(now)) {
                Instant missedAt = futureZdt.toInstant();
                log.warn("Schedule {} missed run at {}, recording", schedule.getId(), missedAt);
                missedRunRepository.insert(schedule.getId(), missedAt);
                futureZdt = CronExpression.parse(schedule.getCronExpression()).next(futureZdt);
            }
            int newIndex = schedule.getCurrentRotationIndex() + 1;
            scheduleRepository.updateAfterRun(schedule.getId(), futureZdt.toInstant(), previousRunAt, newIndex);
            return;
        }

        // Normal run — create job from template
        scheduleJobCreator.createJob(schedule, previousRunAt, zone, schedule.getCreatedBy());

        int newIndex = schedule.getCurrentRotationIndex() + 1;
        scheduleRepository.updateAfterRun(schedule.getId(), nextOccurrence, previousRunAt, newIndex);

        log.info("Schedule {} fired, next_run_at set to {}", schedule.getId(), nextOccurrence);
    }
}
