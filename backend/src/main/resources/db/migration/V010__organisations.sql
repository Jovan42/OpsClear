CREATE TABLE organisations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(10)  NOT NULL UNIQUE,
    created_by  UUID         REFERENCES users(id),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP
);

CREATE TABLE organisation_members (
    organisation_id UUID        NOT NULL REFERENCES organisations(id),
    user_id         UUID        NOT NULL REFERENCES users(id),
    org_role        VARCHAR(10) NOT NULL,
    joined_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (organisation_id, user_id)
);

CREATE TABLE organisation_invites (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID         NOT NULL REFERENCES organisations(id),
    email           VARCHAR(255) NOT NULL,
    invited_by      UUID         REFERENCES users(id),
    token           VARCHAR(64)  NOT NULL UNIQUE,
    expires_at      TIMESTAMP    NOT NULL,
    accepted_at     TIMESTAMP,
    revoked_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_organisation_invites_token ON organisation_invites(token);
CREATE INDEX idx_organisation_invites_email ON organisation_invites(email);

ALTER TABLE users    ADD COLUMN organisation_id UUID REFERENCES organisations(id);
ALTER TABLE projects ADD COLUMN organisation_id UUID REFERENCES organisations(id);
