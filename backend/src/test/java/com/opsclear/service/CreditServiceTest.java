package com.opsclear.service;

import com.opsclear.dto.GrantCreditRequest;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrgCreditModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.repository.OrgCreditRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditService")
class CreditServiceTest {

    @Mock private OrgCreditRepository orgCreditRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private FeedbackService feedbackService;

    private CreditService creditService;

    @BeforeEach
    void setUp() {
        creditService = new CreditService(orgCreditRepository, organisationRepository, feedbackService);
    }

    // --- grant ---

    @Test
    @DisplayName("grant inserts a ledger entry for a purely discretionary grant")
    void grant_shouldInsertLedgerEntry_withoutSubmission() {
        UUID orgId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();
        GrantCreditRequest request = GrantCreditRequest.builder()
                .orgId(orgId).amount(500).reason("  Goodwill  ").build();
        OrgCreditModel inserted = OrgCreditModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).amount(500).reason("Goodwill").grantedBy(grantedBy).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(orgCreditRepository.insert(orgId, 500, "Goodwill", null, grantedBy)).thenReturn(inserted);

        OrgCreditModel result = creditService.grant(grantedBy, request);

        assertThat(result).isEqualTo(inserted);
        verify(feedbackService, never()).markReviewedForOrg(any(), any());
    }

    @Test
    @DisplayName("grant marks the linked submission reviewed when a submissionId is provided")
    void grant_shouldMarkSubmissionReviewed_whenSubmissionIdProvided() {
        UUID orgId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        GrantCreditRequest request = GrantCreditRequest.builder()
                .orgId(orgId).amount(1000).reason("Great bug report").submissionId(submissionId).build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(orgCreditRepository.insert(orgId, 1000, "Great bug report", submissionId, grantedBy))
                .thenReturn(OrgCreditModel.builder().id(UUID.randomUUID()).orgId(orgId).build());

        creditService.grant(grantedBy, request);

        verify(feedbackService).markReviewedForOrg(submissionId, orgId);
    }

    @Test
    @DisplayName("grant throws NotFoundException when the org does not exist")
    void grant_shouldThrow_whenOrgNotFound() {
        UUID orgId = UUID.randomUUID();
        GrantCreditRequest request = GrantCreditRequest.builder()
                .orgId(orgId).amount(500).reason("Goodwill").build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditService.grant(UUID.randomUUID(), request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Organisation not found");
        verify(orgCreditRepository, never()).insert(any(), anyInt(), any(), any(), any());
    }

    // --- getBalance ---

    @Test
    @DisplayName("getBalance returns the ledger sum for an owner")
    void getBalance_shouldReturnSum_forOwner() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.of(OrganisationRole.OWNER));
        when(orgCreditRepository.sumByOrgId(orgId)).thenReturn(1500);

        assertThat(creditService.getBalance(orgId, callerId)).isEqualTo(1500);
    }

    @Test
    @DisplayName("getBalance throws ForbiddenException for a plain member")
    void getBalance_shouldThrow_forPlainMember() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.of(OrganisationRole.MEMBER));

        assertThatThrownBy(() -> creditService.getBalance(orgId, callerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Insufficient permissions: OWNER or ADMIN role required");
    }

    @Test
    @DisplayName("getBalance throws NotFoundException when the caller is not a member")
    void getBalance_shouldThrow_whenCallerNotAMember() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(organisationRepository.findMemberRole(orgId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditService.getBalance(orgId, callerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Organisation not found");
    }

    // --- getLedger ---

    @Test
    @DisplayName("getLedger returns the full ledger for an org")
    void getLedger_shouldReturnFullLedger() {
        UUID orgId = UUID.randomUUID();
        List<OrgCreditModel> ledger = List.of(OrgCreditModel.builder().id(UUID.randomUUID()).orgId(orgId).build());

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(orgCreditRepository.findByOrgId(orgId)).thenReturn(ledger);

        assertThat(creditService.getLedger(orgId)).isEqualTo(ledger);
    }

    // --- listOrganisations ---

    @Test
    @DisplayName("listOrganisations returns every active organisation from the repository")
    void listOrganisations_shouldReturnAllActiveOrgs() {
        List<OrganisationModel> orgs = List.of(
                OrganisationModel.builder().id(UUID.randomUUID()).name("Acme Corp").build());
        when(organisationRepository.findAllActive()).thenReturn(orgs);

        assertThat(creditService.listOrganisations()).isEqualTo(orgs);
    }
}
