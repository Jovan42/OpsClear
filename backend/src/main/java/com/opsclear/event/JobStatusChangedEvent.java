package com.opsclear.event;

import com.opsclear.model.JobStatus;

import java.util.UUID;

/**
 * A job's status changed to anything but Blocked (ADR-0051 {@code JOB_STATUS_CHANGED}), including Completed and
 * reopen. A move to Blocked publishes {@link JobBlockedEvent} instead. {@code actorId} is who changed it.
 */
public record JobStatusChangedEvent(UUID jobId, UUID projectId, UUID actorId, JobStatus fromStatus,
                                    JobStatus toStatus) {
}
