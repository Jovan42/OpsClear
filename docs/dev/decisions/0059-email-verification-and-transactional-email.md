# ADR-0059: Email Verification and Transactional Email

**Status:** Proposed
**Date:** 2026-11-16
**Author:** Jovan

## Context

Anyone can register an OpsClear account with any email address. `verifyEmail` is `false` in `keycloak/realm-export.json`, so nothing checks that the person owns the address. `smtpServer` is empty (`{}`), so Keycloak couldn't send email even if verification were turned on. Password reset (`resetPasswordAllowed: true`) also needs email and probably doesn't work on prod today for the same reason.

The backend doesn't send email either. `EmailService.sendInvite` is a stub that only logs the invite token, and `OrgInvitesPage` shows no link to copy. **As things stand, an invitee can't receive an org invite at all.**

This ADR adds one sending path (Brevo SMTP) and uses it for:

1. Keycloak email verification on registration
2. Keycloak password-reset emails
3. Org invite emails from the backend, plus a "Copy link" and a "Resend email" action

Promoted from JOB-255 (Future Consideration, PRJ-002) into PRJ-014 / MIL-043. This ADR is JOB-333.

### Facts from the existing system that shape this design

- **Keycloak blocks unverified logins before the app sees them.** With `verifyEmail: true`, Keycloak adds a `VERIFY_EMAIL` required action and stops login until the link is clicked, before any token reaches the backend. `UserSyncService` creates the `users` row only once a real token arrives, so there are no half-registered users to clean up.
- **Turning it on affects existing users too.** Keycloak also triggers `VERIFY_EMAIL` at the next login for every user with `emailVerified=false`. That's everyone who has registered on prod so far.
- **Prod's realm isn't updated from the repo.** Keycloak runs `start-dev --import-realm`, and the import skips a realm that already exists. Editing `realm-export.json` and redeploying (the `deploy-keycloak` job from ADR-0036) does **not** change realm settings on prod; it only reloads mounted theme files. Realm changes need the admin API or the console.
- **The VPS can't use the usual SMTP ports.** DigitalOcean blocks outbound SMTP ports 25, 465 and 587 on Droplets by default (465 and 587 since March 2025), and DigitalOcean has no email service of its own. Keycloak can only send over SMTP, not a provider's HTTPS API, so it has to use a port that isn't blocked: **2525**, which Brevo supports.
- **There's no sending domain.** The app runs on `46-101-205-77.sslip.io`. sslip.io works out the IP from the hostname, and its DNS can't be edited, so it can't hold SPF/DKIM/DMARC records.
- **Theme.** `keycloak/themes/opsclear/login/` overrides `login`, `register`, `login-reset-password` and `login-update-password` (ADR-0036), with `messages_en`/`messages_sr` and `supportedLocales: ["en", "sr"]`. There's no `login-verify-email.ftl` and no email theme (`emailTheme` unset). Keycloak's built-in email texts have no Serbian.
- **One realm file serves dev, CI and prod.** CI starts Keycloak with `docker run` and loads `realm-export.json` through the admin REST API. The `@smoke` E2E test `auth-flows/registration.cy.ts` expects to land in the app straight after registering.
- **Seed users** are created by `scripts/seed.sh` with `emailVerified: true`; `testuser@example.com` in the realm export is verified too.
- **Invites.** `POST /api/organisations/{orgId}/invites` saves an invite with a random token that expires after 7 days (`INVITE_EXPIRY_DAYS`). `POST /api/invites/{token}/accept` only checks that the caller's email matches the invite email. Without verification, someone who registers with another person's email and has the token could accept that person's invite. Verification closes this. `OrgInviteResponse` doesn't include the token. The accept page is `/invite/:token`.
- **Language.** The backend doesn't store a user's language; it's a frontend preference (ADR-0023, ADR-0037).
- **Async pattern.** `NotificationEventListener` uses `@TransactionalEventListener(AFTER_COMMIT)` + `@Async(AsyncConfig.NOTIFICATION_EXECUTOR)`, which the invite email reuses.

## Decision

