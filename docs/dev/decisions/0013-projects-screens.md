# ADR-0013: Projects Screens

**Status:** Accepted
**Date:** 2026-03-03
**Author:** Jovan Manojlovic

## Context

Module 7.3 covers two screens:

1. **Project list** — the app's home screen; shows all projects the authenticated user
   belongs to, with a way to create a new project.
2. **Project settings** — name/description editing, member list, role management, and
   project deletion.

The key decisions are: app shell layout, list presentation style, how create/edit forms
are surfaced (modal vs page), member invite UX given the API constraint, role-based access
guards, and TanStack Query key conventions for the projects domain.

---

## Decision

### 1. App shell layout

A persistent `AppLayout` wrapper component renders on every route. It contains:

- **Top navbar**: brand name (left), user display name + logout button (right).
- **Page content area**: fills the remaining viewport height; each page manages its own
  padding and scroll.

No persistent sidebar for MVP. Navigation between a project's sub-sections (jobs,
approvals, settings) is handled by in-page tabs or a sub-header on the project screens —
not a global sidebar — to keep the mobile layout simple.

The top navbar is always visible. On mobile it collapses to a single line with a hamburger
menu if needed in a later iteration.

### 2. Project list screen (`/projects`)

Displays projects as a **card grid** (2 columns on desktop, 1 on mobile). Each card shows:

- Project name (h2)
- Description (truncated to 2 lines, if present)
- User's role badge (OWNER / ADMIN / MEMBER)

Clicking a card navigates to `/projects/:projectId/jobs`.

A **"New Project" button** in the page header opens a **modal dialog** (not a separate
page). The form has two fields: `name` (required, max 80 chars) and `description` (optional,
max 255 chars). On submit, it calls `POST /api/projects`, closes the modal, and invalidates
the `['projects']` query.

**Why cards over a table:** The project list is expected to be short (1–10 projects per
user for an SME tool). Cards provide a better visual hierarchy and scale better to mobile
than a table with many columns.

**Why modal over a dedicated `/projects/new` page:** The create form is short (2 fields).
A modal avoids a navigation round-trip and keeps the user in context of the list.

### 3. Project settings screen (`/projects/:projectId/settings`)

A single-column settings page with two sections:

**Section 1 — Project details**

An inline edit form (pre-filled with current name and description). The same React Hook
Form + Zod approach used app-wide. Submit calls `PUT /api/projects/:projectId`.

Only OWNER and ADMIN can edit project details (enforced in the UI via the role guard;
the backend enforces it independently).

**Section 2 — Members**

A table with columns: Name / Email, Role (badge), Actions.

- **Role change**: A dropdown next to each member (visible to OWNER and ADMIN only).
  Calls `PUT /api/projects/:projectId/members/:memberId`. Only OWNER can change another
  OWNER's role.
- **Remove member**: A "Remove" button (OWNER and ADMIN only; cannot remove self).
  Calls `DELETE /api/projects/:projectId/members/:memberId`.

**Add member**

The add-member form accepts an email input. As the user types (debounced 300ms), it calls
`GET /api/users?email=<prefix>` and shows a dropdown of matching users (name + email).
Selecting a user populates the hidden `userId` field; the manager then picks a role and
submits. The backend `POST /api/projects/:projectId/members` receives `userId` + `role`
as before.

The `GET /api/users?email=` endpoint is tracked in issue #149 (Chores & Tech Debt). Only
users who have logged in at least once are searchable (they exist in the local `users`
table via `UserSyncFilter`). Creating Keycloak accounts via invite is a separate,
post-MVP feature.

**Delete project**

A "Delete Project" button in a danger zone at the bottom of the settings page (OWNER
only). Opens a confirmation dialog before calling `DELETE /api/projects/:projectId`. On
success, navigates to `/projects`.

### 4. Role-based UI guards

The hook `useProjectRole(projectId)` returns the caller's role for a given project. It is
derived from the `['projects', projectId, 'members']` query and the `useAuth` hook's
`userId`.

| UI element | Minimum role |
|---|---|
| Settings page link | ADMIN |
| Edit project details | ADMIN |
| Invite / remove members | ADMIN |
| Change member roles | ADMIN |
| Delete project | OWNER |

MEMBERs see only the project card (jobs access is handled in Module 7.4). The settings
link is hidden from MEMBERs entirely.

### 5. TanStack Query key conventions

```
['projects']                              — full project list
['projects', projectId]                   — single project
['projects', projectId, 'members']        — member list for a project
```

Mutations invalidate the narrowest relevant key:

- Create project → invalidate `['projects']`
- Update project → invalidate `['projects', projectId]` and `['projects']`
- Add/update/remove member → invalidate `['projects', projectId, 'members']`
- Delete project → invalidate `['projects']`

### 6. Custom query hooks in `features/projects/`

Each feature folder exposes its own TanStack Query hooks:

```
features/projects/
├── ProjectListPage.tsx
├── ProjectSettingsPage.tsx
├── index.ts            # re-exports hooks used by other features
└── useProjects.ts      # useProjectList, useProject, useProjectMembers,
                        #   useCreateProject, useUpdateProject, useDeleteProject,
                        #   useAddMember, useUpdateMember, useRemoveMember,
                        #   useProjectRole
```

---

## Alternatives Considered

### Alternative 1: Dedicated `/projects/new` page instead of a modal

A separate route for creating a project is consistent with the REST resource model and
shareable via URL.

**Why rejected:** The create form is 2 fields. A full page navigation adds unnecessary
friction; there is no use case for bookmarking or sharing the create-project URL. A modal
is the correct pattern for short in-context forms.

### Alternative 2: Persistent sidebar navigation

A fixed left sidebar listing the user's projects with a sub-menu for jobs / approvals /
settings per project.

**Pros:** Persistent context; one-click navigation between any section.

**Cons:** Consumes significant horizontal space on mobile. Adds layout complexity (nested
routing with an `Outlet`). For an MVP with a handful of projects, a flat card list plus
breadcrumb/back navigation is simpler and mobile-friendly.

**Why rejected:** Mobile-first constraint favours a flat layout for MVP. Sidebar can be
revisited in Phase 8 polish.

### Alternative 3: Member invite by raw UUID input

The add-member form accepts a raw Keycloak UUID. No backend search endpoint needed.

**Pros:** No additional backend work.

**Cons:** UUIDs are not discoverable from the UI; managers would need to open Keycloak
Admin Console to find a user's ID. Not usable in production by non-technical users.

**Why rejected:** Not user-friendly. Email-based search (`GET /api/users?email=`) is
implemented instead (issue #149).

---

## Consequences

### Positive

- App shell is defined once and shared across all future screens
- Modal pattern for short forms is established and reusable for jobs/approvals
- Query key convention documented here is adopted for all other feature modules
- Role guard hook (`useProjectRole`) is a reusable primitive for Module 7.4 and 7.5

### Negative

- Only users who have previously logged in are searchable (email search hits the local `users` table)
- No sidebar means extra navigation steps when switching between projects (back to `/projects`, then click another card)

### Neutral

- Settings page is only accessible to OWNER and ADMIN; MEMBER users have no settings link

---

## References

- [ADR-0005: Roles and Permissions](./0005-roles-and-permissions.md)
- [ADR-0011: Frontend Architecture](./0011-frontend-architecture.md)
- [ADR-0012: Auth UI Approach](./0012-auth-ui-approach.md)
