package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgSubscriptionModel {

    private UUID id;
    private UUID orgId;
    private UUID tierId;
    private String billingCycle;
    private boolean isInternal;
    private Instant createdAt;
    private Instant updatedAt;
    private List<UUID> addonIds;
    private String paddleSubscriptionId;
    private String subscriptionStatus;
    private Instant paddleScheduledCancellationAt;
    private Instant paddleCurrentPeriodStartsAt;
    private UUID pendingTierId;
    private List<UUID> pendingAddonIds;
    private Instant paddlePendingDowngradeEffectiveAt;
}
