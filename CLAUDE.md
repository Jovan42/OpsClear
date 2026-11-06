# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpsClear is an operational tracking app for small/medium businesses (5-50 employees). It answers: "What's the truth about our work TODAY?"

**Core concept:** Everything revolves around a "Job" with 4 statuses: New → In Progress → Blocked → Completed

**Target users:** SME owners and operational managers tired of constant status calls.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React + Vite + TypeScript |
| Backend | Java 21 + Spring Boot 3.x + Gradle |
| Database | PostgreSQL 16 + Flyway migrations + jOOQ (generated classes, no JPA) |
| Auth | Keycloak (OAuth2 Resource Server + JWT, stateless) |
| API Docs | SpringDoc OpenAPI |
| Testing | JUnit 5 + Mockito + Testcontainers, Vitest, Cypress |
| Infra | Monorepo, Docker, VPS, GitHub Actions |

## Project Structure

```
OpsClear/
├── backend/                # Spring Boot API (Java 21 + Gradle)
│   ├── src/main/java/com/opsclear/
│   │   ├── aop/            # @RequiresAddon feature gating
│   │   ├── config/         # Security, web config
│   │   ├── controller/     # REST controllers
│   │   ├── service/        # Business logic
│   │   ├── repository/     # jOOQ repositories (DSLContext)
│   │   ├── model/          # Domain models (plain classes, not JPA entities)
│   │   ├── dto/            # Request/response DTOs
│   │   ├── exception/      # Custom exceptions + GlobalExceptionHandler
│   │   ├── paddle/         # Paddle billing integration
│   │   ├── scheduler/      # Recurring job scheduling
│   │   └── security/       # API key + JWT auth, UserSyncFilter
│   └── src/main/resources/
│       ├── db/migration/   # Flyway migrations (V{nnn}__{description}.sql)
│       └── application.yml
├── frontend/               # React SPA (Vite + TypeScript), Cypress E2E in frontend/cypress/
├── docs/dev/               # Developer docs
│   ├── decisions/          # Architecture Decision Records (ADRs)
│   └── process/            # Dev process: WORKFLOW, JOBS, BACKEND, FRONTEND, E2E, AGENTS, DEV-GUIDE
├── scripts/                # opsclear CLI (OpsClear API), seed.sh
├── keycloak/               # Realm export, themes
├── api/postman/            # Postman collection
├── .claude/                # Shared Claude Code settings (permissions)
├── docker-compose.yml      # Local infra: postgres, keycloak, pgadmin, portainer
├── CLAUDE.md               # This file (Claude context)
├── TECHNICAL.md            # Technical decisions log
└── README.md               # Project overview
```

## Key Documents

- `README.md` - Product vision, features, target users
- `TECHNICAL.md` - All technical decisions with rationale
- `docs/dev/ROADMAP.md` - Points to where work is tracked (OpsClear itself: PRJ-001 Maintenance, PRJ-002 Future Considerations, one project per phase)
- `CONTRIBUTING.md` - Commit message and PR details
- `docs/dev/process/` - **How work is done**: WORKFLOW (tracks, branches, commit scopes), JOBS, BACKEND, FRONTEND, E2E, AGENTS
- `docs/dev/decisions/` - Architecture Decision Records (ADRs)
- `docs/dev/process/DEV-GUIDE.md` - **Start here:** one-page guide to doing dev with the skills and agents
- `docs/dev/process/AGENTS.md` - Claude Code skills, agents, permissions, `scripts/opsclear` CLI and machine setup

## Domain Model (Core Entities)

- **Job** - Central entity (name, client, responsible person, deadline, status)
- **User** - Belongs to an organisation; project roles Owner, Admin and Member
- **Note** - Attached to jobs, immutable (audit trail)
- **Approval** - Request from employee to owner (approve/reject)

## Key Features (Priority Order)

