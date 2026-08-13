package com.opsclear.paddle;

import java.util.UUID;

/**
 * Resolves a subscription tier or add-on to its Paddle Price ID for a given billing
 * cycle. Backed by the {@code paddle_price_id_monthly}/{@code paddle_price_id_annual}
 * columns on {@code subscription_tiers}/{@code subscription_addons} (JOB-176) — a
 * tier/addon has a distinct Paddle Price per cycle, so the cycle must be part of the
 * lookup key. This is a seam: {@link com.opsclear.service.PaddleSubscriptionService}
 * depends only on this interface.
 */
public interface PaddlePriceResolver {

    String resolveTierPriceId(UUID tierId, String billingCycle);

    String resolveAddonPriceId(UUID addonId, String billingCycle);
}
