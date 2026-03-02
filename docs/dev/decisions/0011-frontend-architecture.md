# ADR-0011: Frontend Architecture

**Status:** Accepted
**Date:** 2026-03-02
**Author:** Jovan Manojlovic

## Context

OpsClear needs a React frontend that consumes the existing REST API. The core requirements:

- Authenticated requests via JWT (Keycloak-issued tokens)
- Project-scoped navigation: projects → jobs → notes/approvals
- Role-sensitive UI (Owner/Admin see approval queue and management controls; Members see
  their own jobs)
- Mobile-friendly (primary target: field workers on phones)
- Small team — one developer, rapid iteration; no over-engineering

The tech stack (React + Vite + TypeScript) was fixed in ADR-0001. The open decisions are:
routing library, server state management, styling approach, API client, and form handling.

---

## Decision

### Build tool: Vite 6 + React 19 + TypeScript (strict)

Already committed in ADR-0001. TypeScript strict mode is enabled from the start to catch
null/undefined bugs early — especially important for nullable API fields (`approver_id`,
`decided_at`, etc.).

### Routing: React Router v7

React Router v7 is the direct evolution of v6 with improved data loading APIs (loaders,
actions). It is the most widely used React routing library and has first-class TypeScript
support.

File-based routing is not adopted — the app is small enough that explicit `<Routes>` in
a single `router.tsx` file is simpler and easier to reason about for one developer.

**Route structure:**

```
/                         → redirect to /projects
/projects                 → project list
/projects/:projectId      → job list for project
/projects/:projectId/jobs/:jobId  → job detail (notes, approvals)
/projects/:projectId/approvals    → pending approvals queue (Owner/Admin)
/projects/:projectId/settings     → project settings + members
```

### API client: Axios + request interceptor

Axios is used over the native `fetch` API for two reasons:

1. Request interceptors — attach `Authorization: Bearer <token>` on every outgoing request
   from a single place without wrapping every call
2. Response interceptors — handle 401 (token refresh or redirect to login) centrally

A single `apiClient.ts` module exports a configured Axios instance. The token is read
from the Keycloak.js instance on every request — no manual token storage in `localStorage`.

### Auth/token management: keycloak-js

`keycloak-js` is the official Keycloak JavaScript adapter. It handles:

- Silent token refresh via the iframe/check-sso mechanism
- Exposing the current token for the Axios interceptor (`keycloak.token`)
- Providing user profile claims (sub, email, name)

The Keycloak instance is initialised once at app startup (`keycloak.init()`). The auth flow
(login redirect vs custom UI) is covered in a separate ADR-0012.

### Server state: TanStack Query v5

All API data (projects, jobs, notes, approvals) is server state — it lives on the server
and the client needs to fetch, cache, and synchronise it. TanStack Query handles this with
minimal boilerplate:

- Automatic caching and background refetch
- Loading and error states out of the box
- Optimistic updates where needed (job status changes)
- Cache invalidation on mutations (`queryClient.invalidateQueries`)

Global client state (user identity, active project) is held in React Context — no Redux or
Zustand. The combination of TanStack Query for server state + Context for auth/session state
covers all requirements without additional libraries.

### Styling: Tailwind CSS v4

Tailwind CSS provides utility classes that map directly to CSS properties. For a B2B ops
tool with a clear, functional UI and one developer, Tailwind is the fastest way to build
responsive layouts without maintaining a custom stylesheet.

Tailwind v4 drops the `tailwind.config.js` file for CSS-based configuration — simpler setup
with Vite.

No separate UI component library is adopted for MVP. Components are written with plain HTML
elements + Tailwind classes. If a complex component is needed (date picker, combobox), a
headless library (Radix UI) will be added at that point rather than up front.

### Form handling: React Hook Form + Zod

Forms in OpsClear are simple (2-5 fields each). React Hook Form handles controlled inputs
with minimal re-renders. Zod schemas define validation rules and TypeScript types in one
place — the same schema validates form input and types the API request body.

### Project structure

```
frontend/
├── src/
│   ├── api/            # Axios instance + typed API functions per resource
│   ├── auth/           # Keycloak init, AuthContext, useAuth hook
│   ├── components/     # Shared UI components (Button, Input, Badge, etc.)
│   ├── features/       # Feature folders (projects/, jobs/, approvals/, notes/)
│   │   └── jobs/
│   │       ├── JobList.tsx
│   │       ├── JobDetail.tsx
│   │       └── useJobs.ts   # TanStack Query hooks for this feature
│   ├── router.tsx      # Route definitions
│   ├── main.tsx        # App entry, Keycloak init, QueryClientProvider
│   └── types/          # Shared TypeScript types (mirrors API response shapes)
├── index.html
├── vite.config.ts
└── package.json
```

