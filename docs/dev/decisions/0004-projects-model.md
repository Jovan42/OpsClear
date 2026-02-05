# ADR-0004: Projects Model and Multi-Tenancy

**Status:** Proposed
**Date:** 2026-02-05
**Author:** Jovan

---

## Context

OpsClear needs a way to organize work for different companies/teams. The core questions:

1. **What is a Project?** - The organizational boundary for jobs and team members
2. **Multi-tenancy** - How do we isolate data between different companies/teams?
3. **Access control** - How do users access projects they belong to?

### Requirements

- Users can create multiple projects (e.g., different departments, clients, or companies)
- Each project has its own jobs, members, and settings
- Users can belong to multiple projects
- Data must be isolated - users only see their projects' data
- Simple model for MVP (no complex hierarchies)

---

## Decision

### Project as the Tenant Boundary

A **Project** is the top-level organizational unit in OpsClear. All other entities (Jobs, Notes, Approvals) belong to a Project.

```
Project
├── Members (users with roles)
├── Jobs
│   ├── Notes
│   └── Approvals
└── Settings (future)
```

### Multi-Tenancy: Row-Level Isolation

Use **row-level isolation** with `project_id` as the tenant identifier.

```
┌─────────────────────────────────────────────────────────────┐
│                    Single Database                           │
├─────────────────────────────────────────────────────────────┤
│  projects        │  project_members  │  jobs                │
│  ────────        │  ───────────────  │  ────                │
│  id: uuid        │  project_id (FK)  │  project_id (FK)     │
│  name            │  user_id (FK)     │  title               │
│  owner_id (FK)   │  role             │  status              │
│                  │                   │  ...                 │
├─────────────────────────────────────────────────────────────┤
│  All queries filtered by project_id based on user access    │
└─────────────────────────────────────────────────────────────┘
```

**Why row-level isolation?**
- Simplest to implement and maintain
- Single database, single schema
- Easy to query across projects if needed (admin/analytics)
- Good performance for SME scale (thousands of jobs, not millions)

---

## Project Entity

### Database Schema

```sql
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT uk_projects_name_owner UNIQUE (name, owner_id)
);

CREATE INDEX idx_projects_owner ON projects(owner_id);
CREATE INDEX idx_projects_deleted ON projects(deleted_at) WHERE deleted_at IS NULL;
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `name` | VARCHAR(255) | Project name (e.g., "Acme Corp", "Marketing Team") |
| `description` | TEXT | Optional description |
| `owner_id` | UUID (FK) | User who created the project (always has OWNER role) |
| `created_at` | TIMESTAMP | Creation timestamp |
| `updated_at` | TIMESTAMP | Last update timestamp |
| `deleted_at` | TIMESTAMP | Soft delete timestamp (NULL = active) |

### Soft Delete

Projects use **soft delete** instead of hard delete:
- `deleted_at = NULL` means the project is active
- `deleted_at = timestamp` means the project is deleted
- All queries must filter `WHERE deleted_at IS NULL`
- Repository methods should use `@Query` or naming convention to enforce this

**Benefits:**
- Recover accidentally deleted projects
- Audit trail for compliance
- Can serve as "archive" feature later
- Related data (jobs, notes) remains intact

### Constraints

- Project name must be unique per owner (same user can't have two "Marketing" projects)
- Owner is automatically added as OWNER role in `project_members`

---

## Java Entity

```java
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
```

---

## API Endpoints

### Project CRUD

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/projects` | Create new project | Any authenticated user |
| GET | `/api/projects` | List user's projects | Returns only active projects user is member of |
| GET | `/api/projects/{id}` | Get project details | Project members only |
| PUT | `/api/projects/{id}` | Update project | OWNER, ADMIN |
| DELETE | `/api/projects/{id}` | Soft delete project | OWNER only |

### Request/Response DTOs

**Create Project Request:**
```json
{
  "name": "Acme Corp",
  "description": "Main operations tracking"
}
```

