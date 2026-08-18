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
public class OrgCreditModel {

    private UUID id;
    private UUID orgId;
    private int amount;
    private String reason;
    private UUID submissionId;
    private UUID grantedBy;
    private String grantedByName;
    private Instant createdAt;

    // Not persisted — set only on the object returned from CreditService.grant() when
    // the Paddle sync was skipped (JOB-180 #2), so the super admin console can warn the
    // caller immediately instead of the failure being silent/log-only. Ledger reads
    // (getLedger) never populate this — it's a one-shot signal for the grant response.
    private String paddleSyncSkippedReason;
}