1. Real-time job status tracking
2. Blockage tracking (who, why, since when)
3. Simple approvals workflow
4. Quick notes/agreements (immutable)
5. Dashboard (blocked, in progress, awaiting approval)

## Design Principles

- Simple over complex (no subtasks, Gantt charts, custom statuses)
- 3 clicks max to any action
- Mobile-friendly (future React Native app)
- Audit trail for decisions

## Git Conventions

Follow [docs/dev/process/WORKFLOW.md](docs/dev/process/WORKFLOW.md) (single source) and
[CONTRIBUTING.md](CONTRIBUTING.md):

- One OpsClear job = one branch = one PR. Never push directly to `main`.
- Branches: `feature/JOB-123-…`, `fix/JOB-123-…`, `e2e/JOB-123-…`, `chore/JOB-123-…`
- Commits: `<type>(<scope>): <summary> (JOB-123)` with `-` bullet body. Scope from the fixed list
  in WORKFLOW.md (`backend` `frontend` `db` `auth` `config` `ci` `docs` `deps` `infra` `adr`).
- No AI/tool attribution in commits or PRs: no `Co-Authored-By: Claude …` trailer, no
  "Generated with Claude Code" line.

## Commands

```bash
# Start local infra (postgres, keycloak, pgadmin, portainer); backend and frontend run on the host
docker-compose up

# Development (backend only, requires running postgres + keycloak)
cd backend && ./gradlew bootRun

# Run all tests
cd backend && ./gradlew test

# Run only unit tests (skip integration tests)
cd backend && ./gradlew test -PexcludeIntegrationTests

# Run only integration tests
cd backend && ./gradlew test -PonlyIntegrationTests

# Run a single test class
cd backend && ./gradlew test --tests "com.opsclear.service.ProjectServiceTest"

# Run a single test method
cd backend && ./gradlew test --tests "com.opsclear.service.ProjectServiceTest.create_shouldCreateProject_forExistingUser"
```

## Conventions

Technical conventions live in the process docs. Read the one for the layer you're changing:

- [BACKEND.md](docs/dev/process/BACKEND.md): layering, jOOQ patterns, Flyway, soft delete, feature
  gating, error handling, unit and integration tests (`ApiPaths`, `@DisplayName`), Checkstyle
- [FRONTEND.md](docs/dev/process/FRONTEND.md): structure, API layer, hooks, forms, add-on gating
- [E2E.md](docs/dev/process/E2E.md): when E2E coverage is required and how much
- [JOBS.md](docs/dev/process/JOBS.md): job naming and splitting

## Local Setup

Machine setup (OpsClear API key, `gh`, OrbStack Docker for Testcontainers, `JAVA_HOME` for
Homebrew `openjdk@21`) is in [docs/dev/process/AGENTS.md](docs/dev/process/AGENTS.md#setup).
For OpsClear API work use `scripts/opsclear` (same doc), not raw curl.

## Access Points (Local Dev)

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost:5173 | — |
| Backend API | http://localhost:8080 | — |
| Swagger UI | http://localhost:8080/swagger-ui.html | — |
| Keycloak Admin | http://localhost:8180/admin | admin/admin |
| Keycloak Auth | http://localhost:8180/realms/opsclear | — |
| pgAdmin | http://localhost:5050 | admin@admin.com/admin |
| Portainer | http://localhost:9000 | set on first run |

## Demo Users

All users share password `password123`. Run `./scripts/seed.sh` to populate the DB with realistic demo data.

| Email | Role in seed data |
|-------|-------------------|
| `testuser@example.com` | owner: Mobile App \| member: Infrastructure Upgrade |
| `alice@example.com` | owner: Website Redesign \| admin: Mobile App |
| `bob@example.com` | admin: Website Redesign \| owner: Infrastructure Upgrade |
| `carol@example.com` | member: Website Redesign, Mobile App |
| `dave@example.com` | member: Website Redesign, Infrastructure Upgrade |
