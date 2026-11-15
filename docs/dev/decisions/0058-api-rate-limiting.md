# ADR-0058: API Rate Limiting

**Status:** Proposed
**Date:** 2026-11-15
**Author:** Jovan

## Context

Nothing in OpsClear is rate limited. Every endpoint (JWT, API key and the few public ones) accepts requests at whatever rate a caller sends them, and the API is on the public internet. That's a real exposure whether or not there are paying customers yet.

Two surfaces stand out:

- **`POST /api/webhooks/paddle`** (ADR-0044) is public. Signature verification stops forged events, but not a flood of requests that each cost signature work in the JVM.
- **API-key traffic** (ADR-0025) exists for programmatic access. That's exactly what a runaway script or a leaked key abuses.

This is hardening work, not a user-facing feature, so it stays proportional to the risk: protect those two surfaces, add a cheap backstop for everything else, and don't rework every endpoint.

Promoted from JOB-243 (Future Consideration, PRJ-002) into PRJ-014 / MIL-042. This ADR is JOB-332.

### Facts from the existing system that shape this design

- **nginx is the only way into the API.** In `docker-compose.prod.yml` the `backend` service publishes no port. All `/api/` traffic goes through nginx in the `frontend` container (`frontend/nginx.conf`, ADR-0051). Rate limiting in nginx needs config only, no new infrastructure.
- **One backend instance.** A limiter kept in memory inside the app is accurate; no shared store (Redis etc.) is needed.
- **The backend can't see client IPs.** No `server.forward-headers-strategy` is set, so `request.getRemoteAddr()` is nginx's container IP for every request. Limiting per IP has to live in nginx (or the backend would have to start trusting `X-Forwarded-For`).
- **API-key auth** is `ApiKeyAuthFilter`, which runs before `BearerTokenAuthenticationFilter`. It hashes the `X-Api-Key` header, looks the key up, and on success authenticates the request as the key's user. Bad keys get a bare `response.sendError(401, …)`, not the JSON error shape.
- **Today's API-key callers** are `scripts/opsclear` (devs and Claude Code agents) and the GitHub job sync workflow (ADR-0055). Both send small bursts.
- **Error shape.** `GlobalExceptionHandler` returns `ErrorResponse(error, message, timestamp)`. The frontend axios interceptor (`frontend/src/api/client.ts`) shows a toast for every non-401 error and maps `error` to a translated category through `ERROR_CATEGORY_KEYS`.
- **Login is already covered.** Keycloak brute-force protection is on in `keycloak/realm-export.json`: `failureFactor: 5`, `waitIncrementSeconds: 60`, `maxFailureWaitSeconds: 900`, no permanent lockout. Login and other auth endpoints are out of scope here.
- **Internal orgs exist.** `org_subscriptions.is_internal` (ADR-0030) marks the owner's own org and demo accounts. It's set by SQL only and "all tier limits and enforcement are bypassed" for these orgs. `RequiresAddonAspect` already resolves the caller's org with `OrganisationRepository.findByMember(userId)` and checks `AddonAccessChecker.isInternal(orgId)`.
- **Users share IPs.** OpsClear's customers are 5–50 person offices, often behind one NAT IP, so a per-IP limit has to carry a whole office's traffic.

## Decision

Two layers, each doing what it's good at:

1. **nginx (per client IP):** a tight limit on the Paddle webhook, plus a generous global backstop on all of `/api/`. It stops floods before they reach the JVM.
2. **Backend (per API key):** a Bucket4j token bucket for each API key, checked in `ApiKeyAuthFilter` after the key is resolved.

Both layers answer a limited caller with **429 Too Many Requests**, a `Retry-After` header and the standard JSON error body. The same limits apply to every org; there are no tiers. **Internal orgs (`is_internal = true`) skip the API-key limit**, as they skip every other limit.

## Product decisions (settled in JOB-332)

