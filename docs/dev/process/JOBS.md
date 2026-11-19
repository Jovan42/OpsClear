# Jobs

## Naming conventions

Job titles follow `<Layer>: <short description>` (`ADR:`, `DB:`, `Backend:`, `Frontend:`, `E2E:`,
`Bug:`, `UX:`, `Chore:`, `Tech debt:`). Prefixes and examples: [NAMING.md](NAMING.md#job-titles).

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
