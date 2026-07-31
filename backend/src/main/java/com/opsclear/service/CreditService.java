package com.opsclear.service;

import com.opsclear.dto.GrantCreditRequest;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrgCreditModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.repository.OrgCreditRepository;
import com.opsclear.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditService {

    private final OrgCreditRepository orgCreditRepository;
    private final OrganisationRepository organisationRepository;
    private final FeedbackService feedbackService;

    @Transactional
    public OrgCreditModel grant(UUID grantedBy, GrantCreditRequest request) {
        requireOrgExists(request.getOrgId());
        if (request.getSubmissionId() != null) {
            feedbackService.markReviewedForOrg(request.getSubmissionId(), request.getOrgId());
        }
        OrgCreditModel credit = orgCreditRepository.insert(
                request.getOrgId(), request.getAmount(), request.getReason().strip(),
                request.getSubmissionId(), grantedBy);
        log.info("Granted {} credit to org {} by user {} (submission={})",
                credit.getAmount(), credit.getOrgId(), grantedBy, credit.getSubmissionId());
        return credit;
    }

    @Transactional(readOnly = true)
    public int getBalance(UUID orgId, UUID callerId) {
        requireOwnerOrAdmin(orgId, callerId);
        return orgCreditRepository.sumByOrgId(orgId);
    }

    @Transactional(readOnly = true)
    public List<OrgCreditModel> getLedger(UUID orgId) {
        requireOrgExists(orgId);
        return orgCreditRepository.findByOrgId(orgId);
    }

    private void requireOrgExists(UUID orgId) {
        organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
    }

    private void requireOwnerOrAdmin(UUID orgId, UUID userId) {
        OrganisationRole role = organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
        if (role == OrganisationRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Organisation.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }
}
