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
| Database | PostgreSQL 16 + Flyway migrations |
| Auth | Keycloak (OAuth2 Resource Server + JWT, stateless) |
| API Docs | SpringDoc OpenAPI |
| Testing | JUnit 5 + Mockito + Testcontainers, Vitest, Cypress |
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
│   │   ├── exception/      # Custom exceptions + GlobalExceptionHandler
│   │   └── security/       # JWT filter, UserSyncFilter, UserSyncService
│   └── src/main/resources/
│       ├── db/migration/   # Flyway migrations (V{n}__{description}.sql)
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

**Small, focused PRs** - one module can have multiple branches/PRs.

Branch naming: `feature/<phase>.<module>-<description>`
- `feature/1.1-auth-adr` - Auth ADR only
- `feature/1.1-auth-user-entity` - User entity + migration
- `feature/1.1-auth-jwt` - JWT service

Commit format:
```
<type>(<scope>): <short summary>

- <change 1>
- <change 2>
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

## Commands

```bash
# Start all services
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

## Backend Architecture Patterns

### Layering

**Controllers** extract the caller's identity from the JWT: `UUID.fromString(auth.getToken().getSubject())`. Return `ResponseEntity` with explicit status codes (201 for POST, 204 for DELETE). Apply `@Valid` on request DTOs.

**Services** annotate write methods `@Transactional`, reads `@Transactional(readOnly = true)`. Throw `NotFoundException` for missing records. Log with `@Slf4j`.

**Repositories** extend `JpaRepository<Entity, UUID>`. All active-record queries filter soft-deleted rows via method naming: `findByIdAndDeletedAtIsNull(...)`.

**DTOs** use a factory method for entity→response conversion: `ProjectResponse.from(Project entity)`. Request DTOs carry Jakarta validation annotations (`@NotBlank`, `@Size`).

### Soft Delete

Entities have a `deletedAt` timestamp. Active records are filtered `WHERE deleted_at IS NULL`. Services call `entity.softDelete()` instead of `repository.delete()`.

### User Sync

`UserSyncFilter` (runs after `BearerTokenAuthenticationFilter`) calls `UserSyncService.syncFromJwt(jwt)` on every authenticated request. It creates the user on first login or updates `lastLoginAt` on subsequent requests. The User UUID primary key matches Keycloak's `sub` claim.

Name extraction fallback chain: `name` claim → `given_name + family_name` → `preferred_username` → `email`.

### Error Responses

`GlobalExceptionHandler` (`@RestControllerAdvice`) returns consistent JSON:
```json
{ "error": "Error Type", "message": "Detailed message", "timestamp": "<ISO instant>" }
```
`NotFoundException` → 404, `MethodArgumentNotValidException` → 400.

### Security

`SecurityConfig` sets up an OAuth2 Resource Server with stateless JWT validation. Public endpoints: `/api/health`, `/api-docs/**`, `/swagger-ui/**`. Keycloak issuer URI defaults to `http://localhost:8180/realms/opsclear` and is overridable via `KEYCLOAK_ISSUER_URI`.

## Testing Patterns

**Unit tests** use `@ExtendWith(MockitoExtension.class)`, `@Mock` for dependencies, and manually instantiate the service in `@BeforeEach`. Use AssertJ (`assertThat`, `assertThatThrownBy`) and `ArgumentCaptor`. All test classes/methods use `@DisplayName`.

**Integration tests** live in `src/test/java/com/opsclear/integration/` and use:
- `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- `application-test.yml` activates Testcontainers PostgreSQL (Flyway disabled, Hibernate DDL `create-drop`)
- `@BeforeEach` cleans state via `repository.deleteAll()`
- MockMvc with mock JWT: `.with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("email", "...")))`
- Assertions on HTTP status + `jsonPath()` + database state after mutations
- **URL strings**: always use `ApiPaths` helper methods — never raw string concatenation in `perform()` calls. `ApiPaths` lives in the same package so no import is needed. Add a new method there whenever a new endpoint is introduced.

Test method naming convention: `methodName_shouldExpectedBehavior_whenCondition()`.

## Access Points (Local Dev)

| Service | URL | Credentials |
|---------|-----|-------------|
| Backend API | http://localhost:8080 | — |
| Swagger UI | http://localhost:8080/swagger-ui.html | — |
| Keycloak Admin | http://localhost:8180/admin | admin/admin |
| Keycloak Auth | http://localhost:8180/realms/opsclear | — |
| pgAdmin | http://localhost:5050 | admin@admin.com/admin |

## Test User

- Email: `testuser@example.com`
- Password: `password123`
