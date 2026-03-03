# ADR-0012: Auth UI Approach

**Status:** Accepted
**Date:** 2026-03-03
**Author:** Jovan Manojlovic

## Context

OpsClear uses Keycloak as the identity provider (ADR-0002). The frontend needs to handle
login, registration, and password reset. The key decision is whether to build a custom-styled
UI that submits credentials directly, or to delegate the auth UI entirely to Keycloak's
built-in login pages via the standard OAuth2 redirect flow.

The ROADMAP originally noted "custom UI proxying to Keycloak, no redirect" — this ADR
revisits that assumption after evaluating the trade-offs.

### Current state

The scaffold (ADR-0011) already initialises keycloak-js with `onLoad: 'login-required'`,
which triggers the Authorization Code + PKCE flow immediately. Login via Keycloak redirect
was tested and confirmed working. No additional code was needed.

---

## Decision

**Use Keycloak's built-in login pages via Authorization Code + PKCE redirect.**

Keycloak handles login, registration, and password reset. The React app never sees or
handles user credentials. For MVP, the Keycloak login theme is left as default; it can be
customised (branded) later without changing application code.

### Flow

```
User opens app
  → keycloak.init({ onLoad: 'login-required' })
  → Redirect to Keycloak login page
  → User enters credentials directly in Keycloak
  → Keycloak redirects back with auth code
  → keycloak-js exchanges code for tokens (PKCE)
  → App renders with authenticated session
```

### Registration and password reset

Keycloak exposes self-registration and password reset as built-in flows, reachable via
links on the Keycloak login page. No additional frontend screens are needed for MVP:

- **Register**: enabled via Keycloak realm settings (`Registration allowed: ON`)
- **Password reset**: enabled via Keycloak realm settings (`Forgot password: ON`)

Both flows are handled entirely by Keycloak. The user is redirected back to the app after
completing them.

### Protected route wrapper

A `<ProtectedRoute>` component is not needed in the traditional sense. Because keycloak-js
is initialised with `onLoad: 'login-required'`, the entire app is gated — unauthenticated
users are redirected to Keycloak before the React router even renders. The `AuthProvider`
shows a loading screen during init, then renders children only when authenticated.

For role-based access (e.g. hiding the approval queue from MEMBERs), the `useAuth` hook
exposes the user's role per project. Role guards are implemented at the component level, not
the router level, since roles are project-scoped rather than global.

### Keycloak theme customisation (post-MVP)

Keycloak supports custom login themes. There are two levels of customisation, both deferred
to post-MVP:

**Level 1 — CSS only.** Mount a `theme.properties` + `login.css` file into the Keycloak
container. Controls colours, fonts, padding, and hiding/showing elements. Quick, but limited:
the underlying HTML structure is fixed, so Keycloak's own high-specificity CSS can win.

**Level 2 — Full HTML via FreeMarker templates.** Override the `.ftl` template files to
replace the HTML completely. This is the path to fully custom pages — see the section
"Future path: fully custom auth pages" below.

---

## Alternatives Considered

### Alternative 1: Custom login form (Direct Grant / ROPC flow)

Build a React login page (`/login`) that collects username and password, then calls
`POST /realms/opsclear/protocol/openid-connect/token` with `grant_type=password` directly.

**Pros:**
- Fully branded login UI with no redirect
- Feels more like a native app

**Cons:**
- Resource Owner Password Credentials (ROPC) flow is deprecated in OAuth 2.1 and
  considered a security anti-pattern — the client application sees the raw credentials
- Disabled by default in Keycloak 18+ (must be explicitly enabled per client)
- Breaks future MFA, social login, and SSO — these all require a browser redirect
- Requires building and maintaining login, register, and password reset screens
- Credentials travel through the frontend, increasing the attack surface
- Does not work with browser-based security features (password managers expect a redirect)

**Why rejected:** Security risk and maintenance cost outweigh the UX benefit for an MVP.
The ROPC flow is explicitly discouraged by the OAuth 2.1 spec for browser-based apps.

### Alternative 2: Custom login page proxying to Keycloak Admin API

Build a React login form that calls Keycloak's Admin REST API to authenticate users.

**Pros:** Full control over the login UI.

**Cons:** The Admin API is not intended for end-user authentication. It requires the
Admin client secret to be exposed to the frontend (a critical security vulnerability).
Session management, token refresh, and MFA are not handled.

**Why rejected:** Fundamentally insecure — Admin credentials must never be in the browser.

### Alternative 3: Keycloak redirect now, custom theme later

Use the standard redirect flow now (accepted decision) and replace the Keycloak login
theme with a branded version in a later iteration.

**Pros:** Secure by default; branding is possible without changing application code.

**Cons:** None significant — this is the accepted path.

**Why chosen:** This is what we are doing.

---

## Consequences

### Positive

- Zero auth screen code to write or maintain
- Authorization Code + PKCE is the most secure flow for browser-based apps
- Token refresh, session timeout, and logout are handled by keycloak-js
- MFA, social login (Google, Microsoft) can be enabled in Keycloak with no React changes
- Password reset and registration are available immediately via Keycloak's built-in flows
- Confirmed working in the current scaffold

### Negative

- Login page is not branded for MVP (Keycloak default theme)
- Brief redirect away from the app on first load (standard OAuth UX — users are accustomed to it)

### Neutral

- Issues #64 (login page), #65 (register page), #66 (password reset) are closed as
  won't-implement for MVP — Keycloak handles all three natively
- Issue #67 (auth state + protected route) is partially implemented via `AuthProvider`;
  role-based UI guards are handled per-component using `useAuth`

---

## References

- [ADR-0002: Authentication with Keycloak](./0002-authentication.md)
- [ADR-0011: Frontend Architecture](./0011-frontend-architecture.md)
- [OAuth 2.1 — ROPC deprecation](https://oauth.net/2.1/)
- [Keycloak Authorization Code Flow docs](https://www.keycloak.org/docs/latest/securing_apps/)
- [keycloak-js adapter docs](https://www.keycloak.org/docs/latest/securing_apps/#_javascript_adapter)
