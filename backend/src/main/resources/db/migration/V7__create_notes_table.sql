CREATE TABLE notes (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID          NOT NULL REFERENCES jobs(id),
    author_id  UUID          NOT NULL REFERENCES users(id),
    content    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notes_job_id ON notes(job_id);

COMMENT ON TABLE notes IS 'Immutable audit notes attached to jobs — no update or delete paths exist';
COMMENT ON COLUMN notes.created_at IS 'Set by the database default; never passed by the application';
