package com.opsclear.service;

import com.opsclear.dto.AddOrgMemberRequest;
import com.opsclear.dto.UpdateOrgMemberRoleRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrgMemberModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganisationMemberService {

    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<OrgMemberModel> listMembers(UUID orgId, UUID callerId) {
        requireOrgExists(orgId);
        requireMember(orgId, callerId);
        return organisationRepository.findMembers(orgId);
    }

    @Transactional
    public OrgMemberModel addMember(UUID orgId, UUID callerId, AddOrgMemberRequest request) {
        requireOrgExists(orgId);
        requireOwnerOrAdmin(orgId, callerId);
        requireRoleAssignable(request.getRole());
        requireUserExists(request.getUserId());
        requireNotAlreadyMember(orgId, request.getUserId());

        organisationRepository.saveMember(orgId, request.getUserId(), request.getRole());
        log.info("Added user {} as {} to organisation {}", request.getUserId(), request.getRole(), orgId);

        return requireMemberRecord(orgId, request.getUserId());
    }

    @Transactional
    public OrgMemberModel updateRole(UUID orgId, UUID callerId, UUID targetUserId,
                                     UpdateOrgMemberRoleRequest request) {
        requireOrgExists(orgId);
        requireOwner(orgId, callerId);
        requireRoleAssignable(request.getRole());

        OrgMemberModel target = requireMemberRecord(orgId, targetUserId);
        requireTargetNotOwner(target, ErrorMessages.Organisation.CANNOT_CHANGE_OWNER_ROLE);

        organisationRepository.updateMemberRole(orgId, targetUserId, request.getRole());
        log.info("Updated user {} role to {} in organisation {}", targetUserId, request.getRole(), orgId);

        return requireMemberRecord(orgId, targetUserId);
    }

    @Transactional
    public void removeMember(UUID orgId, UUID callerId, UUID targetUserId) {
        requireOrgExists(orgId);
        requireOwnerOrAdmin(orgId, callerId);

        OrgMemberModel target = requireMemberRecord(orgId, targetUserId);
        requireTargetNotOwner(target, ErrorMessages.Organisation.CANNOT_REMOVE_OWNER);

        organisationRepository.deleteMember(orgId, targetUserId);
        log.info("Removed user {} from organisation {}", targetUserId, orgId);
    }

    // --- Business rule guards ---

    private void requireRoleAssignable(OrganisationRole role) {
        if (role == OrganisationRole.OWNER) {
            throw new ForbiddenException(ErrorMessages.Organisation.CANNOT_ASSIGN_OWNER_ROLE);
        }
    }

    private void requireNotAlreadyMember(UUID orgId, UUID userId) {
        if (organisationRepository.existsMember(orgId, userId)) {
            throw new ConflictException(ErrorMessages.Organisation.ALREADY_A_MEMBER);
        }
    }

    private void requireTargetNotOwner(OrgMemberModel target, String message) {
        if (target.getRole() == OrganisationRole.OWNER) {
            throw new ForbiddenException(message);
        }
    }

    // --- Guards ---

    private void requireOrgExists(UUID orgId) {
        organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
    }

    private void requireUserExists(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));
    }

    private OrgMemberModel requireMemberRecord(UUID orgId, UUID userId) {
        return organisationRepository.findMember(orgId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.MEMBER_NOT_FOUND));
    }

    // --- Permission helpers ---

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

    private void requireOwnerOrAdmin(UUID orgId, UUID userId) {
        OrganisationRole role = organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_A_MEMBER));
        if (role == OrganisationRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Organisation.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }
}
