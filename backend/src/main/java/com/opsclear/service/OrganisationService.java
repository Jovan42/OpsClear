package com.opsclear.service;

import com.opsclear.config.TrialProperties;
import com.opsclear.dto.CreateOrganisationRequest;
import com.opsclear.dto.UpdateOrganisationRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final FriendlyIdService friendlyIdService;
    private final TrialProperties trialProperties;
    private final boolean signupOpen;

    public OrganisationService(OrganisationRepository organisationRepository,
                               UserRepository userRepository,
                               FriendlyIdService friendlyIdService,
                               TrialProperties trialProperties,
                               @Value("${opsclear.signup.open:true}") boolean signupOpen) {
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
        this.friendlyIdService = friendlyIdService;
        this.trialProperties = trialProperties;
        this.signupOpen = signupOpen;
    }

    /** Whether new organisations can be created (ADR-0066). Super users can always create one. */
    public boolean isSignupOpen() {
        return signupOpen;
    }

    @Transactional
    public OrganisationModel create(CreateOrganisationRequest request, UUID callerId) {
        UserModel caller = requireUser(callerId);
        requireSignupOpenOrSuperUser(caller);
        requireNoExistingOrganisation(callerId);
        requireSlugAvailable(request.getSlug());

        OrganisationModel org = OrganisationModel.builder()
                .name(request.getName())
                .slug(request.getSlug().toUpperCase())
                .createdBy(callerId)
                .trialEndsAt(newTrialEndsAt())
                .build();

        // JOB-238: requireSlugAvailable() above is a check-then-act race — two concurrent
        // requests for the same never-used slug can both pass it before either commits.
        // The partial unique index (V039) is the real guard; this turns its violation into
        // the same clean 409 instead of an uncaught DataIntegrityViolationException 500.
        try {
            org = organisationRepository.save(org);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ErrorMessages.Organisation.SLUG_ALREADY_EXISTS);
        }
        organisationRepository.saveMember(org.getId(), callerId, OrganisationRole.OWNER);
        friendlyIdService.seedForOrg(org.getId());

        log.info("Created organisation '{}' (slug={}) by user {}", org.getName(), org.getSlug(), callerId);
        return org;
    }

    @Transactional(readOnly = true)
    public OrganisationModel getById(UUID orgId, UUID callerId) {
        OrganisationModel org = requireOrg(orgId);
        requireMember(orgId, callerId);
        return org;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<OrganisationModel> getMyOrganisation(UUID callerId) {
        return organisationRepository.findByMember(callerId);
    }

    @Transactional
    public OrganisationModel update(UUID orgId, UpdateOrganisationRequest request, UUID callerId) {
        OrganisationModel org = requireOrg(orgId);
        requireOwner(orgId, callerId);
        requireSlugAvailableForUpdate(request.getSlug(), orgId);

        org.setName(request.getName());
        org.setSlug(request.getSlug().toUpperCase());

        log.info("Updated organisation '{}' by user {}", orgId, callerId);
        try {
            return organisationRepository.save(org);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ErrorMessages.Organisation.SLUG_ALREADY_EXISTS);
        }
    }

    @Transactional
    public void softDelete(UUID orgId, UUID callerId) {
        OrganisationModel org = requireOrg(orgId);
        requireOwner(orgId, callerId);
        org.softDelete();
        organisationRepository.save(org);
        log.info("Soft-deleted organisation '{}' by user {}", orgId, callerId);
    }

    /**
     * ADR-0067: every new org starts a trial of {@code opsclear.trial.days}. The one place a new org's trial end is
     * decided; a redeemed sign-up code (ADR-0066, JOB-443) overrides it here, in the same transaction.
     */
    private Instant newTrialEndsAt() {
        return Instant.now().plus(Duration.ofDays(trialProperties.days()));
    }

    // --- Guards ---

    private OrganisationModel requireOrg(UUID orgId) {
        return organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
    }

    private UserModel requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));
    }

    private void requireSignupOpenOrSuperUser(UserModel caller) {
        // ADR-0066: closed until launch for everyone except super users (test orgs on prod). Invite accept
        // joins an existing org and never comes through here.
        if (!signupOpen && !caller.isSuperUser()) {
            throw new ForbiddenException(ErrorMessages.Organisation.SIGNUP_CLOSED);
        }
    }

    private void requireMember(UUID orgId, UUID userId) {
        organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_A_MEMBER));
    }

    private void requireOwner(UUID orgId, UUID userId) {
        OrganisationRole role = organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_A_MEMBER));
        if (role != OrganisationRole.OWNER) {
            throw new ForbiddenException(ErrorMessages.Organisation.INSUFFICIENT_PERMISSIONS_OWNER);
        }
    }

    private void requireNoExistingOrganisation(UUID callerId) {
        // JOB-241: the rest of the app (GET /organisations/mine, OrgContext,
        // OrgRequiredRoute) assumes exactly one org per user — without this guard a
        // second create() silently succeeded, leaving the caller a member of two and
        // /mine returning whichever one findByMember's unordered `.limit(1)` happened
        // to pick.
        if (organisationRepository.findByMember(callerId).isPresent()) {
            throw new ConflictException(ErrorMessages.Organisation.ALREADY_IN_ORGANISATION);
        }
    }

    private void requireSlugAvailable(String slug) {
        if (organisationRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new ConflictException(ErrorMessages.Organisation.SLUG_ALREADY_EXISTS);
        }
    }

    private void requireSlugAvailableForUpdate(String slug, UUID excludeId) {
        if (organisationRepository.existsBySlugAndIdNotAndDeletedAtIsNull(slug, excludeId)) {
            throw new ConflictException(ErrorMessages.Organisation.SLUG_ALREADY_EXISTS);
        }
    }
}
