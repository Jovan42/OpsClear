package com.opsclear.paddle;

import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaddlePriceResolverImpl implements PaddlePriceResolver {

    private final SubscriptionTierRepository tierRepository;
    private final SubscriptionAddonRepository addonRepository;

    @Override
    public String resolveTierPriceId(UUID tierId, String billingCycle) {
        SubscriptionTierModel tier = tierRepository.findById(tierId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.SubscriptionTier.NOT_FOUND));
        return requirePriceId(priceIdFor(tier.getPaddlePriceIdMonthly(), tier.getPaddlePriceIdAnnual(), billingCycle));
    }

    @Override
    public String resolveAddonPriceId(UUID addonId, String billingCycle) {
        SubscriptionAddonModel addon = addonRepository.findById(addonId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.SubscriptionAddon.NOT_FOUND));
        String priceId = priceIdFor(addon.getPaddlePriceIdMonthly(), addon.getPaddlePriceIdAnnual(), billingCycle);
        return requirePriceId(priceId);
    }

    private String priceIdFor(String monthlyPriceId, String annualPriceId, String billingCycle) {
        return "ANNUAL".equals(billingCycle) ? annualPriceId : monthlyPriceId;
    }

    private String requirePriceId(String priceId) {
        if (priceId == null) {
            throw new ConflictException(ErrorMessages.Paddle.PRICE_NOT_SYNCED_TO_PADDLE);
        }
        return priceId;
    }
}
