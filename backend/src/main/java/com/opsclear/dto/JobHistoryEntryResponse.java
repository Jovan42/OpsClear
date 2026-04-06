package com.opsclear.dto;

import com.opsclear.model.JobStatusHistoryModel;
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
public class JobHistoryEntryResponse {

    private UUID id;
    private String changedFrom;
    private String changedTo;
    private UUID changedBy;
    private String changedByName;
    private Instant changedAt;
    private String blockReason;

    public static JobHistoryEntryResponse from(JobStatusHistoryModel model) {
        return JobHistoryEntryResponse.builder()
                .id(model.getId())
                .changedFrom(model.getChangedFrom())
                .changedTo(model.getChangedTo())
                .changedBy(model.getChangedBy())
                .changedByName(model.getChangedByName())
                .changedAt(model.getChangedAt())
                .blockReason(model.getBlockReason())
                .build();
    }
}
