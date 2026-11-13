package com.opsclear.dto;

import com.opsclear.model.PendingCompletionApprovalModel;
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
public class PendingCompletionApprovalResponse {

    private UUID id;
    private UUID requesterId;
    private String requesterName;
    private Instant requestedAt;
    // The request message (the approval's description, or the default text when none was given)
    private String message;

    public static PendingCompletionApprovalResponse from(PendingCompletionApprovalModel model) {
        return PendingCompletionApprovalResponse.builder()
                .id(model.getId())
                .requesterId(model.getRequesterId())
                .requesterName(model.getRequesterName())
                .requestedAt(model.getRequestedAt())
                .message(model.getMessage())
                .build();
    }
}
