package com.opsclear.service;

import com.opsclear.dto.GrantCreditRequest;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.exception.PaddleSyncException;
import com.opsclear.model.OrgCreditModel;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleDiscount;
import com.opsclear.repository.OrgCreditRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

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
    @Mock private OrgSubscriptionRepository orgSubscriptionRepository;
    @Mock private FeedbackService feedbackService;
    @Mock private PaddleClient paddleClient;

    private CreditService creditService;

    @BeforeEach
    void setUp() {
        creditService = new CreditService(
                orgCreditRepository, organisationRepository, orgSubscriptionRepository, feedbackService, paddleClient);
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
        verify(feedbackService, never()).markCreditedForOrg(any(), any());
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

        verify(feedbackService).markCreditedForOrg(submissionId, orgId);
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

    // --- grant: Paddle credit sync ---

    @Test
    @DisplayName("grant skips Paddle sync when the org has no real Paddle subscription yet")
    void grant_shouldSkipPaddleSync_whenNoRealSubscription() {
        UUID orgId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();
        GrantCreditRequest request = GrantCreditRequest.builder()
                .orgId(orgId).amount(500).reason("Goodwill").build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(orgCreditRepository.insert(orgId, 500, "Goodwill", null, grantedBy))
                .thenReturn(OrgCreditModel.builder().id(UUID.randomUUID()).orgId(orgId).amount(500).build());
        when(orgSubscriptionRepository.hasRealBilling(orgId)).thenReturn(false);

        OrgCreditModel result = creditService.grant(grantedBy, request);

        assertThat(result.getPaddleSyncSkippedReason())
                .isEqualTo(CreditService.PaddleSyncSkippedReason.NO_PADDLE_SUBSCRIPTION);
        verify(paddleClient, never()).createOneTimeDiscount(any(), any(), any());
        verify(paddleClient, never()).attachDiscountToSubscription(any(), any());
    }

    @Test
    @DisplayName("grant creates a one-time Paddle discount and attaches it to the org's real subscription")
    void grant_shouldAttachDiscount_whenRealSubscriptionExists() {
        UUID orgId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();
        UUID creditId = UUID.randomUUID();
        GrantCreditRequest request = GrantCreditRequest.builder()
                .orgId(orgId).amount(29).reason("Great bug report").build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(orgCreditRepository.insert(orgId, 29, "Great bug report", null, grantedBy)).thenReturn(
                OrgCreditModel.builder().id(creditId).orgId(orgId).amount(29).reason("Great bug report").build());
        when(orgSubscriptionRepository.hasRealBilling(orgId)).thenReturn(true);
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(
                OrgSubscriptionModel.builder().orgId(orgId).paddleSubscriptionId("sub_123").build()));
        when(paddleClient.createOneTimeDiscount("2900", "EUR", "OpsClear credit: Great bug report"))
                .thenReturn(new PaddleDiscount("dsc_123"));

        OrgCreditModel result = creditService.grant(grantedBy, request);

        assertThat(result.getPaddleSyncSkippedReason()).isNull();
        verify(paddleClient).createOneTimeDiscount("2900", "EUR", "OpsClear credit: Great bug report");
        verify(paddleClient).attachDiscountToSubscription("sub_123", "dsc_123");
    }

    @Test
    @DisplayName("grant rolls back (throws PaddleSyncException) when the Paddle sync call throws — "
            + "Paddle genuinely had a real subscription to sync against here (JOB-180)")
    void grant_shouldRollBack_whenPaddleCallThrows() {
        UUID orgId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();
        GrantCreditRequest request = GrantCreditRequest.builder()
                .orgId(orgId).amount(500).reason("Goodwill").build();
        OrgCreditModel inserted = OrgCreditModel.builder()
                .id(UUID.randomUUID()).orgId(orgId).amount(500).reason("Goodwill").build();

        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(orgCreditRepository.insert(orgId, 500, "Goodwill", null, grantedBy)).thenReturn(inserted);
        when(orgSubscriptionRepository.hasRealBilling(orgId)).thenReturn(true);
        when(orgSubscriptionRepository.findByOrgId(orgId)).thenReturn(Optional.of(
                OrgSubscriptionModel.builder().orgId(orgId).paddleSubscriptionId("sub_123").build()));
        when(paddleClient.createOneTimeDiscount(any(), any(), any()))
                .thenThrow(new RestClientException("Paddle is unreachable"));

        assertThatThrownBy(() -> creditService.grant(grantedBy, request))
                .isInstanceOf(PaddleSyncException.class);
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
