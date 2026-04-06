package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStatusHistoryModel {

    private UUID id;
    private UUID jobId;
    private String changedFrom;   // null on job creation
    private String changedTo;
    private UUID changedBy;       // null for future system-generated transitions
    private String changedByName;
    private Instant changedAt;
    private String blockReason;   // null unless changedTo = BLOCKED
}
