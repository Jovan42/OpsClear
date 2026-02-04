# ADR-0002: Authentication with Keycloak

**Status:** Proposed
**Date:** 2026-02-05
**Author:** Jovan

---

## Context

OpsClear needs user authentication that:
- Works for web app now, mobile app later
- Is stateless (no server-side sessions)
- Supports role-based access (Owner, Admin, Member per project)
- Handles common auth flows (register, login, password reset, email verification)
- Minimizes custom code and maintenance burden
- Can be self-hosted (no vendor lock-in)

### The Build vs Buy Question

Building authentication from scratch requires:
- User entity and repository
- Password hashing (BCrypt)
- JWT generation and validation
- Refresh token management
- Security filters
- Password reset flow
- Email verification
- Future: 2FA, social login

This is significant work and easy to get wrong (security implications).

---

## Decision

**Use Keycloak as the identity provider.**

Keycloak is an open-source Identity and Access Management (IAM) solution that handles all authentication concerns out of the box.

---

## What is Keycloak?

Keycloak is a standalone authentication server that:
- Manages users, credentials, roles, and groups
- Issues JWT/OAuth2 tokens
- Provides login/registration UI (customizable)
- Handles password reset, email verification, 2FA
- Supports social login (Google, Microsoft, GitHub)
- Has an admin console for user management
- Integrates natively with Spring Boot

**Backed by:** Red Hat (now part of IBM)
**License:** Apache 2.0 (fully open source)
**First release:** 2014 (mature, battle-tested)

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         User Browser                              │
└──────────────────────────────────────────────────────────────────┘
                │                              │
                │ 1. Login/Register            │ 4. API calls + JWT
                ▼                              ▼
┌─────────────────────────┐      ┌─────────────────────────────────┐
│       Keycloak          │      │      Spring Boot Backend        │
│  (Authentication)       │      │   (Resource Server)             │
│                         │      │                                 │
│  - Login UI             │      │  - Validates JWT signature      │
│  - User management      │      │  - Extracts user/roles          │
│  - JWT issuance         │      │  - Syncs user to local DB       │
│  - Password reset       │      │  - Business logic               │
│  - Social login         │      │  - Project/Job APIs             │
└─────────────────────────┘      └─────────────────────────────────┘
         │                                      │
         │ 2. JWT tokens                        │
         ▼                                      ▼
┌─────────────────────────┐      ┌─────────────────────────────────┐
│    React Frontend       │      │        PostgreSQL Database      │
│                         │      │  ┌───────────┐ ┌─────────────┐  │
│  - Redirect to Keycloak │      │  │ keycloak  │ │  opsclear   │  │
│  - Store tokens         │      │  │  schema   │ │   schema    │  │
│  - Attach to API calls  │      │  │ (users,   │ │ (projects,  │  │
└─────────────────────────┘      │  │  creds)   │ │ jobs, etc)  │  │
         3. Stores token         │  └───────────┘ └─────────────┘  │
                                 └─────────────────────────────────┘
```

### Authentication Flow

1. User clicks "Login" in React app
2. Redirect to Keycloak login page
3. User enters credentials (or registers)
4. Keycloak validates and issues JWT tokens
5. Redirect back to app with tokens
6. Frontend stores tokens, attaches to API calls
7. Backend validates JWT on each request
8. Backend syncs user info to local database

---

## User Data Strategy

### Two Sources of Truth

| Data | Source | Why |
|------|--------|-----|
| Credentials (password) | Keycloak | Security handled by experts |
| Email, Name | Keycloak (synced to our DB) | Keycloak is master, we cache |
| Custom fields (avatar, timezone, preferences) | Our DB | Keycloak doesn't have these |
| Project membership | Our DB | Application-specific |

### Users Table (Synced from Keycloak)

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,                    -- Same as Keycloak user ID (sub claim)
    email VARCHAR(255) NOT NULL,            -- Synced from Keycloak
    name VARCHAR(255) NOT NULL,             -- Synced from Keycloak
    avatar_url VARCHAR(500),                -- Custom field
    timezone VARCHAR(50) DEFAULT 'UTC',     -- Custom field
    preferences JSONB DEFAULT '{}',         -- Custom field (UI settings, etc.)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,

    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users(email);
```

### Sync on Login

When a JWT arrives, we upsert the user:

```java
@Service
public class UserSyncService {

    public User syncFromJwt(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        return userRepository.upsert(userId, email, name);
    }
}
```

**Why sync instead of just reference?**
- Can JOIN to get user names in queries
- Can add custom fields (avatar, preferences)
- Can list project members with their info
- Future-proof for app-specific user data

---

## Keycloak Concepts

| Concept | Description | Our Usage |
|---------|-------------|-----------|
| **Realm** | Isolated tenant in Keycloak | One realm: `opsclear` |
| **Client** | Application that uses Keycloak | Two: `opsclear-frontend`, `opsclear-backend` |
| **User** | A person who can log in | Our users |
| **Role** | Global permission | Not used (we use project_members) |
| **Group** | Collection of users | Not used initially |

### Realm Configuration

```
Realm: opsclear
├── Clients
│   ├── opsclear-frontend (public, PKCE)
│   └── opsclear-backend (bearer-only)
├── Realm Settings
│   ├── User registration: enabled
│   ├── Email verification: enabled (when SMTP configured)
│   └── Password policy: 8+ characters
└── Users
    └── (self-registration or admin-created)
```

---

## Project-Specific Roles

Keycloak handles **authentication** (who are you?).
Our database handles **authorization** (what can you do in this project?).

```sql
CREATE TABLE project_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_project_members UNIQUE (project_id, user_id)
);
```

