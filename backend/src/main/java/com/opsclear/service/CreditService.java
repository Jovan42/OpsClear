package com.opsclear.service;

import com.opsclear.dto.CreditLedgerEntryResponse;
import com.opsclear.dto.GrantCreditRequest;
import com.opsclear.exception.ErrorMessages;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code syncCreditToPaddle} attaches a one-time Paddle Discount to the org's real
 * subscription (JOB-180) — replaced the original Adjustments-based approach, which
 * turned out to be unusable for the common case: Paddle rejects a "credit" Adjustment
 * against an automatically-collected (card-charged) transaction outright, and even
 * where it is allowed, an Adjustment only ever affects a transaction that's already
 * been paid, never a future one, contradicting ADR-0043's "discount the org's next
 * payment" promise. A Discount reduces whatever transaction Paddle generates next for
 * the subscription, matching that promise. Skipped (not a failure) when the org has no
 * real, webhook-confirmed subscription yet — {@code org_credits} is the record of truth
 * either way. A genuine Paddle failure is different: {@link #grant} rolls the whole
 * transaction back for that case so the ledger never claims a sync that didn't happen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditService {

    private static final String CURRENCY_EUR = "EUR";

    private final OrgCreditRepository orgCreditRepository;
    private final OrganisationRepository organisationRepository;
    private final OrgSubscriptionRepository orgSubscriptionRepository;
    private final FeedbackService feedbackService;
    private final PaddleClient paddleClient;

    @Transactional
    public OrgCreditModel grant(UUID grantedBy, GrantCreditRequest request) {
        requireOrgExists(request.getOrgId());
        if (request.getSubmissionId() != null) {
            feedbackService.markCreditedForOrg(request.getSubmissionId(), request.getOrgId());
        }
        OrgCreditModel credit = orgCreditRepository.insert(
                request.getOrgId(), request.getAmount(), request.getReason().strip(),
                request.getSubmissionId(), grantedBy);
        log.info("Granted {} credit to org {} by user {} (submission={})",
                credit.getAmount(), credit.getOrgId(), grantedBy, credit.getSubmissionId());
        Optional<String> skipReason = syncCreditToPaddle(credit);
        if (PaddleSyncSkippedReason.PADDLE_ERROR.equals(skipReason.orElse(null))) {
            // Unlike NO_PADDLE_SUBSCRIPTION, Paddle genuinely had something to sync
            // against here — rolling back so the ledger never claims a sync that
            // didn't happen. @Transactional unwinds the insert above (and the
            // markCreditedForOrg call, if any) along with this.
            throw new PaddleSyncException(ErrorMessages.Paddle.CREDIT_SYNC_FAILED);
        }
        skipReason.ifPresent(credit::setPaddleSyncSkippedReason);
        return credit;
    }

    // Returns a stable reason code (see PaddleSyncSkippedReason) when the sync was
    // skipped or failed, empty when it succeeded. NO_PADDLE_SUBSCRIPTION is a non-fatal
    // signal only — grant() lets that stand. PADDLE_ERROR is different: grant() rolls
    // the transaction back for that one (JOB-180).
    private Optional<String> syncCreditToPaddle(OrgCreditModel credit) {
        try {
            Optional<OrgSubscriptionModel> subscription = orgSubscriptionRepository.findByOrgId(credit.getOrgId());
            String paddleSubscriptionId = subscription.map(OrgSubscriptionModel::getPaddleSubscriptionId).orElse(null);
            if (!orgSubscriptionRepository.hasRealBilling(credit.getOrgId()) || paddleSubscriptionId == null) {
                log.info("Org {} has no real Paddle subscription yet — skipping Paddle credit sync",
                        credit.getOrgId());
                return Optional.of(PaddleSyncSkippedReason.NO_PADDLE_SUBSCRIPTION);
            }

            PaddleDiscount discount = paddleClient.createOneTimeDiscount(
                    toMinorUnits(credit.getAmount()), CURRENCY_EUR, discountDescription(credit));
            paddleClient.attachDiscountToSubscription(paddleSubscriptionId, discount.id());
            log.info("Attached one-time Paddle discount {} ({} {}) to subscription {} for org {} (credit {})",
                    discount.id(), credit.getAmount(), CURRENCY_EUR, paddleSubscriptionId,
                    credit.getOrgId(), credit.getId());
            return Optional.empty();
        } catch (RuntimeException e) {
            // Unlike a skip reason, grant() rolls the whole transaction back for
            // PADDLE_ERROR — this log line fires before that unwind happens, so it's
            // reporting the failure, not stating the ledger entry's final fate.
            log.warn("Failed to sync credit {} (org {}) to Paddle — grant will be rolled back",
                    credit.getId(), credit.getOrgId(), e);
            return Optional.of(PaddleSyncSkippedReason.PADDLE_ERROR);
        }
    }

    private static String discountDescription(OrgCreditModel credit) {
        return "OpsClear credit: " + credit.getReason();
    }

    /** Stable reason codes for a skipped Paddle credit sync — kept as plain string
     *  constants (not a Java enum) so they serialize directly and the frontend can
     *  switch on them without generating a matching type. Only NO_PADDLE_SUBSCRIPTION
     *  ever reaches {@link CreditLedgerEntryResponse#getPaddleSyncSkippedReason()} —
     *  grant() turns PADDLE_ERROR into a thrown {@link PaddleSyncException} instead, so
     *  it's used internally as a control-flow signal but never actually serialized. */
    public static final class PaddleSyncSkippedReason {
        public static final String NO_PADDLE_SUBSCRIPTION = "NO_PADDLE_SUBSCRIPTION";
        public static final String PADDLE_ERROR = "PADDLE_ERROR";

        private PaddleSyncSkippedReason() {
        }
    }

    private static String toMinorUnits(int wholeEuros) {
        return String.valueOf(wholeEuros * 100);
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

    @Transactional(readOnly = true)
    public List<OrganisationModel> listOrganisations() {
        return organisationRepository.findAllActive();
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
