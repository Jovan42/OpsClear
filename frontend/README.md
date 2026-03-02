# OpsClear Frontend

React 19 + Vite 6 + TypeScript SPA consuming the OpsClear REST API.

## Tech Stack

| Concern | Library |
|---------|---------|
| Build | Vite 6 |
| Framework | React 19 + TypeScript (strict) |
| Routing | React Router v7 |
| API client | Axios + request interceptor |
| Auth adapter | keycloak-js |
| Server state | TanStack Query v5 |
| Styling | Tailwind CSS v4 |
| Forms | React Hook Form + Zod |

See [ADR-0011](../docs/dev/decisions/0011-frontend-architecture.md) for rationale.

---

## Pages

### Auth

| Page | Route | Description |
|------|-------|-------------|
| Login | `/login` | Custom login form (username + password, proxied to Keycloak) |
| Register | `/register` | Registration form via Keycloak Admin API |
| Password Reset | `/reset-password` | Request password reset email |

Auth approach: see [ADR-0012](../docs/dev/decisions/0012-auth-ui-approach.md) _(pending)_.

---

### Projects

| Page | Route | Description |
|------|-------|-------------|
| Project List | `/projects` | All projects the user is a member of |
| Create Project | `/projects/new` | Form to create a new project |
| Project Settings | `/projects/:projectId/settings` | Edit name/description, manage members |

---

### Jobs

| Page | Route | Description |
|------|-------|-------------|
| Job List | `/projects/:projectId/jobs` | All jobs in the project with filters (status, assigned) |
| Job Detail | `/projects/:projectId/jobs/:jobId` | Full job view — status, blocking info, notes, approvals |
| Create Job | `/projects/:projectId/jobs/new` | Form to create a new job |
| Edit Job | `/projects/:projectId/jobs/:jobId/edit` | Edit job name, client, responsible, deadline |

**Inline actions on Job Detail (no separate page):**

- Status change (New → In Progress → Completed)
- Block / Unblock (with reason)
- Add note
- Request approval

---

### Approvals

| Page | Route | Description |
|------|-------|-------------|
| Approval Queue | `/projects/:projectId/approvals` | All pending approvals in the project (Owner/Admin only) |

Approve/reject is a modal on the Approval Queue page — no separate route.

---

### Dashboard _(Phase 8)_

| Page | Route | Description |
|------|-------|-------------|
| Dashboard | `/projects/:projectId/dashboard` | Summary: blocked jobs, in-progress, awaiting approval |

---

## Project Structure

```
frontend/
├── src/
│   ├── api/              # Axios instance + typed functions per resource
│   ├── auth/             # Keycloak init, AuthContext, useAuth hook
│   ├── components/       # Shared UI (Button, Input, Badge, Modal, ...)
│   ├── features/
│   │   ├── projects/     # ProjectList, CreateProject, ProjectSettings
│   │   ├── jobs/         # JobList, JobDetail, CreateJob, EditJob
│   │   ├── approvals/    # ApprovalQueue
│   │   └── auth/         # Login, Register, ResetPassword
│   ├── router.tsx        # Route definitions
│   ├── types/            # TypeScript types mirroring API response shapes
│   └── main.tsx          # Entry point — Keycloak init, providers
├── index.html
├── vite.config.ts
└── package.json
```

## Commands

```bash
# Install dependencies
npm install

# Start dev server (requires backend + Keycloak running)
npm run dev

# Type check
npm run type-check

# Lint
npm run lint

# Build for production
npm run build
```

## Local Dev Prerequisites

Backend and Keycloak must be running. See root `docker-compose.yml`.

| Service | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080 |
| Keycloak | http://localhost:8180/realms/opsclear |
