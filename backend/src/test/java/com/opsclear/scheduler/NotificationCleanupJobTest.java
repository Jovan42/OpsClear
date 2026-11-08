package com.opsclear.scheduler;

import com.opsclear.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCleanupJob")
class NotificationCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-11-08T03:30:00Z");

    @Mock private NotificationRepository notificationRepository;

    private NotificationCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new NotificationCleanupJob(notificationRepository);
    }

    @Test
    @DisplayName("cleanup deletes read notifications older than 30 days, then any older than 90 days")
    void cleanup_shouldDeleteReadAfter30Days_thenAllAfter90Days() {
        when(notificationRepository.deleteReadCreatedBefore(any())).thenReturn(4);
        when(notificationRepository.deleteCreatedBefore(any())).thenReturn(2);

        job.cleanup(NOW);

        InOrder order = inOrder(notificationRepository);
        order.verify(notificationRepository).deleteReadCreatedBefore(Instant.parse("2026-10-09T03:30:00Z"));
        order.verify(notificationRepository).deleteCreatedBefore(Instant.parse("2026-08-10T03:30:00Z"));
    }

    @Test
    @DisplayName("run uses the current time as the retention reference")
    void run_shouldUseCurrentTime() {
        Instant before = Instant.now();

        job.run();

        ArgumentCaptor<Instant> readCutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> allCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteReadCreatedBefore(readCutoff.capture());
        verify(notificationRepository).deleteCreatedBefore(allCutoff.capture());
        Instant after = Instant.now();
        assertThat(readCutoff.getValue()).isBetween(before.minus(Duration.ofDays(30)), after.minus(Duration.ofDays(30)));
        assertThat(allCutoff.getValue()).isBetween(before.minus(Duration.ofDays(90)), after.minus(Duration.ofDays(90)));
    }
}
