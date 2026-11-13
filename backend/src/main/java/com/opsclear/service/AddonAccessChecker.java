package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.repository.OrgSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Service-level add-on check, for business rules that depend on an add-on without the whole
 * endpoint being gated (e.g. ADR-0052: turning the completion gate on needs {@code APPROVALS}).
 * Same rule as {@link com.opsclear.aop.RequiresAddonAspect}: internal orgs always pass, otherwise
 * the org needs real Paddle billing and the add-on in its subscription.
 */
@Component
@RequiredArgsConstructor
public class AddonAccessChecker {

    private final OrgSubscriptionRepository subscriptionRepository;

    public boolean hasAddon(UUID orgId, AddonCode addon) {
        if (subscriptionRepository.isInternal(orgId)) {
            return true;
        }
        return subscriptionRepository.hasRealBilling(orgId)
                && subscriptionRepository.hasAddon(orgId, addon.name());
    }
}
