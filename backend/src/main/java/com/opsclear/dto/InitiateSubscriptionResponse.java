package com.opsclear.dto;

import com.opsclear.model.OrgSubscriptionModel;
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
public class InitiateSubscriptionResponse {

    private UUID orgId;
    private String paddleCustomerId;

    public static InitiateSubscriptionResponse from(OrgSubscriptionModel model) {
        return InitiateSubscriptionResponse.builder()
                .orgId(model.getOrgId())
                .paddleCustomerId(model.getPaddleCustomerId())
                .build();
    }
}
