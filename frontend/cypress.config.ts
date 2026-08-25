import { defineConfig } from 'cypress';

export default defineConfig({
  // cypress-multi-reporters keeps the readable terminal 'spec' output locally while
  // also writing JUnit XML (JOB-206) — dorny/test-reporter (already used for
  // unit-tests/integration-tests) reads that format regardless of source language, so
  // E2E results show up as a PR check/annotation the same way backend test results do.
  reporter: 'cypress-multi-reporters',
  reporterOptions: {
    configFile: 'reporter-config.json',
  },
  e2e: {
    // Overridable via `CYPRESS_BASE_URL` so CI can point at the container-network
    // frontend address instead of localhost (ADR-0049 §"CI").
    baseUrl: process.env.CYPRESS_BASE_URL ?? 'http://localhost:5173',
    supportFile: 'cypress/support/e2e.ts',
    specPattern: 'cypress/e2e/**/*.cy.ts',
  },
});
