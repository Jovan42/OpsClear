package com.opsclear.paddle;

import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link PaddlePriceResolver} until JOB-176 lands. Fails loudly and
 * predictably (409, not a raw 500) rather than silently resolving to a wrong or
 * fabricated Price ID.
 */
@Component
public class NotYetImplementedPaddlePriceResolver implements PaddlePriceResolver {

    @Override
    public String resolveTierPriceId(UUID tierId) {
        throw new ConflictException(ErrorMessages.Paddle.PRICE_SYNC_NOT_IMPLEMENTED);
    }

    @Override
    public String resolveAddonPriceId(UUID addonId) {
        throw new ConflictException(ErrorMessages.Paddle.PRICE_SYNC_NOT_IMPLEMENTED);
    }
}
