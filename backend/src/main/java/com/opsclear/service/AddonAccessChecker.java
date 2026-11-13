package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.repository.OrgSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single add-on access rule, used by {@link com.opsclear.aop.RequiresAddonAspect} for gated endpoints and by
 * services whose business rules depend on an add-on (ADR-0052: the completion gate needs {@code APPROVALS}).
 * <ul>
 *   <li>An internal org can use every add-on, reads and writes, whatever its billing state.</li>
 *   <li>Otherwise the org needs real Paddle billing (a webhook-confirmed, non-cancelled subscription) and the add-on
 *       in that subscription; a staged selection alone never counts (JOB-200).</li>
 *   <li>While the subscription is {@code PAST_DUE}, add-on reads stay available but writes are blocked
 *       (ADR-0044).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AddonAccessChecker {

    private static final String PAST_DUE = "PAST_DUE";

    private final OrgSubscriptionRepository subscriptionRepository;

    /**
     * Whether the org can use {@code addon} for writes right now: internal, or real billing with the add-on and a
     * subscription that isn't {@code PAST_DUE}. This is the check for turning on, and (ADR-0052) enforcing, the
     * completion approval gate: when it's false nobody could decide completion approvals.
     */
    public boolean isAddonUsableForWrites(UUID orgId, AddonCode addon) {
        if (isInternal(orgId)) {
            return true;
        }
        return hasPaidAddon(orgId, addon) && !isPastDue(orgId);
    }

    /** Internal orgs skip every add-on and billing check. */
    public boolean isInternal(UUID orgId) {
        return subscriptionRepository.isInternal(orgId);
    }

    /** Real Paddle billing and {@code addon} in the subscription. Ignores internal status and {@code PAST_DUE}. */
    public boolean hasPaidAddon(UUID orgId, AddonCode addon) {
        return subscriptionRepository.hasRealBilling(orgId)
                && subscriptionRepository.hasAddon(orgId, addon.name());
    }

    /** The subscription status is {@code PAST_DUE}: add-on writes are blocked (ADR-0044). */
    public boolean isPastDue(UUID orgId) {
        return subscriptionRepository.findSubscriptionStatus(orgId)
                .filter(PAST_DUE::equals)
                .isPresent();
    }
}