- **Provider: Brevo free plan** (300 emails/day, no expiry). SMTP relay `smtp-relay.brevo.com:2525` with STARTTLS. Keycloak and the backend use the same Brevo account and sender.
- **Sender for now: one verified sender address**, a dedicated free Gmail address made for OpsClear and verified in Brevo. This is acceptable only while the app is in testing with no real users. **Before real users sign up**, switch to a domain we own with Brevo domain authentication (DKIM + SPF + DMARC `p=none`). Until then, some mail will land in spam.

  > **Note (2026-11-25, JOB-433):** the domain sender is in place. Keycloak and the backend send as `OpsClear <no-reply@opsclear.app>` through Brevo, with the domain authenticated (DKIM, SPF, DMARC `p=none`), and replies go to `support@opsclear.app`. The requirement above is met. See ADR-0064 and the "Domain email" section of `docs/dev/DEPLOY.md`.
- **Keycloak:** `verifyEmail: true`, Brevo SMTP, a branded verify-email page, and an `opsclear` email theme with en/sr texts for the verification and password-reset emails.
- **Existing unverified users have to verify too.** At their next login Keycloak sends them the verification email and blocks the login until they click it.
- **Prod realm changes go through an idempotent `kcadm` script**, run by hand on the VPS, with SMTP secrets from `.env.prod`.
- **Dev and CI use Mailpit** with verification on, and E2E clicks the real verification link.
- **Backend:** Spring Mail sends invite emails in the inviter's UI language, after commit and asynchronously. Pending invites get "Copy link" and "Resend email" (5-minute cooldown).

## Product decisions (settled in JOB-333)

| Question | Decision |
|----------|----------|
| Provider | Brevo, free plan. SendGrid and Mailgun considered (see Alternatives). |
| Sender | A verified single sender (dedicated Gmail) during testing. A domain we own, authenticated in Brevo, is required before real users. |
| Existing users | Must verify. Anyone with `emailVerified=false` is asked at their next login, the same as a new sign-up. No one is marked verified in bulk. |
| Branding | Branded `login-verify-email.ftl` and the "email verified" info page. An `opsclear` email theme keeps Keycloak's email layout but overrides subjects and body texts in en and sr. |
| Password reset | Covered: same SMTP, same email theme, manual and E2E checks. |
| Invite emails | In scope. Sent by the backend through the same Brevo account. |
| Invite email language | The inviter's current UI language (`en`/`sr`), sent with the request. |
| Invite delivery failure | Never fails the request. The send runs after commit and asynchronously; failures are logged. Owners/admins can always "Copy link" from the invites list. |
| Resend | Yes: "Resend email" on pending invites, with a 5-minute cooldown per invite. It keeps the same token and expiry. |

## Technical design

### Keycloak realm (`keycloak/realm-export.json`)

The export is what fresh installs, dev and CI get:

- `verifyEmail: true`
- `emailTheme: "opsclear"`
- `smtpServer`: Mailpit, no auth, no TLS:

  ```json
  { "host": "mailpit", "port": "1025", "from": "no-reply@opsclear.local", "fromDisplayName": "OpsClear", "auth": "false", "starttls": "false", "ssl": "false" }
  ```

- No secrets in the export. **Prod's SMTP values are set only by the apply script.** A fresh prod install would start with Mailpit settings until the script runs; the script's README section says so.

### Keycloak theme (`keycloak/themes/opsclear/`)

- **`login/login-verify-email.ftl`:** the "check your inbox" page, built on the same layout as the other branded pages (ADR-0036). It shows the user's email, a "Resend email" link (Keycloak's own `url.loginAction`) and a link back to login.
- **`login/info.ftl`:** branded info page. Keycloak shows it after the link is clicked ("Your email address has been verified") and for other action-token results.
- **Message keys** for both pages go in `login/messages/messages_en.properties` and `messages_sr.properties`.
- **`email/theme.properties`:** `parent=base`, `locales=en,sr`. **`email/messages/messages_en.properties` / `messages_sr.properties`** override:
  - `emailVerificationSubject`, `emailVerificationBody`, `emailVerificationBodyHtml`
  - `passwordResetSubject`, `passwordResetBody`, `passwordResetBodyHtml`

  The texts are short, mention OpsClear, and keep Keycloak's `{0}` link and `{1}`/`{3}` expiry placeholders. No custom HTML email templates.

  > **Amendment (2026-11-17, JOB-344):** after seeing the plain emails in Mailpit, the maintainer chose a light branded layout instead. `email/html/template.ftl` wraps every HTML email in a white card (about 560px) on a light grey background, with an "OpsClear" header, a sans-serif font stack and the login theme's brand colours. The verification and reset `*BodyHtml` texts put the link in a brand-coloured button and repeat the raw link under it for clients that block styling. Everything uses inline styles, because email clients ignore `<style>` blocks and CSS variables. There are no images. `email-verification.ftl` and `password-reset.ftl` are overridden only to skip `kcSanitize`, which strips the button's inline styles. The link is made safe for the attribute instead. The other base templates and the text bodies are unchanged.
