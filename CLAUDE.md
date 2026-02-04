# OpsClear - Claude Context

## Project Overview

OpsClear is an operational tracking app for small/medium businesses (5-50 employees). It answers: "What's the truth about our work TODAY?"

**Core concept:** Everything revolves around a "Job" with 4 statuses: New → In Progress → Blocked → Completed

**Target users:** SME owners and operational managers tired of constant status calls.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React + Vite + TypeScript |
| Backend | Java 21 + Spring Boot 3.x + Gradle |
| Database | PostgreSQL 16 + Flyway migrations |
| Auth | JWT (OAuth2 planned) |
| API Docs | SpringDoc OpenAPI |
| Testing | JUnit 5 + Testcontainers, Vitest, Cypress |
| Infra | Monorepo, Docker, VPS, GitHub Actions |

## Project Structure

```
OpsClear/
├── backend/                # Spring Boot API (Java 21 + Gradle)
│   ├── src/main/java/com/opsclear/
│   │   ├── config/         # Security, web config
│   │   ├── controller/     # REST controllers
│   │   ├── service/        # Business logic
│   │   ├── repository/     # JPA repositories
│   │   ├── entity/         # JPA entities
│   │   ├── dto/            # Request/response DTOs
│   │   ├── exception/      # Custom exceptions
│   │   └── security/       # JWT, auth
│   └── src/main/resources/
│       ├── db/migration/   # Flyway migrations
│       └── application.yml
├── frontend/               # React SPA (not yet scaffolded)
├── docs/                   # Docusaurus documentation
│   ├── docs/              # User-facing guides
│   └── dev/               # Developer documentation
├── docker-compose.yml      # Local dev environment
├── CLAUDE.md              # This file (Claude context)
├── TECHNICAL.md           # Technical decisions log
└── README.md              # Project overview
```

## Key Documents

- `README.md` - Product vision, features, target users
- `TECHNICAL.md` - All technical decisions with rationale
- `ROADMAP.md` - Phases, modules, and tasks
- `CONTRIBUTING.md` - **Commit messages, branching, coding standards**
- `docs/dev/decisions/` - Architecture Decision Records (ADRs)

## Domain Model (Core Entities)

- **Job** - Central entity (name, client, responsible person, deadline, status)
- **User** - Two roles: Owner (full access) and Employee (own jobs)
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

**See `CONTRIBUTING.md` for full details.**

Commit format:
```
<type>(<scope>): <short summary>

- <change 1>
- <change 2>
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

Branches: `feature/<name>`, `fix/<name>`, `docs/<name>`

## Commands

```bash
# Development
cd backend && ./gradlew bootRun
cd frontend && npm run dev

# Docker
docker-compose up

# Tests
cd backend && ./gradlew test
cd frontend && npm test
npx cypress run
```