### Summary of chosen libraries

| Concern | Choice | Version |
|---------|--------|---------|
| Build | Vite | 6.x |
| Framework | React | 19.x |
| Language | TypeScript | 5.x (strict) |
| Routing | React Router | 7.x |
| API client | Axios | 1.x |
| Auth adapter | keycloak-js | 26.x |
| Server state | TanStack Query | 5.x |
| Styling | Tailwind CSS | 4.x |
| Forms | React Hook Form | 7.x |
| Validation | Zod | 3.x |

---

## Alternatives Considered

### Alternative 1: Next.js instead of Vite + React Router

Next.js provides SSR, file-based routing, and API routes out of the box.

**Pros:** Batteries included; SEO-friendly (not relevant for a B2B ops tool behind auth).

**Cons:** OpsClear is a fully authenticated SPA — there is no public content to SSR. Next.js
adds complexity (server components, RSC, hydration) with no benefit for this use case. The
backend API already handles all data — Next.js API routes would be redundant.

**Why rejected:** Vite + React Router is simpler and sufficient for an authenticated SPA.

### Alternative 2: Redux Toolkit for state management

Redux Toolkit is the standard Redux experience with less boilerplate.

**Pros:** Predictable state, strong DevTools, well-understood pattern.

**Cons:** For a CRUD app whose state primarily lives on the server, Redux duplicates what
TanStack Query already does. You end up writing reducers for loading states, error states,
and cache invalidation — all of which TanStack Query handles automatically.

**Why rejected:** TanStack Query covers server state. React Context covers the remaining
auth/session state. Redux adds complexity with no benefit at this scale.

### Alternative 3: SWR instead of TanStack Query

SWR is a lighter alternative to TanStack Query from Vercel.

**Pros:** Smaller bundle; simpler API.

**Cons:** TanStack Query has better mutation support, more granular cache invalidation, and
better DevTools. For an app with frequent write operations (status changes, approvals), the
richer mutation API is valuable.

**Why rejected:** TanStack Query is preferred for its mutation/invalidation capabilities.

### Alternative 4: CSS Modules instead of Tailwind

CSS Modules scope styles to the component file, eliminating class name collisions.

**Pros:** Familiar CSS syntax; styles co-located with components.

**Cons:** More files to maintain (`.module.css` per component); no design system constraints
(every developer invents spacing and colour values independently); slower to build responsive
layouts.

**Why rejected:** Tailwind's constraint-based system produces more consistent UI faster,
which is critical for a solo developer building an MVP.

### Alternative 5: Fetch API instead of Axios

The native `fetch` API requires no dependency.

**Pros:** Zero bundle cost; no additional abstraction.

**Cons:** No interceptors — attaching the Bearer token on every call requires a wrapper
function anyway, at which point the abstraction is equivalent to Axios. Axios error handling
(response status checks) is also more ergonomic.

**Why rejected:** The interceptor pattern is the clearest way to centralise auth header
injection; Axios provides this natively.

---

## Consequences

### Positive

- One place to add the auth header (Axios interceptor) — no risk of forgetting it on a call
- TanStack Query eliminates manual loading/error state management across every component
- Tailwind makes responsive design fast without a custom CSS file
- TypeScript strict mode catches null API fields at compile time
- Feature folders keep related code (component + query hook) together as the app grows

### Negative

- `keycloak-js` init is async — the app must show a loading screen before the router renders
- TanStack Query adds a learning curve for developers unfamiliar with the server state pattern
- Tailwind class lists become verbose on complex components

### Neutral

- No component library up front — individual headless components added if/when needed
- Auth UI flow (login redirect vs. embedded form) is a separate decision (ADR-0012)

---

## References

- [ADR-0001: Initial Tech Stack](./0001-initial-tech-stack.md)
- [ADR-0002: Authentication](./0002-authentication.md)
- [TanStack Query docs](https://tanstack.com/query/v5)
- [React Router v7 docs](https://reactrouter.com)
- [Tailwind CSS v4 docs](https://tailwindcss.com)
- [keycloak-js npm](https://www.npmjs.com/package/keycloak-js)
