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
}
