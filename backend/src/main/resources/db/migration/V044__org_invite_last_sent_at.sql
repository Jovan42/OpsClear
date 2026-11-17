-- Invite email resend cooldown (ADR-0059). Set by the backend whenever an invite email is sent.
ALTER TABLE organisation_invites ADD COLUMN last_sent_at TIMESTAMP NULL;

-- Existing invites count as sent when they were created.
UPDATE organisation_invites SET last_sent_at = created_at;
