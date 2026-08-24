import { defineConfig } from 'cypress';

export default defineConfig({
  e2e: {
    // Overridable via `CYPRESS_BASE_URL` so CI can point at the container-network
    // frontend address instead of localhost (ADR-0049 §"CI").
    baseUrl: process.env.CYPRESS_BASE_URL ?? 'http://localhost:5173',
    supportFile: 'cypress/support/e2e.ts',
    specPattern: 'cypress/e2e/**/*.cy.ts',
  },
});
