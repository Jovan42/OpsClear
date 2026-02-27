package com.opsclear.dto;

import com.opsclear.model.BlockReasonModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockReasonResponse {

    private UUID id;
    private String reason;

    public static BlockReasonResponse from(BlockReasonModel model) {
        return BlockReasonResponse.builder()
                .id(model.getId())
                .reason(model.getReason())
                .build();
    }
}
