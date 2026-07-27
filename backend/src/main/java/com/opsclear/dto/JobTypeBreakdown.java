package com.opsclear.dto;

import com.opsclear.model.JobTypeColor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobTypeBreakdown {

    private UUID typeId;
    private String typeName;
    private JobTypeColor typeColor;
    private int count;
}