- **Email language:** Keycloak picks it from the user's locale (set from the registration page's language), falling back to `en`.

### Prod rollout script (`scripts/keycloak-apply-email.sh`)

Runs `kcadm.sh` inside the `keycloak` container on the VPS. It's idempotent, so it's safe to run again.

1. Log in as the master admin (`KEYCLOAK_ADMIN_PASSWORD` from `.env.prod`).
2. Update the realm's email settings:
   - `emailTheme=opsclear`
   - `smtpServer` from env: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_FROM_NAME`, `auth=true`, `starttls=true`
3. List the users with `emailVerified=false`. These are the people who will have to verify at their next login.
4. **Only with `--enable-verification`:** set `verifyEmail=true`.
5. Print the realm's email settings back, with the password masked.

Rollout takes two runs, so verification is never switched on before email is known to work. Otherwise every unverified user would be stuck on a page waiting for an email that never comes.

1. Run without the flag.
2. Use Keycloak's **Realm settings → Email → Test connection** on prod to confirm that port 2525 gets through.
3. Run again with `--enable-verification`.

Run it again after any fresh realm import or credential change.

### Secrets and config

- **`.env.prod`** (uncommitted) gets `SMTP_HOST=smtp-relay.brevo.com`, `SMTP_PORT=2525`, `SMTP_USER` (Brevo SMTP login), `SMTP_PASSWORD` (Brevo SMTP key), `SMTP_FROM`, `SMTP_FROM_NAME=OpsClear` and `SMTP_STARTTLS=true`. `.env.prod.example` lists them empty.
- **`docker-compose.prod.yml`** passes the `SMTP_*` values to `backend`, plus `APP_BASE_URL=https://${APP_HOST}:${APP_PORT}`. Keycloak doesn't read them from the environment; the script does.

### Dev and CI (Mailpit)

