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
public class FeedbackSubmissionModel {

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
}
