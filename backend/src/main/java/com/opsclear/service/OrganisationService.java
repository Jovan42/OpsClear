package com.opsclear.service;

import com.opsclear.dto.CreateOrganisationRequest;
import com.opsclear.dto.UpdateOrganisationRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final FriendlyIdService friendlyIdService;

    @Transactional
    public OrganisationModel create(CreateOrganisationRequest request, UUID callerId) {
        requireUserExists(callerId);
        requireSlugAvailable(request.getSlug());

        OrganisationModel org = OrganisationModel.builder()
                .name(request.getName())
                .slug(request.getSlug().toUpperCase())
                .createdBy(callerId)
                .build();

        org = organisationRepository.save(org);
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
        return organisationRepository.save(org);
    }

    @Transactional
    public void softDelete(UUID orgId, UUID callerId) {
        OrganisationModel org = requireOrg(orgId);
        requireOwner(orgId, callerId);
        org.softDelete();
        organisationRepository.save(org);
        log.info("Soft-deleted organisation '{}' by user {}", orgId, callerId);
    }

    // --- Guards ---

    private OrganisationModel requireOrg(UUID orgId) {
        return organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
    }

    private void requireUserExists(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));
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