- **`docker-compose.yml`:** new `mailpit` service (`axllent/mailpit`, pinned version) with SMTP on `1025` and UI/API on `8025` (http://localhost:8025, added to CLAUDE.md access points). Keycloak reaches it as `mailpit:1025`; the backend on the host uses `localhost:1025`.
- **CI (`ci.yml`, E2E job):**
  - create a Docker network
  - start `mailpit` and Keycloak on it, so the realm's `mailpit` host resolves
  - publish Mailpit's `8025` for Cypress and its `1025` for the backend
- **Cypress:** a `cy.mailpitLink(to, pathFragment)` command polls `GET http://localhost:8025/api/v1/search?query=to:<email>` until the newest message arrives, then pulls the first link containing `pathFragment` out of the HTML body.

### Database

Migration: `org_invites.last_sent_at TIMESTAMP NULL`, backfilled to `created_at` for existing rows. Set whenever an invite email is sent (on create and on resend). Used for the resend cooldown.

### API

- **`POST /api/organisations/{orgId}/invites`:** `SendInviteRequest` gets an optional `language` (`en` | `sr`, default `en`, other values give 400). Response unchanged apart from `token` (below).
- **`OrgInviteResponse`** gets `token`. Only owners/admins can list and create invites, and they need the token to build the "Copy link" URL. The invitee already has it.
- **New `POST /api/organisations/{orgId}/invites/{inviteId}/resend`**
  - Body: `{ "language": "en" | "sr" }` (optional, default `en`).
  - Permissions: the same as sending an invite.
  - Invite not pending (accepted, revoked or expired): 409 Conflict.
  - Less than 5 minutes since `last_sent_at`: 429 + `Retry-After` (seconds left), with the ADR-0058 body `{ "error": "Too Many Requests", … }`.
  - Otherwise: set `last_sent_at`, send the email after commit, return the updated `OrgInviteResponse`. The token and expiry don't change.
- Postman: update the collection for both changes.

### Backend

- **Dependencies:** `spring-boot-starter-mail`, plus `com.icegreen:greenmail-junit5` (test scope) for an in-memory SMTP server in tests.
- **Config** (`application.properties`):

  ```properties
  spring.mail.host=${SMTP_HOST:localhost}
  spring.mail.port=${SMTP_PORT:1025}
  spring.mail.username=${SMTP_USER:}
  spring.mail.password=${SMTP_PASSWORD:}
  spring.mail.properties.mail.smtp.auth=${SMTP_AUTH:false}
  spring.mail.properties.mail.smtp.starttls.enable=${SMTP_STARTTLS:false}
  opsclear.mail.from=${SMTP_FROM:no-reply@opsclear.local}
  opsclear.mail.from-name=${SMTP_FROM_NAME:OpsClear}
  opsclear.app.base-url=${APP_BASE_URL:http://localhost:5173}
  ```

  Prod sets `SMTP_AUTH=true` alongside the other `SMTP_*` values.
- **`EmailService.sendInvite(InviteEmail)`:** builds a multipart text + simple HTML message from a `MessageSource` bundle (`mail/messages_en.properties`, `mail/messages_sr.properties`) and sends it with `JavaMailSender`.
  - Content: the org name, the inviter's name, the link `{base-url}/invite/{token}`, and the expiry date.
  - No template engine.
  - Exceptions are caught and logged at WARN with the invite ID. The email address and token are never logged.
- **`InviteEmailRequestedEvent`** (invite ID + language) is published by `OrgInviteService.send` and `resend`. An `InviteEmailListener` (`AFTER_COMMIT`, `@Async(NOTIFICATION_EXECUTOR)`) loads the invite, org and inviter and calls `EmailService`. This drops the current synchronous stub call that logs the token.
- **Resend cooldown** is a domain rule in `OrgInviteService`. It throws a new `TooManyRequestsException(retryAfterSeconds)`, which `GlobalExceptionHandler` maps to 429 with `Retry-After`.
- **Tests:**
  - Unit tests: `OrgInviteService` (event published, cooldown, non-pending gives 409, language validation) and `EmailService` (en/sr content, link, failure swallowed).
  - Integration tests with GreenMail: create sends one email with the right link and language; resend sends again; resend inside the cooldown gives 429 + `Retry-After`; resend of a revoked invite gives 409; `token` appears in the list response; the endpoint's permissions are checked.

### Frontend

- **Invite form** sends `language: i18n.resolvedLanguage` (`en` | `sr`).
- **`OrgInvitesPage`**, for each pending invite:
  - **Copy link** copies `${window.location.origin}/invite/${token}` and shows a "Link copied" toast.
  - **Resend email** calls the resend endpoint and shows "Invite email sent". On 429 it shows "You can resend in N minutes", using `Retry-After`. Accepted, revoked and expired invites show neither action.
- i18n keys in en and sr.

### Constraints & edge cases

- **Port 2525 must get through from the Droplet.** If Test connection times out, DigitalOcean is blocking that port too. Stop and decide then: ask DigitalOcean support to unblock it, or use another Brevo port. Don't switch providers silently.
- **Brevo's free limit is 300 emails a day,** shared by verification, reset and invite emails. Far above testing volume. If it's ever reached, Brevo refuses the send: Keycloak shows its error page, and invites fall back to "Copy link".
- **Gmail sender.** Brevo may rewrite or flag a free-mail `From`, and DMARC alignment fails, so expect spam-folder delivery until the domain switch. That's accepted for testing only, and the Consequences section records the switch as a must before real users.
- **Wrong address at registration.** The user is stuck on the verify page. They can register again with the right address; the unverified Keycloak account stays behind and never creates a `users` row. Cleaning those up isn't needed for V1.
- **Verification link expiry** uses Keycloak's default action-token lifespan (5 minutes for verify-email in Keycloak 24's realm defaults, adjustable per realm). The waiting page's "Resend email" covers expiry. The script leaves the default alone.
- **Seed and test users** are already `emailVerified: true`, so dev logins and existing E2E logins are unaffected.
- **Existing prod users with a typo'd or dead email** can't verify and are locked out. Fix in the Keycloak admin console: correct the email (Keycloak sends a new verification email at next login), or set "Email verified" by hand once ownership is confirmed some other way.
- **Unverified existing users still have a `users` row** in OpsClear and stay members of their orgs and projects while they're blocked. They just can't log in until they verify; nothing in OpsClear is removed.
- **E2E registration** now goes register → waiting page → Mailpit link → app. Users created in E2E keep unique emails, so the Mailpit search can't pick up the wrong message.
- **The invite token is now in API responses** for owners/admins. They could already invite anyone, so it gives them no new power.
- **Inviter language ≠ invitee language:** acceptable. The invitee can switch language once in the app.
- **Async send after commit** means the API returns before the email goes out. Tests wait for GreenMail and Mailpit to receive the message instead of asserting straight away.

