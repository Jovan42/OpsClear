package com.opsclear.service;

import com.opsclear.config.LockoutProperties;
import com.opsclear.config.TrialProperties;
import com.opsclear.model.OrgAccessModel;
import com.opsclear.model.OrgAccessState;
import com.opsclear.model.OrgLimits;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The one place that answers "what can this org do right now" (ADR-0067): its access state, its member and project
 * limits, and whether the lock-out switch is on. {@link AddonAccessChecker} and the lock-out interceptor build on it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgAccessService {

    private final OrgSubscriptionRepository subscriptionRepository;
    private final SubscriptionTierRepository tierRepository;
    private final OrganisationRepository organisationRepository;
    private final OrgInviteRepository inviteRepository;
    private final ProjectRepository projectRepository;
    private final TrialProperties trialProperties;
    private final LockoutProperties lockoutProperties;

    /**
     * {@code INTERNAL}, then {@code PAID} (real billing, {@code PAST_DUE} included), then {@code TRIAL}
     * ({@code trial_ends_at} in the future), else {@code LOCKED}. So a confirmed Paddle subscription ends the trial at
     * once, and an org that never had a trial ({@code trial_ends_at} NULL) is locked unless it's internal or paid.
     */
    @Transactional(readOnly = true)
    public OrgAccessState state(UUID orgId) {
        return stateFor(orgId, organisationRepository.findTrialEndsAt(orgId));
    }

    /** Internal: unlimited. Paid: the subscription tier's caps. Trial and locked: the trial properties. */
    @Transactional(readOnly = true)
    public OrgLimits limits(UUID orgId) {
        return limitsFor(orgId, state(orgId));
    }

    /** State, trial end, limits and current usage, for {@code access} in the organisation response. */
    @Transactional(readOnly = true)
    public OrgAccessModel access(UUID orgId) {
        Optional<Instant> trialEndsAt = organisationRepository.findTrialEndsAt(orgId);
        OrgAccessState state = stateFor(orgId, trialEndsAt);
        OrgLimits limits = limitsFor(orgId, state);
        return OrgAccessModel.builder()
                .state(state)
                .trialEndsAt(trialEndsAt.orElse(null))
                .maxMembers(limits.getMaxMembers())
                .maxProjects(limits.getMaxProjects())
                .memberCount(organisationRepository.countMembers(orgId))
                .pendingInviteCount(inviteRepository.countPendingByOrgId(orgId))
                .activeProjectCount(projectRepository.countActiveByOrgId(orgId))
                .lockoutEnabled(lockoutProperties.enabled())
                .build();
    }

    /** The lock-out switch: while {@code false}, a {@code LOCKED} org gets no 402 and keeps add-on access. */
    public boolean isLockoutEnabled() {
        return lockoutProperties.enabled();
    }

    /** {@code LOCKED} and the switch is on: every non-allowlisted API call gets 402 {@code OrgLocked}. */
    @Transactional(readOnly = true)
    public boolean isLockedOut(UUID orgId) {
        return lockoutProperties.enabled() && state(orgId) == OrgAccessState.LOCKED;
    }

    private OrgAccessState stateFor(UUID orgId, Optional<Instant> trialEndsAt) {
        if (subscriptionRepository.isInternal(orgId)) {
            return OrgAccessState.INTERNAL;
        }
        if (subscriptionRepository.hasRealBilling(orgId)) {
            return OrgAccessState.PAID;
        }
        Instant now = Instant.now();
        if (trialEndsAt.filter(end -> end.isAfter(now)).isPresent()) {
            return OrgAccessState.TRIAL;
        }
        return OrgAccessState.LOCKED;
    }

    private OrgLimits limitsFor(UUID orgId, OrgAccessState state) {
        return switch (state) {
            case INTERNAL -> OrgLimits.unlimited();
            case PAID -> tierLimits(orgId);
            case TRIAL, LOCKED -> new OrgLimits(trialProperties.maxMembers(), trialProperties.maxProjects());
        };
    }

    // A PAID org always has a subscription row, and tier_id is NOT NULL with a foreign key.
    private OrgLimits tierLimits(UUID orgId) {
        OrgSubscriptionModel subscription = subscriptionRepository.findByOrgId(orgId).orElseThrow();
        SubscriptionTierModel tier = tierRepository.findById(subscription.getTierId()).orElseThrow();
        return new OrgLimits(tier.getMaxMembers(), tier.getMaxProjects());
    }
}
