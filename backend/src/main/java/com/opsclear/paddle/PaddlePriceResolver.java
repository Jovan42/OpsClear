package com.opsclear.paddle;

import java.util.UUID;

/**
 * Resolves a subscription tier or add-on to its Paddle Price ID. JOB-176 backs this
 * with a real {@code paddle_price_id} column on {@code subscription_tiers}/
 * {@code subscription_addons} — that column doesn't exist yet (it's explicitly
 * JOB-176's own DB change, not JOB-173's), so this is a seam: {@link PaddleSubscriptionService}
 * depends only on this interface, and JOB-176 swaps in a real implementation without
 * touching the service's logic. See {@link NotYetImplementedPaddlePriceResolver}.
 */
public interface PaddlePriceResolver {

    String resolveTierPriceId(UUID tierId);

    String resolveAddonPriceId(UUID addonId);
}