## Alternatives considered

### DigitalOcean-native email (not available)

DigitalOcean has no transactional email service and blocks SMTP ports 25/465/587 on Droplets. Its own docs point to third-party providers.

### SendGrid (decided against)

DigitalOcean's suggested partner. Its free plan's status is uncertain (reported as retired in 2025), so it may cost money from day one. Brevo's free plan is confirmed and doesn't expire.

### Mailgun (decided against, fallback)

It has a documented Keycloak setup and supports port 2525, but its free tier is small and has changed over time. It's the fallback if Brevo doesn't work out.

### Amazon SES (decided against)

Cheapest at scale, but it needs an AWS account and domain verification, and the free tier runs out after 12 months whether we use it or not.

### Self-hosted mail server (decided against)

Blocked ports, poor deliverability from a Droplet IP, and ongoing upkeep.

### Use the sslip.io hostname as the sending domain (not possible)

Its DNS can't be edited, so SPF/DKIM/DMARC can't be set up.

### Mark existing users verified in bulk (decided against)

A one-time script step would set `emailVerified=true` on everyone already registered, so only new sign-ups verify. It avoids interrupting anyone, but it marks unchecked addresses (test accounts, typos) as verified. That would leave the invite-acceptance gap open for those accounts and make "verified" mean less.

### Apply realm changes by hand in the admin console (decided against)

Nothing to build, but it isn't repeatable or reviewable, and it's easy to get the order wrong (turning verification on before email is known to work).

### Overwrite the realm on every deploy (decided against)

An import strategy that replaces the existing realm risks wiping users and other realm state on prod.

### Verification off in dev/CI, on only in prod (decided against)

The E2E suite would stay unchanged, but the verification and reset emails would never be tested automatically, and dev would behave differently from prod.

### Brevo HTTP API for invite emails (decided against)

It avoids SMTP ports entirely, but it adds a second integration next to Keycloak's SMTP and ties the backend to Brevo. Spring Mail on the same SMTP settings works with Mailpit locally and with any provider.

### Thymeleaf templates for the invite email (decided against)

A template engine for one short email. Message-bundle strings in a fixed text + simple HTML body are enough.

### Synchronous send that fails the invite request (decided against)

A Brevo hiccup would block inviting. Async send plus "Copy link" means an invite can always be delivered.

## Consequences

### Positive

- Every account, new and existing, proves it owns its email. That also closes the invite-acceptance gap.
- Password reset works on prod.
- Invites can reach people at all: by email, with "Copy link" as a fallback.
- Dev and CI test the real verification, reset and invite emails through Mailpit.
- One provider and one set of `SMTP_*` settings for Keycloak and the backend.

### Negative

- New external dependency (Brevo) and new secrets on the VPS.
- Until we have our own domain, mail sent from the Gmail sender is unauthenticated and often goes to spam. **Moving to an authenticated domain is required before real users sign up.**
- Prod realm settings now depend on a script being run by hand; the export alone doesn't change prod.
- Registration takes one more step (open inbox, click link). Existing unverified users are interrupted once at their next login.
- One more container in local dev and CI.

### Neutral