| Question | Decision |
|----------|----------|
| V1 scope | Paddle webhook + API-key traffic, plus a generous per-IP backstop on all `/api/`. Browser (JWT) users get no limit of their own in the app. |
| Granularity | Per API key in the app; per client IP in nginx. |
| Layer | Both: nginx `limit_req` and in-app Bucket4j. |
| Subscription tiers (ADR-0031) | No. One set of limits for every org in V1. |
| Response | 429 + `Retry-After` + `{ "error": "Too Many Requests", "message", "timestamp" }` from both layers. |
| Internal orgs (ADR-0030) | Exempt from the per-API-key limit. The nginx IP backstop still applies, since nginx can't know the org. |
| Login / auth endpoints | Out of scope; Keycloak brute-force protection covers them. |
| Configurability | In-app limits and an on/off switch are application properties (overridable by env). nginx values stay in `nginx.conf`. |

### Limits

| Surface | Where | Key | Limit |
|---------|-------|-----|-------|
| API-key requests | Backend (Bucket4j) | API key ID | Bucket capacity **60**, refills **120 per minute** (2/s) |
| `POST /api/webhooks/paddle` | nginx | Client IP | **5 r/s**, burst **20**, `nodelay` |
| All other `/api/` | nginx | Client IP | **20 r/s** (1,200/min), burst **200**, `nodelay` |
| `/api/health`, `/api/notifications/stream` | nginx | — | Not limited |

Why these numbers:

- **API key:** a seed run or a job-sync burst gets through on the 60-request bucket; a loop or a leaked key is cut within seconds and then held to 2 requests/s.
- **Webhook:** Paddle sends a few events per subscription change and **retries any non-2xx response**, so a 429 to a real Paddle delivery only delays it.
- **IP backstop:** far above what 50 people clicking around send, including the parallel fetches of a page load. It's there to stop scripted floods, not to shape normal use.
- **Exemptions:** `/api/health` is polled by monitors. The SSE stream is one long-lived connection per tab, reconnecting no faster than its backoff.

## Technical design

### Database

None. Buckets live in memory and reset when the backend restarts, which is acceptable.

### API

No new endpoints. Any `/api/` endpoint can now return:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 1
Content-Type: application/json

