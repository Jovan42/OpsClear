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
public class JobTypeModel {

    private UUID id;
    private UUID projectId;
    private String name;
    private JobTypeColor color;
    private int displayOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