- **Future Consideration to raise:** buy a domain, authenticate it in Brevo (DKIM/SPF/DMARC), switch `SMTP_FROM` and the Brevo sender. Also consider moving the app off sslip.io to that domain.
- CLAUDE.md access points get Mailpit (http://localhost:8025). AGENTS.md / DEV-GUIDE get a line on reading dev emails there.
- ADR-0036's assumption that a Keycloak restart picks up realm-export changes is wrong for realm settings. This ADR records the real behaviour; ADR-0036 isn't edited.
- Unverified Keycloak accounts (abandoned registrations) build up in Keycloak. A cleanup can come later if they become noise.

## Implementation order

1. **DB: `org_invites.last_sent_at`.** Nullable timestamp, backfilled from `created_at`.
2. **Chore: Mailpit and Keycloak email wiring.**
   - `mailpit` in `docker-compose.yml` and in the CI E2E job (shared Docker network)
   - `smtpServer` → Mailpit in `realm-export.json` (not `verifyEmail` or `emailTheme` yet)
   - `scripts/keycloak-apply-email.sh`
   - `SMTP_*` and `APP_BASE_URL` in `.env.prod.example` and `docker-compose.prod.yml`
   - CLAUDE.md / AGENTS.md lines

   Independent of 1.
3. **Backend 1: invite emails.**
   - Spring Mail dependency and config
   - real `EmailService` with en/sr bundles
   - `InviteEmailRequestedEvent` + async listener after commit
   - `language` on `SendInviteRequest`, `token` on `OrgInviteResponse`, `last_sent_at` on create
   - GreenMail tests

   *Blocked by DB.*
4. **Backend 2: resend invite email.**
   - `POST …/invites/{inviteId}/resend`
   - 5-minute cooldown → `TooManyRequestsException` → 429 + `Retry-After`
   - 409 for invites that aren't pending
   - tests and Postman

   *Blocked by Backend 1.*
5. **Frontend 1: Keycloak email verification and email theme.**
   - `login-verify-email.ftl`, `info.ftl`, login message keys
   - `email/` theme with en/sr texts
   - `verifyEmail: true` + `emailTheme: opsclear` in `realm-export.json`
   - `cy.mailpitLink`
   - registration E2E updated to verify through Mailpit, plus a password-reset E2E

   *Blocked by Chore.*
6. **Frontend 2: invite language, Copy link and Resend.**
   - send `language`
   - "Copy link" and "Resend email" on `OrgInvitesPage`, 429 cooldown message
   - i18n
   - E2E: invite → email in Mailpit → link opens `/invite/:token`

   *Blocked by Backend 2 and Chore.*
7. **Chore: enable email on prod.** Manual rollout checklist:
   - Brevo account, dedicated Gmail sender verified
   - `.env.prod` `SMTP_*` and `APP_BASE_URL`
   - deploy
   - run `keycloak-apply-email.sh` (email settings only) and note the unverified users it lists
   - Test connection on port 2525
   - tell the team that they'll be asked to verify at their next login
   - run `keycloak-apply-email.sh --enable-verification`
   - log in as a previously unverified test user, verify, and land in the app
   - register a test account, reset a password and send an invite on prod

   *Blocked by Backend 1 and Frontend 1.*

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`): `language` on send invite, `token` on invite responses, the new resend request
- [ ] Add example requests (resend, resend inside the cooldown → 429)
- [ ] Update environment variables if needed (none for Postman)
- [ ] Test the flow manually before marking complete

## References

- JOB-255: source Future Consideration (provider research)
- ADR-0012 / ADR-0036: Keycloak auth UI and custom auth pages (theme pattern, deploy)
- ADR-0023: User preferences (language stays in the frontend)
- ADR-0026: Organisation layer (invites)
- ADR-0037: Localization (en/sr)
- ADR-0049: E2E coverage plan (auth-flow specs)
- ADR-0051: In-app notification centre (async listener pattern)
- ADR-0058: API rate limiting (429 + `Retry-After` shape)
- DigitalOcean, Why is SMTP blocked?: https://docs.digitalocean.com/support/why-is-smtp-blocked/
- Brevo help centre (SMTP relay settings): https://help.brevo.com/
- Keycloak server admin guide, email settings and required actions: https://www.keycloak.org/docs/latest/server_admin/
- Mailpit: https://mailpit.axllent.org/
