# ADR-0018: User Settings — Storage Strategy and Scope

**Status:** Accepted
**Date:** 2026-03-10
**Author:** Jovan Manojlovic

## Context

A user settings page is needed to let authenticated users manage personal preferences.
The immediate driver is dark mode (#157), but the settings page should be designed
so that additional preferences can be added without structural changes.

The central question is where preferences are stored:

- **localStorage only** — browser-side, zero backend involvement
- **Backend `preferences` column** — a `JSONB` column on the `users` table, synced
  via a dedicated API endpoint
- **Keycloak user attributes** — preferences stored on the identity provider

The scope question: what settings are in range for this phase?

---

## Decision

### 1. Storage — localStorage for MVP

Preferences are stored in `localStorage` under the key `opsclear:preferences` as a
JSON object:

```json
{ "theme": "dark" }
```

No backend changes, no Flyway migration, no new API endpoint for this phase.

**Why not backend:** OpsClear's target users (SME owners, 5–50 employees) overwhelmingly
use one device for work. Cross-device preference sync is not a stated requirement and
adds backend complexity for zero user-visible benefit at MVP stage. localStorage is
sufficient, reliable, and keeps the settings page a pure frontend concern.

**Why not Keycloak attributes:** Tight coupling to the auth provider for a UI preference
is an unnecessary dependency. Keycloak attribute management has no standard REST API
that can be called from the resource server — it would require either the admin REST
API (grants too much privilege) or a custom Keycloak SPI. Disproportionate complexity.

**Migration path:** If cross-device sync becomes a requirement after launch, a
`JSONB preferences DEFAULT '{}'` column can be added to `users` with a single Flyway
migration and a `GET /api/users/me/settings` + `PATCH /api/users/me/settings` API
(tracked in Future Considerations). The frontend `usePreferences` hook is the only
code that needs to change — consumers are insulated.

### 2. Frontend abstraction — `usePreferences` hook

All reads and writes go through a single `usePreferences` hook:

```ts
// hooks/usePreferences.ts
const STORAGE_KEY = 'opsclear:preferences';

type Preferences = {
  theme: 'light' | 'dark' | 'system';
};

const defaults: Preferences = { theme: 'system' };

export function usePreferences() {
  const [prefs, setPrefs] = useState<Preferences>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? { ...defaults, ...JSON.parse(raw) } : defaults;
    } catch {
      return defaults;
    }
  });

  const update = (patch: Partial<Preferences>) => {
    const next = { ...prefs, ...patch };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setPrefs(next);
  };

  return { prefs, update };
}
```

No component reads `localStorage` directly — all go through `usePreferences`.
This is the single seam to replace with an API call if backend storage is adopted later.

### 3. Settings in scope for this phase

| Setting | Values | Default |
|---------|--------|---------|
| Theme | `light` / `dark` / `system` | `system` |

`system` follows the OS `prefers-color-scheme` media query. The settings page shows
a three-way toggle (Light / Dark / System).

Display name editing and locale preference are out of scope for this phase.
Display name changes require a Keycloak profile API call (non-trivial); locale requires
the i18n infrastructure from #148.

### 4. Settings page location

Accessible via a user menu in the AppLayout header (user avatar / name → Settings).
Route: `/settings`.

```
AppLayout
└── header
    └── UserMenu (dropdown)
        ├── Settings  →  /settings
        └── Log out
```

The settings page is a standalone full-page route, not a modal, so it is linkable
and has space for additional settings as the list grows.

### 5. Feature folder structure

```
frontend/src/
├── hooks/
│   └── usePreferences.ts        # localStorage abstraction
├── features/settings/
│   └── SettingsPage.tsx         # /settings route
```

---

## Alternatives Considered

### Alternative 1: JSONB `preferences` column on `users` table

Add `preferences JSONB DEFAULT '{}'` to `users` and expose
`GET /api/users/me/settings` + `PATCH /api/users/me/settings`.

**Pros:** Syncs across devices; preferences survive browser storage clears; single
source of truth.

**Cons:** Requires Flyway migration, new service/controller, additional API round-trip
on app startup to load preferences (or a loading state).

**Why rejected for MVP:** Cross-device sync is not a stated requirement. Adds backend
work with no user-visible benefit for the current target users. Tracked as a future
migration path.

### Alternative 2: Keycloak user attributes

Store preferences as Keycloak user attributes via the admin REST API or account
service.

**Pros:** Centralised with the identity source; survives device changes.

**Cons:** Requires Keycloak admin API or custom SPI; tight coupling to the auth
provider for a UI concern; complex to implement and test; makes preferences
dependent on Keycloak availability.

**Why rejected:** Disproportionate complexity for storing a theme toggle. Wrong
layer of the stack for a UI preference.

### Alternative 3: Global React context without localStorage

Store preferences in React state only; reset to defaults on page reload.

**Pros:** Simplest possible implementation.

**Cons:** Preference resets on every reload — poor UX. Not acceptable even for MVP.

**Why rejected:** Users expect their theme preference to persist across sessions.

---

## Consequences

### Positive

- Zero backend changes for this phase — settings page is a pure frontend feature
- `usePreferences` hook isolates the storage mechanism; migration to backend later
  is a one-file change
- `/settings` route is linkable and extensible
- `system` default respects OS preference out of the box with no user action required

### Negative

- Preferences are per-browser, not per-user — clearing browser storage resets them
- Cross-device sync is not supported until the backend migration is done

### Neutral

- Dark mode implementation details (Tailwind `dark:` class vs CSS variables,
  application to the DOM) are decided in ADR-0019 (dark mode)
- The `UserMenu` component in `AppLayout` is a new addition

---

## Implementation tickets

After this ADR is merged, create the following tickets in Phase 9:

1. `feat(frontend): usePreferences hook — localStorage abstraction for user preferences`
2. `feat(frontend): user settings page — theme toggle and UserMenu entry point`

---

## References

- [ADR-0011: Frontend Architecture](./0011-frontend-architecture.md)
- [ADR-0012: Auth UI Approach](./0012-auth-ui-approach.md)
- [#157: Dark mode theme (ADR-0019)](https://github.com/Jovan42/OpsClear/issues/157)
- [#158: User settings page](https://github.com/Jovan42/OpsClear/issues/158)
- [#148: i18n — future locale preference](https://github.com/Jovan42/OpsClear/issues/148)
