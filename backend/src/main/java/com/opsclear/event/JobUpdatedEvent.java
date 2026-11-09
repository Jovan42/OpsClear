package com.opsclear.event;

import com.opsclear.model.JobPriority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A job's deadline and/or priority changed (ADR-0051 {@code JOB_UPDATED}); other field edits don't publish it.
 * {@code changedFields} holds {@link #DEADLINE} and/or {@link #PRIORITY}. {@code deadline} and {@code priority} are
 * the new values of the changed fields only (null otherwise), so a changed deadline that is null was cleared.
 */
public record JobUpdatedEvent(UUID jobId, UUID projectId, UUID actorId, List<String> changedFields,
                              Instant deadline, JobPriority priority) {

    public static final String DEADLINE = "deadline";
    public static final String PRIORITY = "priority";
}
