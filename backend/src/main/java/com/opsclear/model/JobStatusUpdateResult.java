package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outcome of a job status update (ADR-0052): either the status changed, or, for a Member completing a job on a gated
 * project, a completion approval was created and the job kept its status ({@code completionRequested}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStatusUpdateResult {

    private JobModel job;
    private boolean completionRequested;
}
