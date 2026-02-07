# ADR-0005: Roles and Permissions Model

**Status:** Proposed
**Date:** 2026-02-06
**Author:** Jovan

---

## Context

OpsClear projects need a way to control who can do what. The target users are SMEs (5-50 employees) where:

- The **owner** creates a project and has full control
- Some trusted employees need **admin** powers (manage members, edit project)
- Most employees are **members** who work on jobs within the project

We need a model that is:
- Simple enough for SMEs (no complex permission matrices)
- Flexible enough to cover all current features (projects, jobs, notes, approvals)
- Easy to enforce at the API level

### Requirements

1. Project creator is always the owner
2. Owners can delegate admin duties
3. Members can only interact with their assigned work
4. Permissions are project-scoped (not global)
5. A user can have different roles in different projects

---

## Decision

### Three-Role Model

| Role | Description |
|------|-------------|
| `OWNER` | Project creator. Full control. One per project. Can transfer ownership. |
| `ADMIN` | Trusted member. Can manage members and project settings. |
| `MEMBER` | Regular member. Can work on assigned jobs. |

### Permission Matrix

| Action | OWNER | ADMIN | MEMBER |
|--------|-------|-------|--------|
| View project | Yes | Yes | Yes |
| Update project | Yes | Yes | No |
| Delete project | Yes | No | No |
| Invite/remove members | Yes | Yes | No |
| Change member roles | Yes | Yes (not OWNER) | No |
| Create jobs | Yes | Yes | Yes |
| View all project jobs | Yes | Yes | No |
| View own assigned jobs | Yes | Yes | Yes |
| Update job status | Yes | Yes | Assigned only |
| Block/unblock jobs | Yes | Yes | No |
| Add notes | Yes | Yes | Yes (own jobs) |
| Request approval | Yes | Yes | Yes |
| Approve/reject | Yes | Yes | No |

### Database Schema

```sql
CREATE TABLE project_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_project_member UNIQUE (project_id, user_id),
    CONSTRAINT chk_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX idx_project_members_project ON project_members(project_id);
CREATE INDEX idx_project_members_user ON project_members(user_id);
```

### Java Enum

```java
public enum ProjectRole {
    OWNER,
    ADMIN,
    MEMBER
}
```

### Entity

```java
@Entity
@Table(name = "project_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = Instant.now();
    }
}
```

### Permission Enforcement

Permissions are checked at the **service layer**, not via Spring Security annotations, to keep the logic centralized and testable.

```java
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository memberRepository;

    public ProjectRole getRole(UUID projectId, UUID userId) {
        return memberRepository.findByProjectIdAndUserId(projectId, userId)
                .map(ProjectMember::getRole)
                .orElseThrow(() -> new ForbiddenException("Not a member of this project"));
    }

    public void requireRole(UUID projectId, UUID userId, ProjectRole... allowedRoles) {
        ProjectRole role = getRole(projectId, userId);
        if (!Set.of(allowedRoles).contains(role)) {
            throw new ForbiddenException("Insufficient permissions");
        }
    }
}
```

### API Endpoints

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/projects/{id}/members` | List project members | Any member |
| POST | `/api/projects/{id}/members` | Add member (invite) | OWNER, ADMIN |
| PUT | `/api/projects/{id}/members/{userId}` | Change member role | OWNER, ADMIN |
| DELETE | `/api/projects/{id}/members/{userId}` | Remove member | OWNER, ADMIN |
| POST | `/api/projects/{id}/transfer-ownership` | Transfer ownership to another member | OWNER only |

### Request/Response DTOs

**Add Member Request:**
```json
{
  "email": "employee@example.com",
  "role": "MEMBER"
}
```

**Member Response:**
```json
{
  "userId": "...",
  "name": "Jane Doe",
  "email": "jane@example.com",
  "role": "MEMBER",
  "joinedAt": "2026-02-06T10:00:00Z"
}
```

### Ownership Transfer

The owner can transfer ownership to any existing member of the project:

1. Target member is promoted to `OWNER`
2. Previous owner is demoted to `ADMIN`
3. The `projects.owner_id` is updated to the new owner
4. This is a single atomic transaction

```json
POST /api/projects/{id}/transfer-ownership
{
  "newOwnerId": "550e8400-e29b-41d4-a716-446655440000"
}
```

This handles the case where an owner leaves the company or wants to delegate full control.

### Business Rules

1. **Owner auto-add:** When a project is created, the creator is automatically added as `OWNER`
2. **Single owner:** Only one `OWNER` per project (enforced in service layer)
3. **Owner protection:** Owner cannot be removed or demoted (must transfer ownership first)
4. **Ownership transfer:** Owner can transfer ownership to any project member
5. **Self-removal:** Members and admins can leave a project voluntarily
6. **Invite by email:** Members are invited by email; they must have a Keycloak account (synced user)

---

## Alternatives Considered

### Alternative 1: Two-Role Model (Owner + Member)

**Pros:**
- Simpler
- Fewer edge cases

**Cons:**
- Owner becomes bottleneck for member management (the exact problem OpsClear solves)
- Can't delegate without giving full control

**Why rejected:** Defeats the purpose. The whole app is about reducing the owner bottleneck.

### Alternative 2: Fine-Grained Permissions (RBAC with custom permissions)

```
Permission: CAN_MANAGE_MEMBERS, CAN_EDIT_PROJECT, CAN_DELETE_JOB, ...
Role: custom grouping of permissions
```

**Pros:**
- Maximum flexibility
- Custom roles per project

**Cons:**
- Over-engineered for SMEs
- Complex UI to manage
- More tables, more queries

**Why rejected:** SMEs don't need this. Three roles cover all MVP use cases. Can evolve later if needed.

### Alternative 3: Global Roles (not project-scoped)

**Pros:**
- Simpler model (one role per user)

**Cons:**
- Can't have different roles in different projects
- Doesn't match the multi-tenant model

**Why rejected:** Contradicts the project-as-tenant design from ADR-0004.

---

## Consequences

### Positive

- **Simple and clear** — three roles, easy to explain to users
- **Reduces owner bottleneck** — admins can manage day-to-day
- **Project-scoped** — fits multi-tenant model
- **Easy to enforce** — single `requireRole()` check in services

### Negative

- **No custom roles** — if a customer needs "can manage jobs but not members", we can't do it yet
- **Service-level enforcement** — must remember to add checks to every service method

### Neutral

- **Impacts all future features** — Jobs, Notes, Approvals must all check project membership
- **Needs ForbiddenException** — new exception type for 403 responses

---

## Implementation Plan

1. Create Flyway migration for `project_members` table
2. Implement `ProjectRole` enum
3. Implement `ProjectMember` entity
4. Implement `ProjectMemberRepository`
5. Implement `ProjectMemberService` (invite, remove, change role, permission checks)
6. Create `ProjectMemberController`
7. Update `ProjectService` to auto-add owner on create
8. Add permission checks to existing project endpoints
9. Write integration tests
10. Update Postman collection

---

## References

- [ADR-0004: Projects Model and Multi-Tenancy](./0004-projects-model.md)
- [ADR-0002: Authentication with Keycloak](./0002-authentication.md)
