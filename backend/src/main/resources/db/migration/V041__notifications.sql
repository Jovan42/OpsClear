CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID        NOT NULL REFERENCES organisations(id),
    recipient_id UUID        NOT NULL REFERENCES users(id),
    type         VARCHAR(40) NOT NULL,
    actor_id     UUID        REFERENCES users(id),
    project_id   UUID        REFERENCES projects(id),
    job_id       UUID        REFERENCES jobs(id),
    approval_id  UUID        REFERENCES approvals(id),
    payload      JSONB       NOT NULL DEFAULT '{}',
    read_at      TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notifications_type CHECK (type IN (
        'APPROVAL_REQUESTED','APPROVAL_DECIDED','JOB_ASSIGNED','JOB_STATUS_CHANGED',
        'JOB_BLOCKED','BLOCKER_CLEARED','JOB_UPDATED','JOB_DELETED','NOTE_ADDED',
        'JOB_DEADLINE_SOON','JOB_OVERDUE','PROJECT_MEMBER_ADDED','SCHEDULE_RUN_MISSED',
        'INVITE_ACCEPTED','FEEDBACK_RESOLVED','SUBSCRIPTION_PAST_DUE'))
);

CREATE INDEX idx_notifications_recipient_created ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_notifications_recipient_unread  ON notifications(recipient_id) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_created           ON notifications(created_at);

-- Remembers which deadline alerts were already sent, independent of notification retention.
CREATE TABLE job_deadline_alerts (
    job_id   UUID        NOT NULL REFERENCES jobs(id),
    kind     VARCHAR(20) NOT NULL CHECK (kind IN ('DEADLINE_SOON','OVERDUE')),
    deadline TIMESTAMP   NOT NULL,
    sent_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (job_id, kind, deadline)
);
