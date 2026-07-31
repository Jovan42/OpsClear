package com.opsclear.dto;

import com.opsclear.model.FeedbackStatus;
import com.opsclear.model.FeedbackSubmissionModel;
import com.opsclear.model.FeedbackType;
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
public class FeedbackSubmissionResponse {

    private UUID id;
    private UUID orgId;
    private String orgName;
    private UUID submittedBy;
    private String submitterName;
    private String submitterEmail;
    private FeedbackType type;
    private String title;
    private String description;
    private FeedbackStatus status;
    private Instant createdAt;

    public static FeedbackSubmissionResponse from(FeedbackSubmissionModel model) {
        return FeedbackSubmissionResponse.builder()
                .id(model.getId())
                .orgId(model.getOrgId())
                .orgName(model.getOrgName())
                .submittedBy(model.getSubmittedBy())
                .submitterName(model.getSubmitterName())
                .submitterEmail(model.getSubmitterEmail())
                .type(model.getType())
                .title(model.getTitle())
                .description(model.getDescription())
                .status(model.getStatus())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
