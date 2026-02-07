# OpsClear - Technical Decisions

**Status:** In Progress
**Last Updated:** 2026-02-03 (Added Documentation Strategy - Docusaurus)

---

## Confirmed Stack

| Layer | Choice | Notes |
|-------|--------|-------|
| **Frontend** | React + Vite + TypeScript | SPA calling Java API. Future path to React Native for mobile. |
| **Backend** | Java 21 + Spring Boot 3.x | LTS version, virtual threads available |
| **Build Tool** | Gradle | Faster builds, modern choice |
| **Database** | PostgreSQL 16 | Relational, robust, great JSON support |
| **DB Migrations** | Flyway | SQL-based, simple and predictable |
| **Authentication** | JWT | Stateless, mobile-ready. OAuth2 planned for future. |
| **API Docs** | SpringDoc OpenAPI | Auto-generated Swagger UI |
| **User Documentation** | Docusaurus | React-based, professional, versioning support |

---

## Infrastructure

| Component | Choice | Notes |
|-----------|--------|-------|
| **Repository** | Monorepo | Backend + Frontend in single repo |
| **Deployment** | VPS + Docker Compose | Simple, cheap, full control |
| **CI/CD** | GitHub Actions | Repo hosted on GitHub |
| **Containerization** | Docker | All services containerized |

---

## Testing Stack

| Layer | Choice | Notes |
|-------|--------|-------|
| **Backend Unit/Integration** | JUnit 5 + Testcontainers | Tests against real Postgres in containers |
| **Frontend Unit** | Vitest + React Testing Library | Fast, Vite-native |
| **E2E** | Cypress | Browser-based full flow testing |

---

## Database Patterns

### Soft Delete

Entities that contain significant user data use **soft delete** with a `deleted_at` timestamp:

```sql
deleted_at TIMESTAMP  -- NULL = active, timestamp = deleted
```

**Applies to:**
- `projects` - contains jobs, notes, approvals
- `jobs` - (future) contains notes, history
- Other entities with important user data

**Does NOT apply to:**
- `users` - synced from Keycloak, managed externally
- `project_members` - hard delete is fine (re-invite is easy)
- `notes` - immutable by design, never deleted

**Implementation:**
- Repository methods filter `WHERE deleted_at IS NULL` by default
- Add `@Query` annotation or use method naming: `findAllByDeletedAtIsNull()`
- Entity has `softDelete()` method that sets `deletedAt = Instant.now()`
- Partial index for performance: `CREATE INDEX ... WHERE deleted_at IS NULL`

---

## Docker Services (Planned)

```yaml
services:
  postgres:
    image: postgres:16

  backend:
    build: ./backend
    depends_on:
      - postgres

  frontend:
    build: ./frontend

  # Future additions:
  # - redis (caching/sessions if needed)
  # - mailhog (email testing)
```

---

## Project Structure (TBD)

```
OpsClear/
├── backend/                # Spring Boot app
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   ├── build.gradle
│   └── Dockerfile
├── frontend/               # React app
│   ├── src/
│   ├── package.json
│   └── Dockerfile
├── docs/                   # Docusaurus documentation site
│   ├── docs/              # Markdown documentation files
│   ├── blog/              # Release notes, updates
│   ├── static/            # Images, assets
│   ├── docusaurus.config.js
│   └── package.json
├── docker-compose.yml      # Local development
├── docker-compose.prod.yml # Production
├── .github/
│   └── workflows/          # GitHub Actions CI/CD
└── README.md
```

---

## Documentation Strategy

| Component | Tool | Purpose |
|-----------|------|---------|
| **User Documentation** | Docusaurus | Customer-facing guides, tutorials |
| **API Documentation** | SpringDoc OpenAPI | Auto-generated REST API docs |
| **Code Documentation** | JavaDoc + JSDoc | Inline code documentation |

### Docusaurus Setup

**Location:** `/docs/` in monorepo

**Structure:**
```
docs/
├── docs/                       # Documentation content
│   ├── intro.md               # What is OpsClear
│   ├── getting-started.md     # Quick start guide
│   ├── user-guide/
│   │   ├── jobs.md            # Managing jobs
│   │   ├── blocking.md        # Handling blockages
│   │   ├── approvals.md       # Approval workflow
│   │   └── dashboard.md       # Understanding dashboard
│   └── admin/
│       ├── user-management.md
│       └── settings.md
├── blog/                       # Release notes, updates
├── static/                     # Images, screenshots
│   └── img/
├── docusaurus.config.js        # Site configuration
└── package.json
```

**Features Enabled:**
- ✅ Search (built-in)
- ✅ Versioning (for future releases)
- ✅ Dark mode
- ✅ Mobile responsive
- ✅ Blog for release notes
- ✅ OpenAPI integration (future)

**Deployment:**
- Platform: Netlify (free tier)
- Domain: `docs.opsclear.com`
- Auto-deploy on push to `main`
- Build command: `cd docs && npm run build`
- Publish directory: `docs/build`

**Why Docusaurus:**
- React-based (matches frontend stack)
- Professional appearance (builds SME trust)
- Versioning support (v1.0, v2.0 docs)
- Can embed React components
- Zero ongoing costs
- Battle-tested (Meta, Stripe, Supabase)

---

## Authorization Model

### Three-Role Model (Project-Scoped)

| Role | Description |
|------|-------------|
| `OWNER` | Project creator. Full control. One per project. |
| `ADMIN` | Can manage members and project settings. |
| `MEMBER` | Can work on assigned jobs only. |

- Roles are **project-scoped** — a user can be OWNER in one project and MEMBER in another
- Permissions enforced at the **service layer** via `requireRole()` checks
- Owner is auto-added when project is created
- See [ADR-0005](docs/dev/decisions/0005-roles-and-permissions.md) for full permission matrix

---

## Future Considerations

- [ ] OAuth2 integration (Google, Microsoft login)
- [ ] React Native mobile app
- [ ] Redis for caching/sessions
- [ ] Email notifications (SMTP integration)
- [ ] Monitoring (Prometheus, Grafana)
