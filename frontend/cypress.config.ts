import { defineConfig } from 'cypress';
import { Client } from 'pg';
import { plugin as cypressGrepPlugin } from '@cypress/grep/plugin';

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
    setupNodeEvents(on, config) {
      // JOB-258: lets `--env grepTags=@smoke` (or CYPRESS_grepTags=@smoke) narrow a
      // run to only tests tagged `{ tags: '@smoke' }`, without touching specPattern —
      // e2e-smoke passes this env var, e2e-full doesn't, so both jobs scan the same
      // spec files but run a different subset of the tests inside them.
      cypressGrepPlugin(config);

      // JOB-209: a handful of things a spec needs (an invite's raw token, backdating
      // an invite's expiry to test the 7-day window) have no API surface at all —
      // this is Node-side (unlike cy.request, which can't run arbitrary SQL), so it's
      // a task, not a command. Postgres is TCP-reachable on localhost:5432 in both
      // local dev (docker-compose) and CI (GitHub Actions service container) with the
      // same opsclear/opsclear credentials — same connection shape the backend's own
      // datasource config already uses in both places.
      on('task', {
        async queryDb({ sql, params }: { sql: string; params?: unknown[] }) {
          const client = new Client({
            host: 'localhost',
            port: 5432,
            user: 'opsclear',
            password: 'opsclear',
            database: 'opsclear',
          });
          await client.connect();
          try {
            const result = await client.query(sql, params);
            return result.rows;
          } finally {
            await client.end();
          }
        },
      });

      return config;
    },
  },
});
