# E2E Testing (Cypress)

Stub — running instructions only. The full process doc (when E2E coverage is required on new
feature work, file/naming conventions, CI tier selection) lands in JOB-207.

---

## Running locally

Requires the frontend dev server (and, for anything beyond the trivial smoke spec, the full local
stack — Postgres, backend, Keycloak per `docker-compose up`) running first.

```bash
cd frontend
npm run dev          # in one terminal — leave running

npm run e2e:open      # interactive runner, in another terminal
npm run e2e:run       # headless, same as CI
```

`baseUrl` defaults to `http://localhost:5173` (`cypress.config.ts`) and is overridable via the
`CYPRESS_BASE_URL` env var for CI or a non-default dev server port.

## Structure

```
cypress/
  e2e/<feature-area>/<screen>.cy.ts   # one folder per ADR-0049 feature area
  support/
    e2e.ts        # loaded before every spec
    commands.ts   # custom commands (cy.loginAs(), JOB-204)
```

Feature-area folders match ADR-0049's Appendix breakdown 1:1 — see that ADR for the full test
catalog per area.
