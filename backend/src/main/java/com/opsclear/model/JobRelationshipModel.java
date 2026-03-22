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
public class JobRelationshipModel {

    private UUID id;
    private UUID sourceJobId;
    private UUID targetJobId;
    private JobRelationshipType type;
    private UUID createdBy;
    private Instant createdAt;
}
