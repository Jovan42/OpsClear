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
public class JobLinkModel {

    private UUID id;
    private UUID jobId;
    private String url;
    private String label;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
