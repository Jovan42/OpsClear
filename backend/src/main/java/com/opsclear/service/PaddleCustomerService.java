package com.opsclear.service;

import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.PaddleSyncException;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleCustomer;
import com.opsclear.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * Gets the Paddle customer for an org that has none yet (JOB-396). Paddle customer
 * emails are unique per Paddle account, so an owner whose email already has a
 * customer (an earlier org, an earlier sandbox run) gets 409 customer_already_exists
 * from {@link PaddleClient#createCustomer}. That customer is reused, unless another
 * org already holds it: webhooks map a customer to exactly one org
 * ({@link OrganisationRepository#findIdByPaddleCustomerId}). Any other Paddle failure
 * is a 502 with a clear message, never the generic 500.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaddleCustomerService {

    private final PaddleClient paddleClient;
    private final OrganisationRepository organisationRepository;

    /** Only call this for an org without a {@code paddle_customer_id}. */
    public PaddleCustomer createOrReuse(UUID orgId, String email, String name) {
        try {
            return paddleClient.createCustomer(email, name);
        } catch (HttpClientErrorException.Conflict e) {
            log.info("Paddle already has a customer for the owner of org {}, reusing it", orgId);
            return reuseExisting(orgId, email);
        } catch (RestClientException e) {
            log.error("Paddle customer creation failed for org {}: {}", orgId, e.getMessage());
            throw new PaddleSyncException(ErrorMessages.Paddle.CUSTOMER_SETUP_FAILED);
        }
    }

    private PaddleCustomer reuseExisting(UUID orgId, String email) {
        Optional<PaddleCustomer> existing;
        try {
            existing = paddleClient.findCustomerByEmail(email);
        } catch (RestClientException e) {
            log.error("Paddle customer lookup failed for org {}: {}", orgId, e.getMessage());
            throw new PaddleSyncException(ErrorMessages.Paddle.CUSTOMER_SETUP_FAILED);
        }
        PaddleCustomer customer = existing.orElseThrow(() -> {
            log.error("Paddle reported an existing customer for org {}'s owner but the lookup found none", orgId);
            return new PaddleSyncException(ErrorMessages.Paddle.CUSTOMER_SETUP_FAILED);
        });
        // Can't be this org: callers only get here for an org without a customer id.
        Optional<UUID> holder = organisationRepository.findIdByPaddleCustomerId(customer.id());
        if (holder.isPresent()) {
            log.warn("Paddle customer {} for org {}'s owner is already linked to org {}",
                    customer.id(), orgId, holder.get());
            throw new ConflictException(ErrorMessages.Paddle.CUSTOMER_EMAIL_BELONGS_TO_ANOTHER_ORG);
        }
        return customer;
    }
}
