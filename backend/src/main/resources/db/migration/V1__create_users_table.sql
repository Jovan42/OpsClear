-- Users table (synced from Keycloak)
-- ID matches Keycloak user ID (sub claim in JWT)

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    preferences JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,

    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users(email);

COMMENT ON TABLE users IS 'Users synced from Keycloak on login';
COMMENT ON COLUMN users.id IS 'Same as Keycloak user ID (sub claim)';
COMMENT ON COLUMN users.preferences IS 'User preferences as JSON (UI settings, etc.)';
