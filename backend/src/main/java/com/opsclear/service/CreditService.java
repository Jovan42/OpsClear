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

import java.util.ArrayList;
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
    //
    // A subscription only ever has one discount attached at a time, so a second grant
    // can't just create its own discount — that would silently replace and permanently
    // strand whatever a prior, still-unconsumed grant was worth (JOB-180 #5). Instead
    // this folds every currently-unconsumed grant (see findUnconsumedGrants) together
    // with the new one into a single replacement discount for their combined total.
    private Optional<String> syncCreditToPaddle(OrgCreditModel credit) {
        try {
            Optional<OrgSubscriptionModel> subscription = orgSubscriptionRepository.findByOrgId(credit.getOrgId());
            String paddleSubscriptionId = subscription.map(OrgSubscriptionModel::getPaddleSubscriptionId).orElse(null);
            if (!orgSubscriptionRepository.hasRealBilling(credit.getOrgId()) || paddleSubscriptionId == null) {
                log.info("Org {} has no real Paddle subscription yet — skipping Paddle credit sync",
                        credit.getOrgId());
                return Optional.of(PaddleSyncSkippedReason.NO_PADDLE_SUBSCRIPTION);
            }

            List<OrgCreditModel> unconsumed = orgCreditRepository.findUnconsumedGrants(credit.getOrgId());
            int totalAmount = credit.getAmount() + unconsumed.stream().mapToInt(OrgCreditModel::getAmount).sum();

            PaddleDiscount discount = attachNewDiscount(paddleSubscriptionId, totalAmount, discountDescription(credit));

            List<UUID> foldedCreditIds = new ArrayList<>(unconsumed.stream().map(OrgCreditModel::getId).toList());
            foldedCreditIds.add(credit.getId());
            orgCreditRepository.setPaddleDiscountId(foldedCreditIds, discount.id());

            log.info("Attached one-time Paddle discount {} ({} {}, folding in {} prior unconsumed grant(s)) to "
                            + "subscription {} for org {} (credit {})",
                    discount.id(), totalAmount, CURRENCY_EUR, unconsumed.size(), paddleSubscriptionId,
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

    private PaddleDiscount attachNewDiscount(String paddleSubscriptionId, int amount, String description) {
        PaddleDiscount discount = paddleClient.createOneTimeDiscount(toMinorUnits(amount), CURRENCY_EUR, description);
        paddleClient.attachDiscountToSubscription(paddleSubscriptionId, discount.id());
        return discount;
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

    // Called from PaddleWebhookService once a transaction.completed event confirms a
    // credit discount was actually redeemed (JOB-180 #4/#5) — a discount being
    // detached from the subscription doesn't by itself mean the org's displayed
    // balance should drop; this is what makes that happen. Safe to call more than once
    // for the same discount id (webhook redelivery) — hasDebitForPaddleDiscountId
    // makes it a no-op past the first time.
    //
    // Paddle's flat one-time discount is all-or-nothing per transaction — confirmed
    // via real sandbox data that a discount larger than the transaction it's applied
    // to gets capped at that transaction's total and then fully consumed regardless,
    // with no rollover of the difference. appliedAmount is however much of the
    // discount this specific transaction actually used (whole currency units, from the
    // webhook's own totals.discount — see PaddleWebhookService); null means the
    // payload didn't carry enough to compute it, treated as fully consumed rather than
    // risk creating a phantom leftover discount from bad data. Whatever's left over
    // gets immediately re-synced as a fresh discount so it isn't silently lost.
    @Transactional
    public void consumeCredit(String discountId, Integer appliedAmount) {
        if (orgCreditRepository.hasDebitForPaddleDiscountId(discountId)) {
            return;
        }
        List<OrgCreditModel> grants = orgCreditRepository.findGrantsByPaddleDiscountId(discountId);
        if (grants.isEmpty()) {
            log.warn("Paddle discount {} was consumed but no matching org_credits grant was found", discountId);
            return;
        }

        UUID orgId = grants.get(0).getOrgId();
        UUID grantedBy = grants.get(0).getGrantedBy();
        int totalDiscountAmount = grants.stream().mapToInt(OrgCreditModel::getAmount).sum();
        int consumed = appliedAmount == null ? totalDiscountAmount : Math.min(appliedAmount, totalDiscountAmount);

        orgCreditRepository.insertDebit(orgId, -totalDiscountAmount, "Consumed via Paddle transaction", grantedBy,
                discountId);
        log.info("Consumed {} credit for org {} (discount {}, {} actually applied to the transaction)",
                totalDiscountAmount, orgId, discountId, consumed);

        int remainder = totalDiscountAmount - consumed;
        if (remainder > 0) {
            carryForwardRemainder(orgId, remainder, grantedBy, discountId);
        }
    }

    private void carryForwardRemainder(UUID orgId, int remainder, UUID grantedBy, String settledDiscountId) {
        Optional<OrgSubscriptionModel> subscription = orgSubscriptionRepository.findByOrgId(orgId);
        String paddleSubscriptionId = subscription.map(OrgSubscriptionModel::getPaddleSubscriptionId).orElse(null);
        if (paddleSubscriptionId == null) {
            log.warn("Discount {} for org {} left {} unconsumed but the org has no Paddle subscription to "
                            + "re-sync it to — the balance will be short until the org's next credit grant "
                            + "re-syncs it",
                    settledDiscountId, orgId, remainder);
            return;
        }
        try {
            PaddleDiscount discount = attachNewDiscount(paddleSubscriptionId, remainder,
                    "OpsClear credit: carried forward from a partially-used discount");
            OrgCreditModel carryForward = orgCreditRepository.insert(
                    orgId, remainder, "Carried forward — previous discount only partially used", null, grantedBy);
            orgCreditRepository.setPaddleDiscountId(List.of(carryForward.getId()), discount.id());
            log.info("Carried forward {} unconsumed credit for org {} into new Paddle discount {}",
                    remainder, orgId, discount.id());
        } catch (RuntimeException e) {
            // Unlike grant()'s PADDLE_ERROR path, there's no request to roll back here
            // — the debit above already happened and correctly reflects that the old
            // discount really was consumed. This failure just means the leftover
            // couldn't be re-attached to Paddle, so it's effectively lost from the
            // org's usable balance until manually re-granted.
            log.warn("Failed to re-sync {} leftover credit for org {} after discount {} was only partially "
                            + "used — that amount is stranded until re-granted", remainder, orgId,
                    settledDiscountId, e);
        }
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
