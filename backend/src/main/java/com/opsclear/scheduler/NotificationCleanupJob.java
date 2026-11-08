package com.opsclear.scheduler;

import com.opsclear.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Nightly notification retention (ADR-0051 §Retention): read notifications are deleted 30 days after creation,
 * unread ones 90 days after creation.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationCleanupJob {

    static final Duration READ_RETENTION = Duration.ofDays(30);
    static final Duration UNREAD_RETENTION = Duration.ofDays(90);

    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 30 3 * * *")
    public void run() {
        cleanup(Instant.now());
    }

    /** Runs the retention deletes relative to {@code now}. */
    public void cleanup(Instant now) {
        int read = notificationRepository.deleteReadCreatedBefore(now.minus(READ_RETENTION));
        int old = notificationRepository.deleteCreatedBefore(now.minus(UNREAD_RETENTION));
        log.info("Notification cleanup: deleted {} read notification(s) older than 30 days and {} older than 90 days",
                read, old);
    }
}
