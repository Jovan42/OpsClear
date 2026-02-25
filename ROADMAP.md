# OpsClear - Roadmap & Tasks

**Last Updated:** 2026-02-24

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
3. Create PR, merge to `main`
4. Update this file: check off completed tasks

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
- [ ] Implement `Job` entity (name, client, responsible, deadline, status)
- [ ] Implement status enum: `NEW`, `IN_PROGRESS`, `COMPLETED`
- [ ] Implement `JobRepository`
- [ ] Implement `JobService` (CRUD, status transitions)
- [ ] Create `JobController` (CRUD endpoints)
- [ ] Add project-scoped access (user sees only their project's jobs)
- [ ] Add permission checks (Member sees assigned only)
- [ ] Write integration tests

---

## Phase 4: Blocking (Backend)

**Goal:** Mark jobs as blocked with reason

### Module 4.1: Blocking Feature

- [ ] **DOC:** Write ADR for blocking model
- [ ] Create Flyway migration: add blocking fields to `jobs`
- [ ] Update `Job` entity: `blocked`, `blocked_by`, `blocked_reason`, `blocked_at`
- [ ] Implement block/unblock in `JobService`
- [ ] Add endpoints: POST /jobs/{id}/block, POST /jobs/{id}/unblock
- [ ] Write integration tests

---

## Phase 5: Notes (Backend)

**Goal:** Immutable notes attached to jobs

### Module 5.1: Notes Feature

- [ ] **DOC:** Write ADR for notes model (immutability, audit)
- [ ] Create Flyway migration: `notes` table
- [ ] Implement `Note` entity (job_id, author_id, content, created_at)
- [ ] Implement `NoteRepository`
- [ ] Implement `NoteService` (create only, no update/delete)
- [ ] Create `NoteController` (POST, GET list)
- [ ] Write integration tests

---

## Phase 6: Approvals (Backend)

**Goal:** Request and process approvals

### Module 6.1: Approvals Feature

- [ ] **DOC:** Write ADR for approval workflow
- [ ] Create Flyway migration: `approvals` table
- [ ] Implement `Approval` entity (job_id, requester, approver, status, comment)
- [ ] Implement approval status: `PENDING`, `APPROVED`, `REJECTED`
- [ ] Implement `ApprovalRepository`
- [ ] Implement `ApprovalService` (request, approve, reject)
- [ ] Create `ApprovalController`
- [ ] Write integration tests
- [ ] Squash and rename all Flyway migrations into single `V001__init_mvp.sql` (zero-padded format)

---

## Phase 7: Frontend

**Goal:** React app consuming the API

### Module 7.1: Setup

- [ ] **DOC:** Write ADR for frontend architecture (state management, routing, styling)
- [ ] Scaffold React + Vite + TypeScript
- [ ] Set up routing (React Router)
- [ ] Set up API client (axios/fetch + JWT handling)
- [ ] Set up styling approach (Tailwind / CSS modules / etc)

### Module 7.2: Auth Screens

- [ ] **DOC:** Write ADR for auth UI approach (Keycloak redirect vs custom UI)
- [ ] Login page (custom UI proxying to Keycloak, no redirect)
- [ ] Register page (custom UI via Keycloak Admin API)
- [ ] Password reset flow
- [ ] Auth state management
- [ ] Protected route wrapper

### Module 7.3: Projects Screens

- [ ] Project list
- [ ] Create project
- [ ] Project settings
- [ ] Member management (invite, remove, change role)

### Module 7.4: Jobs Screens

- [ ] Job list (with filters)
- [ ] Job detail view
- [ ] Create/edit job form
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

---

## Future (Post-MVP)

- [ ] Organizations (group projects under company)
- [ ] Email notifications
- [ ] React Native mobile app
- [ ] OAuth (Google, Microsoft)
- [ ] Webhooks / API integrations
- [ ] User-facing documentation (Docusaurus)

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
