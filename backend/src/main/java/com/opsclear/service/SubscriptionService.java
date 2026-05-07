package com.opsclear.service;

import com.opsclear.dto.OrgSubscriptionResponse;
import com.opsclear.dto.UpsertSubscriptionRequest;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final OrgSubscriptionRepository subscriptionRepository;
    private final SubscriptionTierRepository tierRepository;
    private final SubscriptionAddonRepository addonRepository;
    private final OrganisationRepository organisationRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public OrgSubscriptionResponse getSubscription(UUID orgId, UUID requesterId) {
        requireMember(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscription(orgId);
        return buildResponse(subscription);
    }

    @Transactional
    public OrgSubscriptionResponse upsertSubscription(UUID orgId, UUID requesterId, UpsertSubscriptionRequest request) {
        requireOwner(orgId, requesterId);

        SubscriptionTierModel newTier = requireTier(request.getTierId());
        Set<UUID> addonIds = request.getAddonIds() != null ? new HashSet<>(request.getAddonIds()) : new HashSet<>();

        OrgSubscriptionModel existing = subscriptionRepository.findByOrgId(orgId).orElse(null);

        if (existing == null || !existing.isInternal()) {
            validateDowngrade(orgId, newTier);
        }

        OrgSubscriptionModel result;
        if (existing == null) {
            result = subscriptionRepository.create(orgId, newTier.getId(), request.getBillingCycle(), addonIds);
        } else {
            result = subscriptionRepository.update(existing.getId(), orgId, newTier.getId(), request.getBillingCycle(), addonIds);
        }

        log.info("Subscription updated for org {}: tier={}, cycle={}", orgId, newTier.getId(), request.getBillingCycle());
        return buildResponse(result);
    }

    // ─── Guard methods ────────────────────────────────────────────────────────

    private OrgSubscriptionModel requireSubscription(UUID orgId) {
        return subscriptionRepository.findByOrgId(orgId)
                .orElseThrow(() -> new NotFoundException("No subscription found for this organisation"));
    }

    private SubscriptionTierModel requireTier(UUID tierId) {
        return tierRepository.findById(tierId)
                .orElseThrow(() -> new NotFoundException("Subscription tier not found"));
    }

    private void requireMember(UUID orgId, UUID userId) {
        if (organisationRepository.findMemberRole(orgId, userId).isEmpty()) {
            throw new NotFoundException("Organisation not found");
        }
    }

    private void requireOwner(UUID orgId, UUID userId) {
        OrganisationRole role = organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new NotFoundException("Organisation not found"));
        if (role != OrganisationRole.OWNER) {
            throw new ForbiddenException("Only the organisation owner can manage subscriptions");
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void validateDowngrade(UUID orgId, SubscriptionTierModel newTier) {
        int memberCount = organisationRepository.countMembers(orgId);
        if (memberCount > newTier.getMaxMembers()) {
            throw new ForbiddenException(String.format(
                    "Cannot downgrade: organisation has %d members but the selected tier allows %d",
                    memberCount, newTier.getMaxMembers()));
        }

        if (newTier.getMaxProjects() != null) {
            int projectCount = projectRepository.countActiveByOrgId(orgId);
            if (projectCount > newTier.getMaxProjects()) {
                throw new ForbiddenException(String.format(
                        "Cannot downgrade: organisation has %d active projects but the selected tier allows %d",
                        projectCount, newTier.getMaxProjects()));
            }
        }
    }

    private OrgSubscriptionResponse buildResponse(OrgSubscriptionModel subscription) {
        SubscriptionTierModel tier = requireTier(subscription.getTierId());
        List<SubscriptionAddonModel> addons = subscription.getAddonIds().isEmpty()
                ? List.of()
                : addonRepository.findByIds(new HashSet<>(subscription.getAddonIds()));
        return OrgSubscriptionResponse.from(subscription, tier, addons);
    }
}
