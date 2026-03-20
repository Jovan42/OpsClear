# ADR-0025: API Key Authentication

**Status:** Accepted
**Date:** 2026-03-20
**Author:** Jovan Manojlovic

## Context

All API access currently requires a Keycloak JWT obtained through the browser OAuth2 flow. This makes automation, scripts, and integrations unnecessarily complex — there is no way to call the API from a script without setting up a full browser login or implementing device/client-credentials flow against Keycloak.

The immediate use case is creating jobs, updating statuses, and querying projects from shell scripts and external tools without browser interaction.

## Decision

Introduce API key authentication as a second auth method alongside the existing JWT flow.

### Key format

Keys use a readable prefix: `opck_<random>` where `opck` stands for "OpsClear Key". The prefix makes keys identifiable in logs, `.env` files, and error messages — the same pattern used by Stripe (`sk_live_`), GitHub (`ghp_`), and Linear (`lin_api_`).

The random portion is cryptographically generated. Full key length: ~40 characters after the prefix.

### Storage

Only the SHA-256 hash of the key is stored in the database. The raw key is shown once on creation and never stored — if lost, the user must revoke and create a new one.

```sql
CREATE TABLE api_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    key_hash     VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 hex
    key_prefix   VARCHAR(20) NOT NULL,           -- first 8 chars for display (e.g. opck_abc1)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,                   -- NULL = no expiry
    revoked_at   TIMESTAMPTZ                    -- NULL = active
);
```

`key_prefix` stores the first 8 characters of the raw key so the UI can show "opck_abc1..." to help users identify which key is which without storing the full key.

### Authentication header

```
X-Api-Key: opck_<random>
```

Custom header rather than `Authorization: Bearer` — keeps API key auth visually and mechanically distinct from JWT auth. Consistent with industry practice (Stripe uses `Authorization: Bearer sk_...` but many APIs use `X-Api-Key`; we prefer the separation).

### Auth filter chain

`ApiKeyAuthFilter` runs **before** `BearerTokenAuthenticationFilter` in the Spring Security filter chain:

1. If `X-Api-Key` header is present: hash it, look up `api_keys` by hash, check not revoked and not expired, load the owning user, create a synthetic `Authentication` object, update `last_used_at`, continue.
2. If `X-Api-Key` header is absent: skip this filter entirely and fall through to JWT validation as normal.

The synthetic `Authentication` carries the user's UUID as the principal — the same value controllers extract from `auth.getToken().getSubject()` for JWT auth. Controllers do not need to know which auth method was used.

### Permissions

V1: same permissions as the issuing user. The key acts as the user who created it — project membership and roles apply identically. No fine-grained scoping (read-only keys, per-project keys) in V1. Scopes can be added later based on real demand.

### Key management

Each key has:
- `name` (required) — user-defined label, e.g. "deploy script", "monitoring"
- `created_at`
- `last_used_at` — updated on every authenticated request
- `expires_at` — optional, no default expiry
- `revoked_at` — soft delete; revoked keys are kept for audit trail

**Regeneration** is revoke + create new. No in-place rotation — simpler and consistent with most API key systems.

**UI warning** shown if a key has not been used for 90+ days — prompts the user to consider revoking it. Not enforced automatically.

### API

```
POST   /api/user/api-keys           — create a new key (returns raw key once)
GET    /api/user/api-keys           — list keys (shows prefix, name, dates — never hash or raw key)
DELETE /api/user/api-keys/{id}      — revoke a key
```

`POST` response includes the raw key in a `key` field. This is the only time it is returned. The response also includes `id`, `name`, `keyPrefix`, `createdAt`, `expiresAt`.

### Security

- **Rate limiting:** out of scope for V1. Can be added at the nginx level if needed.
- **Key rotation:** revoke + create new. No automatic rotation.
- **Audit:** `last_used_at` updated on each request. Revoked keys retained in the table.
- **Expiry:** stored but not enforced in V1 filter — add enforcement when expiry UI is built.
- **Transmission:** HTTPS only. Keys must not appear in URLs (query params are logged by servers and proxies).

### Public API

Out of scope for V1 — API keys are the foundation when public third-party API access is introduced.

## Alternatives Considered

### Alternative 1: OAuth2 client credentials flow

Allow scripts to authenticate via a Keycloak client with `client_credentials` grant.

**Pros:**
- No new auth infrastructure — Keycloak handles everything
- Token expiry is built-in

**Cons:**
- Scripts must implement token refresh logic
- Managing Keycloak clients per-user/script is complex and requires admin access
- No "belongs to a user" concept out of the box

**Why rejected:** More complex for the script author, requires Keycloak admin involvement per integration. API keys are simpler and more familiar for this use case.

### Alternative 2: Long-lived Keycloak access tokens

Issue access tokens with very long TTLs (days/weeks).

**Pros:**
- No backend changes

**Cons:**
- Long-lived JWTs cannot be revoked without Keycloak blacklist support
- Not designed for this use case
- Violates OAuth2 intent

**Why rejected:** No revocation, not designed for machine auth.

### Alternative 3: Authorization: Bearer for API keys

Reuse the `Authorization: Bearer` header for API keys, distinguished by the `opck_` prefix.

**Pros:**
- Standard header name

**Cons:**
- Filter must inspect the token value to decide whether to validate as JWT or look up as API key — fragile
- `BearerTokenAuthenticationFilter` will attempt to parse `opck_...` as a JWT and fail before our filter can intercept

**Why rejected:** The two auth mechanisms conflict in the filter chain. A dedicated header keeps them cleanly separated.

## Consequences

### Positive

- Scripts and tools can call the API without browser login
- Keys are fully revocable, unlike long-lived JWTs
- Controllers require no changes — same user identity model
- Foundation in place for public API access later

### Negative

- New auth path to test and maintain
- `last_used_at` write on every authenticated request — minor DB write overhead
- Users must manage keys (create, rotate, revoke) — adds operational surface

### Neutral

- JWT auth path is completely unchanged
- Key scope (permissions) revisited in a future ADR when demand exists

## Implementation Notes

1. Flyway migration: `api_keys` table
2. `ApiKey` entity + `ApiKeyRepository`
3. `ApiKeyService` — create (generate + hash), list, revoke; return raw key only on creation
4. `ApiKeyAuthFilter` — look up by hash, build synthetic `Authentication`, update `last_used_at`
5. Register filter before `BearerTokenAuthenticationFilter` in `SecurityConfig`
6. `ApiKeyController` — POST / GET / DELETE endpoints under `/api/user/api-keys`
7. Frontend — API Keys section in Settings page (list, create modal showing key once, revoke)
8. UI warning: highlight keys unused for 90+ days

## References

- [ADR-0002: Authentication with Keycloak](0002-authentication.md)
- [ADR-0018: User Settings](0018-user-settings.md)
