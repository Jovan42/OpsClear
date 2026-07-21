# ADR-0037: Localization (i18n) Support

**Status:** Proposed
**Date:** 2026-07-21
**Author:** Jovan Manojlovic

## Context

OpsClear is English-only today. This phase (PRJ-007: UI/UX Polish & Localization) adds Serbian as a second, user-switchable locale — the first step of a broader i18n effort, not a one-off translation pass.

Two prior decisions directly shape this ADR:

- **ADR-0018** and **ADR-0023** both considered and rejected a backend `user_preferences` table in favor of a flat `localStorage` object, on the grounds that server-side storage is "backend work with no user-visible benefit." ADR-0018 explicitly named locale as deferred, pending "the i18n infrastructure" — this ADR is that infrastructure.
- **ADR-0036** (Custom Branded Keycloak Auth Pages) mandates that every string in the new Keycloak theme route through `msg()` keys, specifically so that Serbian text on the login/register/reset pages is a content-only addition once this ADR ships — no template rework. This ADR treats that as a given, not something to re-decide.

## Decision

Ship English + Serbian via `react-i18next` / `i18next`, with the locale preference stored in the existing `localStorage`-based preferences object (`frontend/src/hooks/usePreferences.ts`), not on the backend. RTL languages are out of scope. API error `message` text stays English for now; only the small, fixed set of error `error` categories gets translated.

## Product decisions

- User-switchable locale (not browser-auto-detect-only) — a language switcher, not silent detection, so a user's choice is explicit and predictable.
- Locale scope for this milestone: English and Serbian only.
- RTL support (Arabic, Hebrew, etc.) is explicitly out of scope — separate future decision if it's ever needed; no RTL-readiness work done now.
- Keycloak auth-page text (login/register/forgot-password/reset-password) gets Serbian translations as a follow-up to this ADR, using ADR-0036's `msg()`-key theme — a `messages_sr.properties` file and appending `sr` to the realm's `supportedLocales`, with no template changes.

## Technical design

### Database

None. Locale preference is a client-side-only setting.

### API

None for locale itself. For error responses: `ErrorResponse`'s `error` field (the coarse HTTP-level category — "Not Found", "Conflict", "Bad Request", "Validation Error", "Forbidden", "Internal Server Error") gets translated on the frontend from a small fixed dictionary (~6 entries). The `message` field (detailed, per-case text like `ErrorMessages.Job.NOT_FOUND`) stays English.

This is a deliberate scope cut, not an oversight: `ErrorMessages` constants are plain strings today, not message keys, and `GlobalExceptionHandler` has no locale resolution. Localizing `message` properly means turning every `ErrorMessages` constant into a key backed by a `MessageSource`/properties bundle and resolving locale from a header or similar — real backend engineering, not polish, and out of scope for a phase explicitly scoped as frontend-only. The category-only translation covers the chrome around every error (toast titles, badges) cheaply; the detailed text remains English until/unless full backend message-key localization becomes its own future phase.

### Backend

None (see above — no locale storage, no message-key refactor in this ADR).

### Frontend

- `react-i18next` + `i18next` added as dependencies.
- All UI strings extracted into `en.json` / `sr.json` translation files.
- Language switcher component — placement TBD during implementation (settings page vs. a topbar control); either is consistent with this ADR, it's a UI-detail decision for the implementation job, not an architectural one.
- `locale` added to the `Preferences` interface in `usePreferences.ts`, alongside `theme` and the existing dashboard/job-list defaults — same storage mechanism, same precedent (ADR-0018, ADR-0023).
- A small fixed dictionary maps the `ErrorResponse.error` category strings to translated labels, used wherever errors are surfaced (toasts, inline error states).

### Constraints & edge cases

- Scope is English + Serbian only; no RTL CSS work.
- Detailed backend error `message` text is not translated in this phase — a known, intentional gap, not a bug.
- Keycloak page localization depends on ADR-0036 shipping first (or concurrently) — this ADR does not re-litigate that theme's architecture, only consumes the `msg()`-key structure it establishes.

## Alternatives considered

### Store locale preference in the database (`UserModel`/`users` table)

Considered, since locale could arguably be "identity" rather than a UI preference — e.g., it might matter for future server-rendered content like emails. Rejected for now: there's no current feature that sends server-rendered, locale-sensitive content (no transactional emails exist yet), and ADR-0018/ADR-0023 already established the precedent that UI-preference-shaped data belongs in `localStorage` unless there's a concrete cross-device or server-side need. The dormant `preferences JSONB` column on `users` (unused since `V001__init_mvp.sql`) remains available if that need materializes later — this ADR doesn't touch it.

### Browser-locale auto-detection only, no manual switcher

Simpler — no UI needed. Rejected: auto-detection alone means a user can't override a wrong guess (e.g., an English-preferring user with a Serbian-configured OS, or vice versa), and the whole point of shipping Serbian is for users to be able to deliberately choose it.

### Full backend error-message localization now

Turn every `ErrorMessages` constant into a `MessageSource`-backed key, resolve locale server-side, localize `message` text fully. Rejected for this phase: `ErrorMessages` is already centralized (a real advantage — this refactor is more tractable than in a codebase with scattered inline strings), but it's still a change to every exception-throwing call site plus new locale-resolution plumbing in `GlobalExceptionHandler` — backend engineering that doesn't fit a phase scoped as frontend-only. Worth a dedicated future ADR if error-detail localization becomes a real ask.

### RTL support included now

Rejected — no target market requires it yet, and RTL CSS work (logical properties, mirrored layouts, icon flipping) is substantial enough to deserve its own scoping exercise if and when it's actually needed, rather than speculative work now.

## Consequences

### Positive

- Zero backend/database changes — locale preference follows the exact pattern already validated twice (ADR-0018, ADR-0023) for UI-preference data
- Keycloak auth pages localize as a content-only change thanks to ADR-0036's `msg()`-key architecture — no cross-ADR rework
- Error chrome (categories) gets translated cheaply without needing a backend message-key system

### Negative

- Detailed backend error messages remain English-only until a possible future backend localization phase — a small but real inconsistency (category translated, detail text not) that users may notice
- Locale preference doesn't follow a user across devices (consistent with all other `localStorage`-based preferences today, not a new limitation introduced here)

### Neutral

- RTL remains entirely unaddressed — no readiness work, no CSS logical-property migration; a future locale requiring RTL would need its own scoping pass
- The dormant `preferences JSONB` column on `users` stays unused; this ADR doesn't activate or repurpose it

## Implementation order

1. `react-i18next` / `i18next` dependencies + `en.json` / `sr.json` scaffolding
2. Extract existing UI strings into translation keys
3. `locale` field added to `usePreferences.ts` `Preferences` interface + defaults
4. Language switcher component
5. Error-category translation dictionary wired into toast/inline error rendering
6. (Follow-up, depends on ADR-0036 shipping) `messages_sr.properties` + `sr` added to realm `supportedLocales` for the Keycloak auth pages

## References

- [ADR-0018: User Settings](0018-user-settings.md) — established `localStorage`-only preference storage, deferred locale pending i18n infrastructure
- [ADR-0023: User Preferences](0023-user-preferences.md) — extended the same `localStorage` pattern, also rejected a `user_preferences` table
- [ADR-0036: Custom Branded Keycloak Auth Pages](0036-keycloak-custom-auth-pages.md) — the `msg()`-key theme architecture this ADR's Keycloak follow-up depends on
- JOB-126 (PRJ-007 / MIL-018): Localization (i18n) support