**Project Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Acme Corp",
  "description": "Main operations tracking",
  "owner": {
    "id": "...",
    "name": "John Doe",
    "email": "john@example.com"
  },
  "memberCount": 5,
  "createdAt": "2026-02-05T10:00:00Z",
  "updatedAt": "2026-02-05T10:00:00Z"
}
```

**List Projects Response:**
```json
{
  "projects": [
    {
      "id": "...",
      "name": "Acme Corp",
      "role": "OWNER",
      "memberCount": 5
    },
    {
      "id": "...",
      "name": "Marketing Team",
      "role": "MEMBER",
      "memberCount": 3
    }
  ]
}
```

---

## Access Control

### Project Access Rules

1. **Creating projects** - Any authenticated user can create a project
2. **Listing projects** - User sees only projects they're a member of
3. **Viewing project** - Must be a member of the project
4. **Updating project** - Must be OWNER or ADMIN
5. **Deleting project** - Must be OWNER

### Implementation

```java
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    public List<Project> getProjectsForUser(UUID userId) {
        return projectRepository.findByMemberId(userId);
    }

    public Project getProject(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new NotFoundException("Project not found"));

        if (!memberRepository.isMember(projectId, userId)) {
            throw new ForbiddenException("Not a member of this project");
        }

        return project;
    }
}
```

---

## Project Creation Flow

When a user creates a project:

1. Create the `projects` record
2. Automatically add creator as `project_members` with role `OWNER`

```java
@Transactional
public Project createProject(CreateProjectRequest request, UUID userId) {
    User owner = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found"));

    Project project = Project.builder()
        .name(request.getName())
        .description(request.getDescription())
        .owner(owner)
        .build();

    project = projectRepository.save(project);

    // Auto-add owner as member
    ProjectMember ownerMember = ProjectMember.builder()
        .project(project)
        .user(owner)
        .role(ProjectRole.OWNER)
        .build();

    memberRepository.save(ownerMember);

    return project;
}
```

---

## Alternatives Considered

### Alternative 1: Schema-per-tenant

Each company gets its own PostgreSQL schema.

**Pros:**
- Complete data isolation
- Easy to backup/restore per tenant

**Cons:**
- Complex deployment (schema management)
- Hard to query across tenants
- Connection pool per schema
- Overkill for MVP

**Why rejected:** Too complex for MVP. Row-level isolation is sufficient for SME scale.

### Alternative 2: Organization > Project hierarchy

```
Organization (Company)
└── Projects
    └── Jobs
```

**Pros:**
- Cleaner for large enterprises
- Billing per organization

**Cons:**
- Extra complexity
- MVP doesn't need it
- Can add later if needed

**Why rejected:** Over-engineering for MVP. A flat project model is simpler and can evolve later.

### Alternative 3: No projects (user-level isolation)

Each user has their own jobs, shared via explicit sharing.

**Pros:**
- Simpler initial model

**Cons:**
- Doesn't match SME workflow (teams work together)
- Hard to manage team access
- Doesn't scale to teams

**Why rejected:** Doesn't match the target use case of team collaboration.

---

## Consequences

### Positive

- **Simple model** - One concept (Project) for organization
- **Flexible** - Users can create multiple projects for different purposes
- **Standard pattern** - Row-level multi-tenancy is well understood
- **Easy to extend** - Can add Organization layer later if needed

### Negative

- **Query complexity** - All queries must include project_id filter
- **No cross-project features** - User can't see jobs across projects in one view
- **Manual enforcement** - Must remember to filter by project in all queries

### Neutral

- **Member management** - Need `project_members` table (Module 2.2)
- **Cascading deletes** - Deleting project deletes all jobs, notes, etc.

---

## Implementation Plan

1. Create Flyway migration for `projects` table
2. Implement `Project` entity
3. Implement `ProjectRepository` with custom query for user's projects
4. Implement `ProjectService` with access control
5. Create `ProjectController` with CRUD endpoints
6. Write integration tests
7. Update Postman collection

---

## References

- [ADR-0002: Authentication with Keycloak](./0002-authentication.md) - User model
- [Multi-tenant SaaS patterns](https://docs.microsoft.com/en-us/azure/architecture/guide/multitenant/overview)
