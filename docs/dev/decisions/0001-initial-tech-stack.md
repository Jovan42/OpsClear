# ADR-0001: Initial Tech Stack

**Status:** Implemented
**Date:** 2026-02-03
**Author:** Jovan

## Context

OpsClear needs a tech stack that:
- Supports rapid development of a web application
- Is maintainable by a small team
- Can scale to mobile (React Native) in the future
- Uses mature, well-documented technologies
- Keeps infrastructure costs low for MVP

Target users are SME owners (5-50 employees) who need operational visibility.

## Decision

### Backend
- **Java 21 + Spring Boot 3.4.x** - LTS version, virtual threads, mature ecosystem
- **Gradle** - Faster builds than Maven, modern syntax
- **PostgreSQL 16** - Relational integrity, JSON support, proven reliability
- **Flyway** - SQL-based migrations, version controlled, predictable

### Frontend
- **React + Vite + TypeScript** - Fast DX, type safety, path to React Native

### Authentication
- **JWT tokens** - Stateless, mobile-ready, simple to implement

### API Documentation
- **SpringDoc OpenAPI** - Auto-generated from code, Swagger UI included

### Infrastructure
- **Monorepo** - Single source of truth, atomic commits
- **Docker Compose** - Local dev parity with production
- **VPS + Docker** - Simple, cheap, full control

### Testing
- **JUnit 5 + Testcontainers** - Automated integration tests (real Postgres)
- **Postman** - Manual API testing and documentation
- **Vitest** - Fast, Vite-native frontend testing
- **Cypress** - E2E browser testing

**Testing strategy:** JUnit for automated tests, Postman for manual testing/docs.

### Documentation
- **Docusaurus** - User-facing documentation (guides, tutorials, release notes)
- **SpringDoc OpenAPI** - API reference (auto-generated)
- **ADRs** - Internal technical decisions (this document)

**Docusaurus hosting:** Netlify free tier, auto-deploy on push to `main`

### Project Management
- **ROADMAP.md** - Phases, modules, and tasks in single file
- **GitHub Issues** - Bug reports and feature requests (when needed)

**Structure:** Phase → Module → Tasks (each module starts with DOC task for ADR)

**Why not Jira/Linear/etc:** Overhead not justified for small team. Upgrade later if needed.

## Alternatives Considered

### Alternative 1: Node.js + Express

**Pros:**
- Single language (TypeScript) frontend and backend
- Faster initial development

**Cons:**
- Less robust for complex business logic
- Weaker typing than Java
- Harder to maintain long-term

**Why rejected:** Java offers better long-term maintainability and Spring Boot's ecosystem is more comprehensive for enterprise features.

### Alternative 2: Python + Django/FastAPI

**Pros:**
- Rapid prototyping
- Good ORM (Django)

**Cons:**
- Performance concerns at scale
- Less familiar to team

**Why rejected:** Java is preferred for long-term maintainability and team expertise.

### Alternative 3: MySQL instead of PostgreSQL

**Pros:**
- Widely used, familiar

**Cons:**
- Weaker JSON support
- Less advanced features
- Licensing concerns (Oracle)

**Why rejected:** PostgreSQL is more feature-rich and has better JSON support for future flexibility.

### Alternative 4: GitBook instead of Docusaurus

**Pros:**
- Polished UI, no build step needed
- Easy collaboration

**Cons:**
- Free tier limited (1 user, public only)
- Paid plans expensive ($8+/user/month)
- Vendor lock-in, can't self-host

**Why rejected:** Cost at scale and vendor dependency.

### Alternative 5: MkDocs + Material Theme

**Pros:**
- Simple, fast builds
- Professional Material theme

**Cons:**
- Python dependency (not in our stack)
- No built-in blog for release notes
- Versioning requires plugins

**Why rejected:** Different tech stack, fewer built-in features than Docusaurus.

### Alternative 6: VitePress

**Pros:**
- Very fast (Vite-powered)
- Simple configuration

**Cons:**
- Vue-based (we use React)
- Smaller ecosystem, less mature

**Why rejected:** Vue doesn't match our React stack.

## Consequences

### Positive
- Mature ecosystem with extensive documentation
- Strong typing catches bugs at compile time
- Easy path to mobile via React Native
- Testcontainers ensures test/prod parity

### Negative
- Java is more verbose than alternatives
- Longer initial setup time
- Requires JVM knowledge

### Neutral
- Team needs to maintain Java and TypeScript skills
- Docker required for local development

## References

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [React Documentation](https://react.dev)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
