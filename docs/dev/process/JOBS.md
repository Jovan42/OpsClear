# Jobs

## Naming conventions

Job titles follow the pattern `<Layer>: <short description>`.

| Layer | Prefix | Example |
|-------|--------|---------|
| Architecture decision | `ADR:` | `ADR: org-level job templates` |
| Database migration | `DB:` | `DB: make project_id nullable, add org_id` |
| Backend | `Backend:` | `Backend: org-level template CRUD` |
| Frontend | `Frontend:` | `Frontend: org templates section in OrgSettingsPage` |
| E2E | `E2E:` | `E2E: cy.loginAs() custom command via Keycloak token grant` |
| Bug fix | `Bug:` | `Bug: 404 not returned for wrong-org template` |
| Chore | `Chore:` | `Chore: upgrade Spring Boot to 3.4.1` |
| Tech debt | `Tech debt:` | `Tech debt: replace raw jOOQ inserts with model builder` |
| Future Consideration | (free title) | `Org-level job templates` |

## Splitting rules

- DB, backend, and frontend must never be combined in the same job
- Split a backend job further if it covers more than one logical feature area or would produce a PR too large to review in one sitting
- Split a frontend job further if it covers multiple distinct screens or flows
- Tests are part of the backend job — never a separate job

## Dependencies

Express dependencies using BLOCKED_BY relationships in OpsClear:

- Backend jobs → blocked by DB migration
- Frontend jobs → blocked by the backend job(s) they depend on
- Within the same layer, later jobs can be blocked by earlier ones — `Backend 2` blocked by `Backend 1`, `Frontend 2` blocked by `Frontend 1`
- If a job depends on multiple jobs, block it by all of them

## Templates

Job templates are available in OpsClear for each standard job type. Use them when creating jobs to pre-fill the description structure — via the app dropdown or by referencing them when creating jobs via the API.
