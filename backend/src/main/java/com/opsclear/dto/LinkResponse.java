package com.opsclear.dto;

import com.opsclear.model.JobLinkModel;
import com.opsclear.model.ProjectLinkModel;
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
public class LinkResponse {

    private UUID id;
    private String url;
    private String label;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static LinkResponse from(JobLinkModel link) {
        return LinkResponse.builder()
                .id(link.getId())
                .url(link.getUrl())
                .label(link.getLabel())
                .createdBy(link.getCreatedBy())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }

    public static LinkResponse from(ProjectLinkModel link) {
        return LinkResponse.builder()
                .id(link.getId())
                .url(link.getUrl())
                .label(link.getLabel())
                .createdBy(link.getCreatedBy())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }
}
