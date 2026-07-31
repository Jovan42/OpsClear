package com.opsclear.dto;

import com.opsclear.model.OrgCreditModel;
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
public class CreditLedgerEntryResponse {

    private UUID id;
    private UUID orgId;
    private int amount;
    private String reason;
    private UUID submissionId;
    private UUID grantedBy;
    private String grantedByName;
    private Instant createdAt;

    public static CreditLedgerEntryResponse from(OrgCreditModel model) {
        return CreditLedgerEntryResponse.builder()
                .id(model.getId())
                .orgId(model.getOrgId())
                .amount(model.getAmount())
                .reason(model.getReason())
                .submissionId(model.getSubmissionId())
                .grantedBy(model.getGrantedBy())
                .grantedByName(model.getGrantedByName())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
