# OpsClear - Roadmap & Tasks

**Last Updated:** 2026-03-03

---

## Workflow

**Small, focused PRs** - one module can have multiple branches/PRs if needed.

Branch naming: `feature/<phase>.<module>-<description>`

Examples for Module 1.1 (Auth):
- `feature/1.1-auth-adr` - ADR documentation
- `feature/1.1-auth-user-entity` - User entity + migration
- `feature/1.1-auth-jwt` - JWT service + security filter
- `feature/1.1-auth-endpoints` - Auth controller + tests

Process:
1. Create branch from `main`
2. Make focused changes (1-3 related tasks)
3. **Write tests as part of the same PR** — no separate test tasks
4. Create PR, merge to `main`
5. Update this file: check off completed tasks

---

## Phase 1: Authentication (Backend)

**Goal:** Users can register and login via Keycloak

### Module 1.1: Keycloak Setup

- [x] **DOC:** Write ADR for authentication design (Keycloak)
- [x] Update docker-compose with Keycloak service
- [x] Configure Keycloak realm `opsclear`
- [x] Configure Keycloak clients (frontend, backend)
- [x] Export realm config to JSON (for reproducible setup)

### Module 1.2: User Sync

- [x] **DOC:** Write ADR for user sync strategy (covered in 0002)
- [x] Create Flyway migration: `users` table
- [x] Implement `User` entity
- [x] Implement `UserRepository`
- [x] Implement `UserSyncService` (sync from JWT on login)
- [x] Update Spring Security for OAuth2 resource server
- [x] Write integration tests
- [x] Update Postman collection with auth flow

### Module 1.3: CI/CD Pipeline

- [x] **DOC:** Write ADR for CI/CD strategy
- [x] Create GitHub Actions workflow (`.github/workflows/ci.yml`)
- [x] Configure build step (`./gradlew build`)
- [x] Configure test step (unit + integration, separate jobs)
- [x] Add Checkstyle for code linting
- [x] Configure Checkstyle rules (Google style, 4-space indent)
- [x] Setup branch protection (CI runs on PRs; enforcement requires GitHub Team for private repos)

---

## Phase 2: Projects & Members (Backend)

**Goal:** Users can create projects and invite team members

### Module 2.1: Projects

- [x] **DOC:** Write ADR for project model and multi-tenancy approach
- [x] Create Flyway migration: `projects` table
- [x] Implement `Project` entity
- [x] Implement `ProjectRepository`
- [x] Implement `ProjectService`
- [x] Create `ProjectController` (CRUD endpoints)
- [x] Write integration tests

### Module 2.2: Project Membership

- [x] **DOC:** Write ADR for roles and permissions model
- [x] Create Flyway migration: `project_members` table
- [x] Implement `ProjectMember` entity (user + project + role)
- [x] Implement role enum: `OWNER`, `ADMIN`, `MEMBER`
- [x] Implement `ProjectMemberRepository`
- [x] Implement `ProjectMemberService` (invite, remove, change role)
- [x] Create `ProjectMemberController`
- [x] Add permission checks to project endpoints
- [x] Write unit tests
- [x] Write integration tests

### Module 2.3: Mapper Investigation

- [x] **DOC:** Investigate MapStruct vs manual mapping, write ADR
- [x] Implement mapper layer if beneficial (decided: keep manual `from()` — see ADR-0006)

---

## Phase 3: Jobs (Backend)

**Goal:** CRUD for jobs with status tracking

### Module 3.1: Job Entity & API

