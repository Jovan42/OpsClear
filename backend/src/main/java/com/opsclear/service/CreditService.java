package com.opsclear.service;

import com.opsclear.dto.CreditLedgerEntryResponse;
import com.opsclear.dto.GrantCreditRequest;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.exception.PaddleSyncException;
import com.opsclear.model.OrgCreditModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.paddle.PaddleAdjustment;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleTransaction;
import com.opsclear.paddle.PaddleTransactionDetails;
import com.opsclear.paddle.PaddleTransactionLineItem;
import com.opsclear.repository.OrgCreditRepository;
import com.opsclear.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code syncCreditToPaddle} is best-effort for the "nothing to sync against yet"
 * cases (ADR-0044): a credit can be granted to an org at any time, including one with
 * no Paddle customer yet, zero completed transactions, or a transaction with no line
 * items — those are legitimate states, not failures, so the grant still stands and
 * {@code org_credits} is the record of truth. A genuine Paddle failure (network error,
 * rate limit, unexpected API response) is different: Paddle *does* have something to
 * sync against, the call just didn't reach it, so leaving the ledger entry in place
 * would silently misrepresent reality. {@link #grant} rolls the whole transaction back
 * in that case (JOB-180) so the admin gets a clear error and can retry, rather than a
 * ledger entry Paddle never received.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditService {

    private final OrgCreditRepository orgCreditRepository;
    private final OrganisationRepository organisationRepository;
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
            // Unlike the other skip reasons, Paddle genuinely had something to sync
            // against here — rolling back so the ledger never claims a sync that
            // didn't happen. @Transactional unwinds the insert above (and the
            // markCreditedForOrg call, if any) along with this.
            throw new PaddleSyncException(ErrorMessages.Paddle.CREDIT_SYNC_FAILED);
        }
        skipReason.ifPresent(credit::setPaddleSyncSkippedReason);
        return credit;
    }

    // Returns a stable reason code (see PaddleSyncSkippedReason) when the sync was
    // skipped or failed, empty when it succeeded. The three "nothing to sync against
    // yet" cases are non-fatal signals only — grant() lets those stand. PADDLE_ERROR is
    // different: grant() rolls the transaction back for that one (JOB-180).
    private Optional<String> syncCreditToPaddle(OrgCreditModel credit) {
        try {
            String customerId = organisationRepository.findPaddleCustomerId(credit.getOrgId()).orElse(null);
            if (customerId == null) {
                log.info("Org {} has no Paddle customer yet — skipping Paddle credit sync", credit.getOrgId());
                return Optional.of(PaddleSyncSkippedReason.NO_PADDLE_CUSTOMER);
            }

            Optional<PaddleTransaction> transaction = paddleClient.findLatestCompletedTransaction(customerId);
            if (transaction.isEmpty()) {
                log.info("Paddle customer {} has no completed transactions yet — skipping Paddle credit sync",
                        customerId);
                return Optional.of(PaddleSyncSkippedReason.NO_COMPLETED_TRANSACTION);
            }

            // The item_id Adjustments needs is the transaction *line* item id
            // (details.line_items[].id, e.g. "txnitm_..."), not the top-level items[]
            // array — Paddle echoes the latter back from the request with no id of its
            // own, so reading .id() off it is always null regardless of item count.
            List<PaddleTransactionLineItem> lineItems = Optional.ofNullable(transaction.get().details())
                    .map(PaddleTransactionDetails::lineItems)
                    .orElse(List.of());
            if (lineItems.isEmpty()) {
                log.warn("Paddle transaction {} for customer {} has no line items — skipping Paddle credit sync",
                        transaction.get().id(), customerId);
                return Optional.of(PaddleSyncSkippedReason.NO_LINE_ITEMS);
            }

            String transactionId = transaction.get().id();
            String itemId = lineItems.get(0).id();
            PaddleAdjustment adjustment = paddleClient.createCreditAdjustment(
                    transactionId, itemId, toMinorUnits(credit.getAmount()), credit.getReason());
            log.info("Created Paddle credit adjustment {} against transaction {} for org {} (credit {})",
                    adjustment.id(), transactionId, credit.getOrgId(), credit.getId());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Failed to sync credit {} (org {}) to Paddle — ledger entry stands regardless",
                    credit.getId(), credit.getOrgId(), e);
            return Optional.of(PaddleSyncSkippedReason.PADDLE_ERROR);
        }
    }

    /** Stable reason codes for a skipped Paddle credit sync — kept as plain string
     *  constants (not a Java enum) so they serialize directly and the frontend can
     *  switch on them without generating a matching type. Only the first three ever
     *  reach {@link CreditLedgerEntryResponse#getPaddleSyncSkippedReason()} — grant()
     *  turns PADDLE_ERROR into a thrown {@link PaddleSyncException} instead, so it's
     *  used internally as a control-flow signal but never actually serialized. */
    public static final class PaddleSyncSkippedReason {
        public static final String NO_PADDLE_CUSTOMER = "NO_PADDLE_CUSTOMER";
        public static final String NO_COMPLETED_TRANSACTION = "NO_COMPLETED_TRANSACTION";
        public static final String NO_LINE_ITEMS = "NO_LINE_ITEMS";
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
