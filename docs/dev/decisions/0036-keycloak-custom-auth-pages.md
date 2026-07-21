# ADR-0036: Custom Branded Keycloak Auth Pages

**Status:** Proposed
**Date:** 2026-07-21
**Author:** Jovan Manojlovic

## Context

The login, register, forgot-password, and reset-password pages currently use Keycloak's default theme — generic and unbranded. Users get redirected away from OpsClear's own visual identity for the entire auth flow. ADR-0012 (Auth UI Approach) accepted the Keycloak-redirect flow for MVP but explicitly deferred branding to a later iteration, naming "Level 2 — Full HTML via FreeMarker templates" as the future path once it was worth doing.

This is now that iteration — part of the PRJ-007 (UI/UX Polish & Localization) phase, JOB-127.

A related piece of context shapes the design below: JOB-126 (Serbian localization, same phase) depends on these pages existing before Keycloak's own auth-page text can be localized. Rather than build both at once, this ADR treats localization-readiness as a **constraint on how JOB-127 is built**, not a feature it delivers — see "Message-key architecture" below.

## Decision

Override Keycloak's login theme with a custom `opsclear` theme using FreeMarker (`.ftl`) template overrides — the "Level 2" path from ADR-0012. Scope for this milestone: **login, register, forgot-password (request), and reset-password (set new password)** — the four pages virtually every user encounters. Less-common states (expired reset link, account lockout, email verification) are left on Keycloak's default theme for now via `parent=keycloak` inheritance, and picked up as a fast-follow if they prove worth it in practice.

The theme is built in English only for this milestone, but every string is routed through Keycloak's `msg()` key mechanism rather than hardcoded — see below. Serbian text itself is out of scope here and lands in the JOB-126 milestone as a content-only addition.

## Technical design

### Theme structure

```
keycloak/themes/opsclear/
  login/
    theme.properties          # parent=keycloak, locales=en, styles=css/theme.css
    login.ftl
    register.ftl
    login-reset-password.ftl  # "forgot password" request screen
    login-update-password.ftl # set-new-password screen (after clicking the reset link)
    resources/
      css/theme.css
      img/                    # logo, favicon
    messages/
      messages_en.properties
```

`parent=keycloak` in `theme.properties` means any page we don't override (`error.ftl`, `login-verify-email.ftl`, lockout screens, etc.) automatically falls back to Keycloak's default template — nothing breaks, it's just unbranded until a follow-up covers it.

### Visual tokens

