-- Split REVIEWED into DECLINED / CREDITED (JOB-169 follow-up to V030) so status
-- alone tells you the outcome, not just that someone looked at it. Every existing
-- REVIEWED row got there only via a credit grant (the sole caller of the old
-- markReviewed was CreditService.grant), so CREDITED is the correct backfill.
--
-- Constraint is dropped BEFORE the backfill, not after — the old CHECK
-- (PENDING/REVIEWED only) would otherwise reject the UPDATE writing 'CREDITED'.
ALTER TABLE feedback_submissions DROP CONSTRAINT feedback_submissions_status_check;

UPDATE feedback_submissions SET status = 'CREDITED' WHERE status = 'REVIEWED';

ALTER TABLE feedback_submissions ADD CONSTRAINT feedback_submissions_status_check
    CHECK (status IN ('PENDING', 'DECLINED', 'CREDITED'));
