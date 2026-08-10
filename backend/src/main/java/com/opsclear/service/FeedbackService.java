package com.opsclear.service;

import com.opsclear.dto.SubmitFeedbackRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.FeedbackStatus;
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
    // the submission belongs to the org being credited, and that it's still
    // PENDING, before marking it CREDITED.
    @Transactional
    public void markCreditedForOrg(UUID submissionId, UUID orgId) {
        FeedbackSubmissionModel submission = requireSubmissionForOrg(submissionId, orgId);
        requireStatusIsPending(submission);
        feedbackSubmissionRepository.updateStatus(submissionId, FeedbackStatus.CREDITED);
    }

    // Closes out a submission the admin decided not to reward — status alone then
    // tells you the outcome (PENDING / DECLINED / CREDITED), no separate flag needed.
    @Transactional
    public void decline(UUID submissionId) {
        FeedbackSubmissionModel submission = requireSubmission(submissionId);
        requireStatusIsPending(submission);
        feedbackSubmissionRepository.updateStatus(submissionId, FeedbackStatus.DECLINED);
    }

    // --- Guards ---

    private OrganisationModel requireCallerOrg(UUID callerId) {
        return organisationRepository.findByMember(callerId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
    }

    private FeedbackSubmissionModel requireSubmission(UUID submissionId) {
        return feedbackSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Feedback.NOT_FOUND));
    }

    private FeedbackSubmissionModel requireSubmissionForOrg(UUID submissionId, UUID orgId) {
        return feedbackSubmissionRepository.findByIdAndOrgId(submissionId, orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Feedback.NOT_FOUND));
    }

    // Rejects a submission that's already been resolved (declined or credited) so
    // it can't be double-credited or re-declined, whether from a stray double-click
    // or a direct API call.
    private void requireStatusIsPending(FeedbackSubmissionModel submission) {
        if (submission.getStatus() != FeedbackStatus.PENDING) {
            throw new ConflictException(ErrorMessages.Feedback.ALREADY_REVIEWED);
        }
    }
}