The frontend already defines its brand tokens as CSS custom properties in `frontend/src/index.css` (`--brand`, `--brand-dark`, `--brand-light`, `--font-sans`, under Tailwind v4's `@theme inline` block). `theme.css` duplicates these values by hand — there's no shared build step between the React app and the Keycloak theme, so a future brand-color change needs updating both places. Acceptable for now; not worth building a token-sync pipeline for four pages.

### Message-key architecture

Every user-facing string in the four templates is routed through `${msg("key")}`, resolved against `messages_en.properties` — never hardcoded directly in the `.ftl` markup, even though only English ships in this milestone. The locale-switcher block that Keycloak's default templates already include (conditionally rendered when more than one locale is supported) is preserved as-is in our overrides rather than stripped out.

This is the specific mechanism that makes JOB-126's later work on these pages a **content-only change**: add `messages_sr.properties` with translated values and append `sr` to the realm's `supportedLocales` — no template edits, no re-test of the FTL structure. Building it this way costs nothing extra now (the indirection is trivial) and avoids redoing the page-building work when JOB-126 lands.

### Realm configuration (`keycloak/realm-export.json`)

Currently: `loginTheme` unset (default), `internationalizationEnabled` unset, `registrationAllowed: true`, `resetPasswordAllowed: true` (both already on — no realm-flow changes needed, just theming).

Changes:
- `loginTheme: "opsclear"`
- `internationalizationEnabled: true`
- `supportedLocales: ["en"]`

Turning on `internationalizationEnabled` with a single locale has no visible effect — Keycloak only renders the language dropdown when more than one locale is supported — but pre-wires the realm so JOB-126 can activate Serbian by appending `sr` to `supportedLocales` with no other realm-structure change.

### Deployment

No other part of this repo publishes a container image — `deploy.yml` deploys backend and frontend via `git pull` + `docker compose up -d --build <service>` on the VPS, and Keycloak already bind-mounts `realm-export.json` directly from the repo checkout. The theme follows the same pattern rather than introducing a new one:

- `docker-compose.yml` and `docker-compose.prod.yml`: add a bind mount on the `keycloak` service — `./keycloak/themes:/opt/keycloak/themes`.
- `.github/workflows/deploy.yml`: add a `keycloak` path filter (`keycloak/**`) and a `deploy-keycloak` job mirroring `deploy-backend`/`deploy-frontend` in shape. No `--build` needed (no image changes, just mounted files) — `docker compose ... up -d keycloak` after `git pull` is enough to pick up new files; a restart clears Keycloak's theme cache.

## Alternatives considered

### CSS-only theming (ADR-0012's "Level 1")

Mount just a `theme.properties` + `login.css` without overriding the HTML. Rejected — Keycloak's own high-specificity CSS can still win against page-structure changes, and the goal here (making the redirect feel seamless with the React app) needs layout control, not just color/font overrides.

### Hardcode English text now, extract to message keys when JOB-126 lands

Simpler for a first pass — no `msg()` indirection to think about. Rejected: it would mean re-touching every template when Serbian is added, which is exactly the rework the message-key approach avoids for a cost of essentially zero extra effort now.

### Ship Serbian text as part of this milestone

Considered, since JOB-126 explicitly names these pages as a dependency for Keycloak-side localization. Rejected for sequencing: JOB-126 hasn't yet decided the app's translation tone/glossary/infra, and building bilingual templates ahead of that risks doing it inconsistently and redoing it. Keeping this milestone strictly visual/branding work, with the realm pre-wired (`internationalizationEnabled: true`) for a content-only follow-up, is cleaner.

### Custom Keycloak image built in CI

Bake the theme into a purpose-built Keycloak image, versioned and published like a normal service image. Rejected — no other service in this repo is deployed that way; the existing `git pull` + bind-mount + restart pattern (already used for `realm-export.json`) handles this with zero new CI machinery.

### Custom-style all edge/error states in v1 (expired link, lockout, verify email)

Rejected for v1 — these are low-traffic paths, `parent=keycloak` fallback means they render correctly (just unbranded) rather than breaking, and covering them can be a fast-follow once the core four pages are live and reviewed.

## Consequences

### Positive

- JOB-126's later work on these pages becomes a content-only change (one properties file + one realm-config list entry) — no FTL rework, no re-test of template structure
- Deployment reuses the exact `git pull` + `docker compose up -d` pattern already used for backend/frontend and for the realm export — no new CI machinery, no image registry introduced
- `parent=keycloak` inheritance means uncovered pages (error, verify-email, lockout) still work correctly, just unbranded — no broken auth states

### Negative

- The four core pages look consistent with the app; expired-link, lockout, and verify-email screens remain visually inconsistent (default Keycloak look) until a fast-follow covers them
- Brand tokens are manually duplicated between `frontend/src/index.css` and the theme's `theme.css` — no shared source of truth, so a future brand change requires updating both by hand

### Neutral

- `internationalizationEnabled: true` ships with only `en` in `supportedLocales` — no visible change for users now, but removes a realm-config step from JOB-126's scope later
- Adds a `keycloak/themes/` directory and a new bind mount to both `docker-compose.yml` and `docker-compose.prod.yml`, plus a new path-filtered job in `deploy.yml` — small, permanent additions to the deploy surface, consistent in shape with the existing backend/frontend jobs

## Implementation order

1. `keycloak/themes/opsclear/login/` skeleton — `theme.properties`, `resources/css/theme.css` (tokens ported from `frontend/src/index.css`), `resources/img/`
2. `messages_en.properties` + `login.ftl`, `register.ftl`, `login-reset-password.ftl`, `login-update-password.ftl`, all text routed through `msg()` keys
3. `keycloak/realm-export.json`: `loginTheme: opsclear`, `internationalizationEnabled: true`, `supportedLocales: [en]`
4. Bind mount `./keycloak/themes:/opt/keycloak/themes` in `docker-compose.yml` and `docker-compose.prod.yml`
5. `deploy.yml`: `keycloak` path filter + `deploy-keycloak` job (restart only, no `--build`)
6. Manual verification against a running Keycloak: all four flows render correctly end-to-end; confirm a container restart picks up template edits

## References

- [ADR-0012: Auth UI Approach](0012-auth-ui-approach.md) — named this exact "Level 2" path as the deferred future work this ADR now implements
- JOB-127 (PRJ-007 / MIL-019): Custom branded Keycloak login/register/reset pages
- JOB-126 (PRJ-007 / MIL-018): Localization (i18n) support — depends on this ADR's message-key architecture