{ "error": "Too Many Requests", "message": "Rate limit exceeded. Try again in 1 second(s).", "timestamp": "2026-11-15T10:00:00Z" }
```

- **Backend:** `Retry-After` is the bucket's real wait time, rounded up to whole seconds (at least 1).
- **nginx:** it can't compute a wait, so it always sends `Retry-After: 1` and a static message: `"Too many requests. Slow down and try again shortly."`

### Backend

- **Dependency:** `com.bucket4j:bucket4j_jdk17-core`, the latest 8.x at implementation time, core only (no JCache, no Redis).
- **`RateLimitProperties`** (`@ConfigurationProperties("opsclear.rate-limit")`):

  ```properties
  opsclear.rate-limit.enabled=true
  opsclear.rate-limit.api-key.capacity=60
  opsclear.rate-limit.api-key.refill-per-minute=120
  ```

  Each can be overridden by env (`OPSCLEAR_RATE_LIMIT_ENABLED`, …) without a code change.
- **`ApiKeyRateLimiter`** (`security/`): holds a `ConcurrentHashMap` keyed by API key ID. Each entry is created on first use and is either a Bucket4j `Bucket` (greedy refill) or an "unlimited" marker. `tryConsume(UUID keyId, UUID userId)` returns allowed or rejected-with-wait. When `enabled=false` it always allows.
- **Internal-org exemption:** when a key's entry is first created, the limiter resolves the key user's org (`OrganisationRepository.findByMember`) and checks `AddonAccessChecker.isInternal`. If the org is internal, the entry is the unlimited marker. The result is cached for each key, so the org lookup runs once per key per backend run, not on every request. A user with no org is limited.
- **`ApiKeyAuthFilter`:** once the key is resolved and active and its user exists, call the limiter **before** setting the security context and before `updateLastUsedAt`.
  - On rejection, write the 429 directly: `Retry-After` header plus the `ErrorResponse` JSON through the app's `ObjectMapper`. `@RestControllerAdvice` doesn't apply inside filters, so the filter writes the response itself.
  - Log once at WARN for each rejected key per minute (key prefix only, never the full key), so a runaway key shows up in the logs without flooding them.
- **Why key by key ID, not the raw header or its hash:** the set of real keys is bounded, so the map can't grow without limit. Invalid-key spam never reaches the limiter; nginx's per-IP backstop handles it.
- **Tests:**
  - Unit tests for `ApiKeyRateLimiter`: allows up to capacity, rejects after, refills, disabled = always allows.
  - Integration tests: through the real filter chain with `@TestPropertySource` setting a small capacity (e.g. 3), the request over capacity gets 429 with `Retry-After` and the JSON body; a second key keeps its own bucket; a key of an `is_internal` org is never limited; JWT requests aren't limited; `enabled=false` turns it off.
  - Other integration tests keep the default limits, which are well above what any test sends with one key.

### nginx (`frontend/nginx.conf`)

```nginx
limit_req_zone $binary_remote_addr zone=api_ip:10m      rate=20r/s;
limit_req_zone $binary_remote_addr zone=paddle_hook:1m  rate=5r/s;
limit_req_status 429;
```

- **`location /api/`:** `limit_req zone=api_ip burst=200 nodelay;`
- **`location = /api/webhooks/paddle`** (new, more specific): `limit_req zone=paddle_hook burst=20 nodelay;` with the same proxy settings as `/api/`. A location with its own `limit_req` doesn't inherit the parent's, so the webhook gets only its own zone.
- **`location = /api/health`** (new): proxies with no `limit_req`.
- **`location /api/notifications/stream`:** unchanged and not limited.
- **429 body:** `error_page 429 = @rate_limited;`, where the named location sends `add_header Retry-After 1 always;`, `default_type application/json;` and `return 429 '{"error":"Too Many Requests",...}'`. The timestamp in that body comes from `$time_iso8601`.
- The `limit_req_zone` lines must be at `http` level. `frontend/Dockerfile` copies `nginx.conf` to `/etc/nginx/conf.d/default.conf`, which nginx includes inside `http`, so they go at the top of `nginx.conf`, above the `server` blocks.

### Frontend

- Add `'Too Many Requests': 'errors:tooManyRequests'` to `ERROR_CATEGORY_KEYS` in `frontend/src/api/client.ts`, with the `errors:tooManyRequests` key ("Too many requests") in every locale (ADR-0037). The toast description stays the server's `message`.
- No automatic retry for mutations. TanStack Query's default retries with backoff for queries already behave sensibly against a 429.
- E2E: one spec stubs a 429 with `cy.intercept` and checks the toast (E2E.md: proportional coverage).

### Constraints & edge cases

- **Real client IP in nginx.** `$binary_remote_addr` is correct only if Docker passes the client's source IP through to the `frontend` container. With the default iptables port publishing it does; with the userland proxy (e.g. IPv6) every request could show the Docker gateway IP, and then the whole internet would share one bucket. The nginx job must check the prod access log and show real client IPs before merging.
- **Shared office IP:** covered by the 20 r/s / burst 200 backstop. If a customer ever hits it, raise the zone rate rather than adding per-user limits.
- **Several keys per user:** each key gets its own bucket, so a user with N keys gets N× the limit. That's acceptable for V1; a per-user cap can come later if abused.
- **Revoked keys:** their buckets stay in the map until restart. That's a few hundred bytes per key, so they aren't cleaned up.
- **API key and nginx limits stack.** A key caller is also counted by the IP backstop, but 2 r/s is far below 20 r/s, so the key limit always applies first.
- **Changing `is_internal`** takes effect for a key on the next backend restart, because the exemption is cached. That's acceptable: the flag is set by hand in SQL and rarely changes.
- **Internal traffic and nginx.** The GitHub job sync and `scripts/opsclear` on prod use keys in the internal OpsClear org, so the key limit never applies to them. They still count against the 20 r/s per-IP backstop, which they come nowhere near.
- **Restart resets all buckets.** Acceptable; there's no persistence requirement.
- **The SSE stream is exempt.** It's one connection per tab, and its reconnect backoff (ADR-0051) prevents reconnect storms.
- **Paddle retries.** A 429 on a real delivery goes through Paddle's retry schedule, so no event is lost.

## Alternatives considered

### nginx only (decided against)

Limiting everything in `nginx.conf`, using the raw `X-Api-Key` header value as the key for key traffic.

- **For:** no backend code.
- **Against:** a made-up header value gets a fresh bucket, so rotating junk values bypasses the limit. Raw secrets would also sit in nginx's shared memory, and the limit couldn't be covered by the backend's integration tests.

### In-app only (decided against)

Bucket4j for everything, including per IP.

- **For:** one place to configure and test.
- **Against:** per IP needs the backend to trust `X-Forwarded-For` (`forward-headers-strategy`), and floods still hit the JVM and the API-key DB lookup before being rejected.

### Per-user limits for JWT traffic (decided against for V1)

Bucket4j per authenticated user for browser traffic.

- **For:** fairer per person than an office-wide IP.
- **Against:** the risk is low (callers are logged in and belong to an org), the limits need tuning against busy screens, and real users could be hit. The IP backstop covers floods.

### Limits by subscription tier (decided against for V1)

Higher API-key limits for paid orgs (ADR-0031).

- **Against:** it needs the org's plan for every request plus config per tier, for a need nobody has yet. It can be raised as a Future Consideration if a customer asks.

### Shared limiter store (Redis / Bucket4j JCache) (decided against)

- **Against:** it's only needed with several backend instances. There's one, and adding Redis is new infrastructure for no gain.

### Resilience4j `RateLimiter` (decided against)

- **Against:** it's built for protecting outgoing calls with a fixed number of limiters. Bucket4j handles many independent per-key buckets and gives the exact wait time for `Retry-After`.

## Consequences

### Positive

- Our own automation (job sync, `scripts/opsclear`, agents) runs from the internal org and is never limited by key.
- A leaked or runaway API key is held to 2 requests/s instead of whatever it can push.
- Floods on the public webhook and on `/api/` are stopped in nginx before they cost JVM or DB work.
- One 429 shape across both layers, which the frontend shows as a clear toast.
- No new infrastructure and no database changes.

### Negative

- Two places hold limits (`nginx.conf` and application properties), so a limit change has to be made in the right one.
- The nginx backstop treats a whole office behind one IP as a single client. The limit is generous, but it's a shared budget.
- Bulk API-key scripts (e.g. a large import) now need to pace themselves or honour `Retry-After`.

### Neutral

- `scripts/opsclear` could retry once after `Retry-After`. It isn't needed at today's volumes; add it if agents start hitting the limit.
- `ApiKeyAuthFilter`'s 401 responses still use bare `sendError` rather than the JSON shape. That's out of scope here and a candidate Maintenance chore.
- AGENTS.md should mention the API-key limit and that internal-org keys are exempt.

## Implementation order

1. **Backend: per-API-key rate limiting.** Bucket4j dependency, `RateLimitProperties`, `ApiKeyRateLimiter` with the internal-org exemption, the 429 response in `ApiKeyAuthFilter`, WARN logging, unit + integration tests, and a short note in AGENTS.md. No blockers.
2. **Chore: nginx per-IP rate limits.** Both `limit_req` zones, the webhook and health locations, the JSON 429 `error_page`, and a check on prod that `$remote_addr` shows real client IPs. Independent of 1.
3. **Frontend: 429 error toast.** The `ERROR_CATEGORY_KEYS` entry, i18n keys in all locales, and an E2E spec with a stubbed 429. *Blocked by Backend.*

No DB job.

## API Changes Checklist

- [ ] Postman collection: no new requests; add a note on the collection that any request can return 429 with `Retry-After`
- [ ] Update environment variables if needed (none required; `OPSCLEAR_RATE_LIMIT_*` overrides are optional)
- [ ] Test the flow manually before marking complete (a burst of API-key requests gets a 429 on prod)

## References

- JOB-243: source Future Consideration
- ADR-0025: API key authentication
- ADR-0030: Subscription data model (`is_internal`)
- ADR-0031: Feature gating (why no tiers in V1; same internal-org check)
- ADR-0036: Keycloak custom auth pages (brute-force protection on login)
- ADR-0037: Localization (the new error key in every locale)
- ADR-0044: Paddle payment integration (webhook endpoint and retries)
- ADR-0051: In-app notification centre (nginx proxy, SSE stream)
- ADR-0055: GitHub job sync (an API-key caller)
- Bucket4j: https://bucket4j.com
- nginx `ngx_http_limit_req_module`: https://nginx.org/en/docs/http/ngx_http_limit_req_module.html
