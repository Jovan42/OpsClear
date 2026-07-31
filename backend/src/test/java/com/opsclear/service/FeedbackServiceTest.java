package com.opsclear.service;

import com.opsclear.dto.SubmitFeedbackRequest;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.FeedbackStatus;
import com.opsclear.model.FeedbackSubmissionModel;
import com.opsclear.model.FeedbackType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.FeedbackSubmissionRepository;
import com.opsclear.repository.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackService")
class FeedbackServiceTest {

    @Mock private FeedbackSubmissionRepository feedbackSubmissionRepository;
    @Mock private OrganisationRepository organisationRepository;

    private FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(feedbackSubmissionRepository, organisationRepository);
    }

    // --- submit ---

    @Test
    @DisplayName("submit creates a submission for the caller's org")
    void submit_shouldCreateSubmission_forCallersOrg() {
        UUID callerId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .type(FeedbackType.BUG)
                .title("  Broken button  ")
                .description("  It does nothing  ")
                .build();
        FeedbackSubmissionModel created = FeedbackSubmissionModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).submittedBy(callerId)
                .type(FeedbackType.BUG).title("Broken button").description("It does nothing")
                .status(FeedbackStatus.PENDING).build();

        when(organisationRepository.findByMember(callerId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(feedbackSubmissionRepository.insert(orgId, callerId, FeedbackType.BUG, "Broken button", "It does nothing"))
                .thenReturn(created);

        FeedbackSubmissionModel result = feedbackService.submit(callerId, request);

        assertThat(result).isEqualTo(created);
        verify(feedbackSubmissionRepository)
                .insert(orgId, callerId, FeedbackType.BUG, "Broken button", "It does nothing");
    }

    @Test
    @DisplayName("submit throws NotFoundException when the caller has no organisation")
    void submit_shouldThrow_whenCallerHasNoOrg() {
        UUID callerId = UUID.randomUUID();
        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .type(FeedbackType.OTHER).title("Title").description("Description").build();

        when(organisationRepository.findByMember(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedbackService.submit(callerId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Organisation not found");
        verify(feedbackSubmissionRepository, never()).insert(any(), any(), any(), any(), any());
    }

    // --- listMine / listAll ---

    @Test
    @DisplayName("listMine returns the caller's own submissions")
    void listMine_shouldReturnCallersSubmissions() {
        UUID callerId = UUID.randomUUID();
        List<FeedbackSubmissionModel> submissions = List.of(
                FeedbackSubmissionModel.builder().id(UUID.randomUUID()).submittedBy(callerId).build());
        when(feedbackSubmissionRepository.findBySubmittedBy(callerId)).thenReturn(submissions);

        assertThat(feedbackService.listMine(callerId)).isEqualTo(submissions);
    }

    @Test
    @DisplayName("listAll returns every submission across all orgs")
    void listAll_shouldReturnEverySubmission() {
        List<FeedbackSubmissionModel> submissions = List.of(
                FeedbackSubmissionModel.builder().id(UUID.randomUUID()).build());
        when(feedbackSubmissionRepository.findAll()).thenReturn(submissions);

        assertThat(feedbackService.listAll()).isEqualTo(submissions);
    }

    // --- markReviewedForOrg ---

    @Test
    @DisplayName("markReviewedForOrg marks the submission reviewed when it belongs to the org")
    void markReviewedForOrg_shouldMarkReviewed_whenSubmissionBelongsToOrg() {
        UUID orgId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        when(feedbackSubmissionRepository.findByIdAndOrgId(submissionId, orgId))
                .thenReturn(Optional.of(FeedbackSubmissionModel.builder().id(submissionId).orgId(orgId).build()));

        feedbackService.markReviewedForOrg(submissionId, orgId);

        verify(feedbackSubmissionRepository).markReviewed(submissionId);
    }

    @Test
    @DisplayName("markReviewedForOrg throws NotFoundException when the submission belongs to a different org")
    void markReviewedForOrg_shouldThrow_whenSubmissionBelongsToDifferentOrg() {
        UUID orgId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        when(feedbackSubmissionRepository.findByIdAndOrgId(submissionId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedbackService.markReviewedForOrg(submissionId, orgId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Feedback submission not found");
        verify(feedbackSubmissionRepository, never()).markReviewed(eq(submissionId));
    }
}