- [x] **DOC:** Write ADR for job model and status flow
- [x] Create Flyway migration: `jobs` table
- [x] Implement `Job` entity (name, client, responsible, deadline, status)
- [x] Implement status enum: `NEW`, `IN_PROGRESS`, `COMPLETED`
- [x] Implement `JobRepository`
- [x] Implement `JobService` (CRUD, status transitions)
- [x] Create `JobController` (CRUD endpoints)
- [x] Add project-scoped access (user sees only their project's jobs)
- [x] Add permission checks (Member sees assigned only)
- [x] Write integration tests

---

## Phase 4: Blocking (Backend)

**Goal:** Mark jobs as blocked with reason

### Module 4.1: Blocking Feature

- [x] **DOC:** Write ADR for blocking model
- [x] Create Flyway migration: `project_block_reasons` table + blocking fields on `jobs`
- [x] Update `Job` entity: `blocked_by`, `blocked_reason_id`, `blocked_reason`, `blocked_at`
- [x] Implement `BlockReasonModel`, `BlockReasonRepository`, `BlockReasonService`
- [x] Extend `JobService`: block/unblock via `PATCH /status`, find-or-create block reasons
- [x] Add `BlockReasonController`: `GET /block-reasons`, `DELETE /block-reasons/{id}`
- [x] Write unit tests (100% branch coverage on all services)
- [x] Write integration tests for blocking and block-reason endpoints

---

## Phase 5: Notes (Backend)

**Goal:** Immutable notes attached to jobs

### Module 5.1: Notes Feature

- [x] **DOC:** Write ADR for notes model (immutability, audit)
- [x] Create Flyway migration: `notes` table
- [x] Implement `NoteModel` (job_id, author_id, content, created_at — no soft delete)
- [x] Regenerate jOOQ schema sources + implement `NoteRepository`
- [x] Implement `NoteService` (create, listByJob, listByProject — no update/delete)
- [x] Create `NoteController` (POST, GET by job, GET by project grouped) + integration tests

---

## Phase 6: Approvals (Backend)

**Goal:** Request and process approvals

### Module 6.1: Approvals Feature

- [x] **DOC:** Write ADR for approval workflow (ADR-0010)
- [x] Create Flyway migration: `approvals` table
- [x] Implement `ApprovalModel` + `ApprovalStatus` enum (`PENDING`, `APPROVED`, `REJECTED`)
- [x] Regenerate jOOQ schema sources + implement `ApprovalRepository`
- [x] Implement `ApprovalService` (request, decide, listByJob, listPendingByProject)
- [x] Create `ApprovalController` + DTOs + integration tests
- [x] Squash and rename all Flyway migrations into single `V001__init_mvp.sql` (zero-padded format)

---

## Phase 7: Frontend

**Goal:** React app consuming the API

### Module 7.1: Setup

- [x] **DOC:** Write ADR for frontend architecture (state management, routing, styling)
- [x] Scaffold React + Vite + TypeScript
- [x] Set up routing (React Router)
- [x] Set up API client (axios/fetch + JWT handling)
- [x] Set up styling approach (Tailwind / CSS modules / etc)

### Module 7.2: Auth Screens

- [x] **DOC:** Write ADR for auth UI approach — decided: Keycloak redirect (Auth Code + PKCE)
- [x] Login page — handled natively by Keycloak (no custom page needed for MVP)
- [x] Register page — handled natively by Keycloak (no custom page needed for MVP)
- [x] Password reset flow — handled natively by Keycloak (no custom page needed for MVP)
- [x] Auth state management — `AuthProvider` + `useAuth` hook (keycloak-js)
- [x] Protected route wrapper — entire app gated via `onLoad: 'login-required'`
- [ ] **Future (#146):** Fully custom login/register/reset pages via FTL template overrides

### Module 7.3: Projects Screens

- [x] **DOC:** Write ADR for projects screens (app shell, card list, modal create, settings, role guards, query key conventions)
- [x] Project list + create project modal
- [x] Project settings (edit details, members, delete)
- [x] Member management (invite by email typeahead, role change, remove)

### Module 7.4: Jobs Screens

- [x] Job list (with filters)
- [ ] Job detail view
- [x] Create/edit job form
- [ ] Status change controls
- [ ] Block/unblock modal
- [ ] Notes section
- [ ] Request approval button

### Module 7.5: Approvals Screens

- [ ] Approval queue (for Owner/Admin)
- [ ] Approve/reject modal

---

## Phase 8: Dashboard & Polish

**Goal:** Production-ready MVP

### Module 8.1: Dashboard

- [ ] **DOC:** Write ADR for dashboard data aggregation
- [ ] Backend: `DashboardService` + `DashboardController`
- [ ] Frontend: Dashboard home screen (blocked, in-progress, awaiting)

### Module 8.2: Polish

- [ ] Mobile responsive design
- [ ] Loading states, error handling
- [ ] In-app notifications
- [ ] Performance optimization
- [ ] Security audit

---

## Chores & Tech Debt

### jOOQ Migration

- [x] Replace Spring Data JPA / Hibernate with jOOQ
- [x] Add `nu.studer.jooq` codegen plugin; generate DSL classes from Flyway schema
- [x] Remove `@Entity` classes; introduce plain model layer (`UserModel`, `ProjectModel`)
- [x] Rewrite repositories using `DSLContext` (upsert, explicit JOINs, soft-delete queries)
- [x] Re-enable Flyway in test profile; remove Hibernate `create-drop`
- [x] Update all unit and integration tests
- [x] Replace `fetchOne()` with `fetchSingle()` across all jOOQ repositories (#130)

### Code Quality

- [x] Bump `postgresql` driver to 42.7.7 — CVE-2025-49146 (#130)
- [x] Extract `ApiPaths` test helper — centralise all integration test URL strings (#133)

---

## Future (Post-MVP)

- [ ] Organizations (group projects under company)
- [ ] Email notifications
- [ ] React Native mobile app
- [ ] OAuth (Google, Microsoft)
- [ ] Webhooks / API integrations
- [ ] User-facing documentation (Docusaurus)
- [ ] Cross-project approval queue — `GET /api/approvals/pending` returning all pending approvals across all projects where the caller is OWNER or ADMIN (action queue for the dashboard)

---

## Completed

- [x] Project documentation (README, TECHNICAL, CLAUDE.md)
- [x] Backend scaffolding (Spring Boot, Gradle, packages)
- [x] Docker Compose with PostgreSQL
- [x] Security config (stateless, JWT-ready)
- [x] Health endpoint + SpringDoc OpenAPI
- [x] ADR template and structure
- [x] Roadmap planning
- [x] Postman collection setup (manual testing)

---

## Legend

| Prefix | Meaning |
|--------|---------|
| **DOC:** | Documentation task (ADR) - do first |
| _(none)_ | Implementation task |
