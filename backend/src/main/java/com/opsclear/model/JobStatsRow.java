package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One completed job in the velocity stats cohort (ADR-0050), as returned by JobStatsRepository.
 * Not a table: every field is derived from jobs and job_status_history.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStatsRow {

    private UUID jobId;
    private UUID assignedTo;           // null = unassigned
    private String assigneeName;       // resolved via JOIN, not a DB column
    private Instant createdAt;
    private Instant deadline;          // null = no deadline
    private Instant completedAt;       // last -> COMPLETED transition
    private Instant firstInProgressAt; // first -> IN_PROGRESS transition, null if never recorded
    private long blockedSeconds;       // sum of BLOCKED intervals ending at or before completedAt
    private LocalDate weekStart;       // Monday of completedAt's ISO week in the requested time zone
}