### Role Permissions

| Role | Jobs | Members | Project |
|------|------|---------|---------|
| **OWNER** | All | Manage all | Delete |
| **ADMIN** | All | Invite/remove | Edit settings |
| **MEMBER** | Own only | View | View |

---

## Token Structure

Keycloak issues standard JWT tokens:

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "John Doe",
  "email_verified": true,
  "iat": 1707123456,
  "exp": 1707127056,
  "iss": "http://localhost:8180/realms/opsclear",
  "aud": "opsclear-backend"
}
```

### Token Lifetimes (Configurable in Keycloak)

| Token | Default | Purpose |
|-------|---------|---------|
| Access Token | 5 minutes | Short-lived, API calls |
| Refresh Token | 30 days | Get new access tokens |
| SSO Session | 10 hours | Browser session |

---

## Spring Boot Integration

### Dependencies

```gradle
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
    implementation 'org.springframework.boot:spring-boot-starter-security'
}
```

### Configuration

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/opsclear}
```

### Security Config

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
            );
        return http.build();
    }
}
```

### Getting User in Controllers

```java
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @GetMapping
    public List<ProjectDto> getMyProjects(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return projectService.getProjectsForUser(userId);
    }
}
```

---

## Docker Setup

### docker-compose.yml

```yaml
services:
  postgres:
    image: postgres:16
    container_name: opsclear-postgres
    environment:
      POSTGRES_USER: opsclear
      POSTGRES_PASSWORD: opsclear
      POSTGRES_DB: opsclear
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U opsclear"]
      interval: 5s
      timeout: 5s
      retries: 5

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    container_name: opsclear-keycloak
    command: start-dev
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/opsclear
      KC_DB_USERNAME: opsclear
      KC_DB_PASSWORD: opsclear
      KC_DB_SCHEMA: keycloak
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    ports:
      - "8180:8080"
    depends_on:
      postgres:
        condition: service_healthy

  backend:
    build: ./backend
    container_name: opsclear-backend
    environment:
      DB_HOST: postgres
      KEYCLOAK_ISSUER_URI: http://keycloak:8080/realms/opsclear
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - keycloak

volumes:
  postgres_data:
```

### Access Points

| Service | URL | Purpose |
|---------|-----|---------|
| Backend API | http://localhost:8080 | Our API |
| Keycloak Admin | http://localhost:8180/admin | User management |
| Keycloak Auth | http://localhost:8180/realms/opsclear | Auth endpoints |
| Swagger UI | http://localhost:8080/swagger-ui.html | API docs |

---

## What Keycloak Gives Us For Free

| Feature | Custom Build | With Keycloak |
|---------|--------------|---------------|
| User registration | Build form + API | Built-in UI |
| Login | Build form + API | Built-in UI |
| Password hashing | BCrypt setup | Handled |
| JWT generation | JJWT library | Handled |
| JWT validation | Security filter | Spring auto-config |
| Refresh tokens | Build logic | Handled |
| Password reset | Email + tokens + UI | Built-in flow |
| Email verification | Build it | Built-in flow |
| Account management | Build UI | Built-in UI |
| Brute force protection | Build it | Built-in |
| Session management | Build it | Built-in |
| Social login | Build each provider | Config only |
| 2FA/MFA | Complex build | Config only |
| Admin user management | Build UI | Admin console |

---

## Alternatives Considered

### Alternative 1: Build Custom Auth

**Pros:**
- Full control
- No external dependency
- Simpler initial architecture

**Cons:**
- Significant development time (weeks)
- Security risk if done wrong
- Must build: password reset, email verification, 2FA
- Ongoing maintenance burden

**Why rejected:** Too much work for MVP, security risk, Keycloak does it better.

### Alternative 2: Auth0 (Managed Service)

**Pros:**
- Zero maintenance
- 7,000 users free
- Quick setup

**Cons:**
- Vendor lock-in
- Data stored off-premises
- Costs at scale ($23/1000 users/month)
- Less control

**Why rejected:** Self-hosted requirement, want full data control.

### Alternative 3: Ory Kratos

**Pros:**
- Modern, lightweight
- API-first design
- Cloud-native (Go-based)

**Cons:**
- Less mature than Keycloak
- Smaller community
- Less Spring Boot documentation
- Need separate Ory Keto for authorization

**Why rejected:** Keycloak has better Spring Boot integration and larger community.

---

## Consequences

### Positive

- **No auth code to write** - focus on business logic
- **Battle-tested security** - 10+ years of hardening
- **Future-proof** - 2FA, social login ready when needed
- **Admin UI** - manage users without building UI
- **Mobile-ready** - OAuth2/OIDC works for native apps
- **Standards-based** - OAuth2, OIDC, JWT

### Negative

- **Additional service** - one more container to run
- **Learning curve** - Keycloak concepts and configuration
- **Heavier** - ~500MB Docker image
- **Login redirect** - users briefly leave app for login

### Neutral

- **Customization** - login pages can be themed to match app
- **Database** - Keycloak uses same Postgres (separate schema)
- **User sync** - need to sync user info on login (simple)

---

## Implementation Plan

### Phase 1.1 Tasks (Updated)

1. Update docker-compose with Keycloak
2. Configure Keycloak realm and clients
3. Update Spring Security for OAuth2 resource server
4. Create Flyway migration for users table
5. Implement User entity and UserSyncService
6. Test with Postman (get token from Keycloak, call API)

---

## References

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Keycloak Docker Image](https://quay.io/repository/keycloak/keycloak)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Keycloak Realm Export/Import](https://www.keycloak.org/server/importExport)
