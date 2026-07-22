package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Relationship entry from the perspective of a specific job.
 * Populated in-memory by JobService.getById — not stored directly in DB.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRelationshipEntry {

    private UUID id;
    private JobRelationshipType type;
    private JobRelationshipDirection direction;
    private UUID linkedJobId;
    private String linkedJobFriendlyId;
    private String linkedJobTitle;
    private JobStatus linkedJobStatus;
}
