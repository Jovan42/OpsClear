package com.opsclear.service;

import com.opsclear.dto.SubmitFeedbackRequest;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.FeedbackSubmissionModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.FeedbackSubmissionRepository;
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
public class FeedbackService {

    private final FeedbackSubmissionRepository feedbackSubmissionRepository;
    private final OrganisationRepository organisationRepository;

    @Transactional
    public FeedbackSubmissionModel submit(UUID callerId, SubmitFeedbackRequest request) {
        OrganisationModel org = requireCallerOrg(callerId);
        FeedbackSubmissionModel submission = feedbackSubmissionRepository.insert(
                org.getId(), callerId, request.getType(),
                request.getTitle().strip(), request.getDescription().strip());
        log.info("Feedback submission {} ({}) created by user {} for org {}",
                submission.getId(), submission.getType(), callerId, org.getId());
        return submission;
    }

    @Transactional(readOnly = true)
    public List<FeedbackSubmissionModel> listMine(UUID callerId) {
        return feedbackSubmissionRepository.findBySubmittedBy(callerId);
    }

    @Transactional(readOnly = true)
    public List<FeedbackSubmissionModel> listAll() {
        return feedbackSubmissionRepository.findAll();
    }

    // Called by CreditService when a grant links back to a submission — validates
    // the submission belongs to the org being credited before marking it reviewed.
    @Transactional
    public void markReviewedForOrg(UUID submissionId, UUID orgId) {
        feedbackSubmissionRepository.findByIdAndOrgId(submissionId, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Feedback.NOT_FOUND));
        feedbackSubmissionRepository.markReviewed(submissionId);
    }

    private OrganisationModel requireCallerOrg(UUID callerId) {
        return organisationRepository.findByMember(callerId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
    }
}
