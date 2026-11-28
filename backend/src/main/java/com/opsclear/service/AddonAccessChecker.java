package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.model.OrgAccessState;
import com.opsclear.repository.OrgSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single add-on access rule, used by {@link com.opsclear.aop.RequiresAddonAspect} for gated endpoints and by
 * services whose business rules depend on an add-on (ADR-0052: the completion gate needs {@code APPROVALS}). It reads
 * the org's access state from {@link OrgAccessService} (ADR-0067).
 * <ul>
 *   <li>An internal org can use every add-on, reads and writes, whatever its billing state.</li>
 *   <li>A trial org can use every add-on, reads and writes, like an internal org (ADR-0067).</li>
 *   <li>A paid org (real Paddle billing: a webhook-confirmed, non-cancelled subscription) needs the add-on in that
 *       subscription; a staged selection alone never counts (JOB-200).</li>
 *   <li>While the subscription is {@code PAST_DUE}, add-on reads stay available but writes are blocked
 *       (ADR-0044).</li>
 *   <li>A locked org has no add-on access while the lock-out switch is on (it gets 402 before any add-on check
 *       anyway). While the switch is off it keeps every add-on, like a trial (ADR-0067 amendment).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AddonAccessChecker {

    private static final String PAST_DUE = "PAST_DUE";

    private final OrgSubscriptionRepository subscriptionRepository;
    private final OrgAccessService orgAccessService;

    /** Whether the org can read {@code addon}: internal, trial, locked with the switch off, or paid with the add-on. */
    public boolean hasAddonAccess(UUID orgId, AddonCode addon) {
        return hasAccess(orgId, addon, false);
    }

    /**
     * Whether the org can use {@code addon} for writes right now: as {@link #hasAddonAccess}, except that a paid
     * subscription must not be {@code PAST_DUE}. This is the check for turning on, and (ADR-0052) enforcing, the
     * completion approval gate: when it's false nobody could decide completion approvals.
     */
    public boolean isAddonUsableForWrites(UUID orgId, AddonCode addon) {
        return hasAccess(orgId, addon, true);
    }

    /** Internal orgs skip every add-on and billing check. */
    public boolean isInternal(UUID orgId) {
        return subscriptionRepository.isInternal(orgId);
    }

    /** The subscription status is {@code PAST_DUE}: add-on writes are blocked (ADR-0044). */
    public boolean isPastDue(UUID orgId) {
        return subscriptionRepository.findSubscriptionStatus(orgId)
                .filter(PAST_DUE::equals)
                .isPresent();
    }

    private boolean hasAccess(UUID orgId, AddonCode addon, boolean forWrites) {
        OrgAccessState state = orgAccessService.state(orgId);
        return switch (state) {
            case INTERNAL, TRIAL -> true;
            case LOCKED -> !orgAccessService.isLockoutEnabled();
            case PAID -> subscriptionRepository.hasAddon(orgId, addon.name()) && !(forWrites && isPastDue(orgId));
        };
    }
}
