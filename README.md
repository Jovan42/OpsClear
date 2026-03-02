# OpsClear

**Operational clarity for small and medium businesses**

---

## Problem Statement

Small and medium enterprises (5-50 employees) face daily operational chaos:
- Constant phone calls asking "where is the job at?"
- Owner becomes a bottleneck for every decision
- Work gets stuck and nobody knows why
- Informal agreements are forgotten
- No real-time visibility into what's happening

Existing solutions are either:
- **Too complex** (ERP systems, Jira, ClickUp) - overwhelming for SMEs
- **Too simple** (Excel, Viber, WhatsApp) - no structure or traceability

---

## Solution

A simple operational tracking app that answers one question:

> **"What's the truth about our work TODAY?"**

Not planning. Not analytics. Just:
- What's in progress
- What's stuck
- What needs a decision

---

## Core Concept

Everything revolves around a **"Job"** (can be a project, order, request, or agreement).

A Job has minimal fields:
- Name
- Client (optional)
- Responsible person
- Deadline
- Status

---

## Status Model

Only 4 statuses (intentionally limited):

```
New → In Progress → Blocked → Completed
```

No custom statuses in MVP.

---

## Main Dashboard (Home Screen)

Three sections, visible at a glance:

| Section | Color | Content |
|---------|-------|---------|
| **Blocked** | Red | Jobs that are stuck (who, why, since when) |
| **In Progress** | Yellow | Active jobs (status, owner, deadline) |
| **Awaiting Approval** | Blue | Jobs needing owner decision (approve/reject) |

---

## 5 Core Features (Priority Order)

### 1. Real-time Job Status (CORE)
- Central entity: Job
- Track progress without complexity
- Everyone sees the same truth

### 2. Blockage Tracking
- Mark job as "Blocked"
- Capture: Who is blocking, Why, Since when
- Auto-highlight blocked jobs
- Send notifications

### 3. Simple Approvals
- Button: "Needs approval"
- Goes to owner
- Two actions: Approve / Reject
- Decision is logged

### 4. Quick Notes / Agreements
- Attach notes to any job
- Auto-capture: Who said it, When
- No editing (audit trail)
- Example: "Client agreed to move deadline to Friday"

### 5. Dashboard (built from data)
- One screen
- No filters
- Shows: What's late, What's blocked, What's waiting

---

## What's Intentionally NOT Included

- No tasks / subtasks
- No Gantt charts
- No complex reports
- No integrations (MVP)
- No CRM
- No invoicing
- No custom statuses
- No complex permissions

This is a conscious decision to avoid becoming another ERP.

---

## Target Users

### Primary (Must-have fit)

**1. SME Owners (5-50 employees)**
- Owner = director = operations
- Constantly interrupted with status questions
- Makes all decisions
- No time for complex software

**2. Operational Managers / Team Leads**
- Manage 5-20 people
- Responsible for delivery
- Need visibility into blockages
- Report to owner without clear data

### Secondary (Good fit)

- Service & project companies (construction, IT agencies, marketing, design)
- Small manufacturing & logistics
- B2B trade

### NOT Targeting (Initially)

- Freelancers / solo entrepreneurs
- Large enterprises
- Technical teams (they use Jira, Notion, etc.)

---

## Ideal First Customer

```
Company:        10-25 employees
Industry:       Services or manufacturing
Pain:           Owner tired of constant calls
Current tools:  Excel, Viber, phone calls
```

If we win this customer, we have product-market fit.

---

## User Experience Goals

- Owner opens app in the morning
- In 30 seconds knows:
  - What's on fire
  - Who's waiting
  - Why something is stuck
- **That's the win.**

---

## Technical Requirements (High-level)

- Web application
- Mobile-friendly (responsive)
- 3 clicks max to any action
- Fast loading
- Simple authentication

---

## User Roles

| Role | Access |
|------|--------|
| **Owner** | Dashboard, approvals, all jobs, escalations |
| **Employee** | Own jobs only, can block, can add notes |

No complex permission system in MVP.

---

## Notifications (Minimal)

Only for important events:
- Job blocked
- Waiting longer than X days
- Approval requested

NOT for every change.

---

## Success Metrics

- Fewer phone calls asking for status
- Faster identification of blocked work
- Documented decisions (no more "he said / she said")
- Owner can delegate without losing visibility

---

## Competitive Advantage

| Competitors | Problem |
|-------------|---------|
| Jira, Asana, ClickUp | Too complex for SMEs |
| ERP systems | Too expensive, too heavy |
| Excel + Viber | No structure, no traceability |

**OpsClear fills the gap**: Simple enough to adopt, structured enough to work.

---

## Name Rationale

**OpsClear** = Operations + Clarity

- Short and memorable
- Implies operational visibility
- Professional but not corporate
- Domain likely available

---

## Local Development

### Prerequisites

- Docker + Docker Compose
- Java 21 (for backend)
- Node.js 22 (for frontend)

### Start all services

```bash
docker-compose up
```

### Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| **Frontend** | http://localhost:5173 | — (start separately with `npm run dev`) |
| **Backend API** | http://localhost:8080 | — |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | — |
| **Keycloak Admin** | http://localhost:8180/admin | admin / admin |
| **Keycloak Auth** | http://localhost:8180/realms/opsclear | — |
| **pgAdmin** | http://localhost:5050 | admin@admin.com / admin |
| **Portainer** | http://localhost:9000 | set on first run |

### Test User

| Field | Value |
|-------|-------|
| Email | testuser@example.com |
| Password | password123 |

### Start backend (API only)

```bash
cd backend && ./gradlew bootRun
```

### Start frontend (dev server)

```bash
cd frontend && npm install && npm run dev
```

---

## Project Status

**Phase:** Active development — backend complete, frontend in progress
**Created:** 2026-02-03
