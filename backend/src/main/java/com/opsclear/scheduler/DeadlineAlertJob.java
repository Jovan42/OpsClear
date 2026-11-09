package com.opsclear.scheduler;

import com.opsclear.event.DeadlineAlertEvent;
import com.opsclear.model.DeadlineAlertKind;
import com.opsclear.model.JobModel;
import com.opsclear.repository.JobDeadlineAlertRepository;
import com.opsclear.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Daily deadline alerts (ADR-0051 §Deadline alerts), on UTC dates:
 * <ul>
 *   <li>{@code JOB_DEADLINE_SOON}: open jobs whose deadline date is tomorrow;</li>
 *   <li>{@code JOB_OVERDUE}: open jobs whose deadline date is before today, i.e. the whole deadline day has passed
 *       (deliberately later than the Dashboard's overdue badge).</li>
 * </ul>
 * Open means not Completed and not deleted, in a project that is neither completed nor deleted. Each alert is sent
 * once per job, kind and deadline value ({@code job_deadline_alerts}), so a moved deadline alerts again. The alert
 * row (an idempotent insert) and its event commit together, in a transaction per job; {@code NotificationService} fans
 * the event out after the commit.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeadlineAlertJob {

    private static final Duration ONE_DAY = Duration.ofDays(1);

    private final JobRepository jobRepository;
    private final JobDeadlineAlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "0 0 6 * * *", zone = "UTC")
    public void run() {
        checkDeadlines(Instant.now());
    }

    /** Sends the alerts due on {@code now}'s UTC date. Returns how many alerts were sent. */
    public int checkDeadlines(Instant now) {
        Instant startOfToday = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfTomorrow = startOfToday.plus(ONE_DAY);
        int soon = alert(DeadlineAlertKind.DEADLINE_SOON, jobRepository
                .findOpenInActiveProjectsWithDeadlineBetween(startOfTomorrow, startOfTomorrow.plus(ONE_DAY)));
        int overdue = alert(DeadlineAlertKind.OVERDUE,
                jobRepository.findOpenInActiveProjectsWithDeadlineBefore(startOfToday));
        log.info("Deadline alerts for {}: {} due tomorrow, {} overdue", startOfToday, soon, overdue);
        return soon + overdue;
    }

    // One transaction per job, and a failing job is logged and skipped: it can't roll back or stop the others. Its
    // alert isn't recorded, so the next run retries it while the deadline still qualifies.
    private int alert(DeadlineAlertKind kind, List<JobModel> jobs) {
        int sent = 0;
        for (JobModel job : jobs) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> alertOnce(kind, job)))) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.error("Failed to send {} alert for job {}: {}", kind, job.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }

    // Records the alert and publishes its event, unless a row for this kind and deadline already exists (an earlier
    // or overlapping run sent it).
    private boolean alertOnce(DeadlineAlertKind kind, JobModel job) {
        if (!alertRepository.insertIfAbsent(job.getId(), kind, job.getDeadline())) {
            return false;
        }
        eventPublisher.publishEvent(new DeadlineAlertEvent(job.getId(), job.getProjectId(), kind, job.getDeadline()));
        log.info("Recorded {} alert for job {} (deadline {})", kind, job.getId(), job.getDeadline());
        return true;
    }
}
