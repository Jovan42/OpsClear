// sessionStorage keys cy.loginAs() (cypress/support/commands.ts) writes to and
// AuthContext reads from, to bypass the check-sso flow in E2E runs (JOB-204/
// ADR-0049 §3). Kept as named exports (not inlined) so the two sides can't drift.
export const E2E_TOKEN_KEY = 'e2e-keycloak-token';
export const E2E_REFRESH_TOKEN_KEY = 'e2e-keycloak-refresh-token';
export const E2E_ID_TOKEN_KEY = 'e2e-keycloak-id-token';
