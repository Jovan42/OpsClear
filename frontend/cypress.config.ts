import { defineConfig } from 'cypress';
import { Client } from 'pg';
import { plugin as cypressGrepPlugin } from '@cypress/grep/plugin';
import { readdirSync, readFileSync } from 'fs';
import { join } from 'path';

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
        // JOB-227: en/sr namespace-key parity is a static, build-time property, not a
        // runtime UI behavior — reading both locale directories directly via Node's fs
        // is far more direct and reliable than trying to prove "every key exists" by
        // driving the UI through every screen.
        readLocaleFiles() {
          // i18next CLDR pluralization suffixes (_zero/_one/_two/_few/_many/_other) —
          // Serbian has more grammatical plural categories than English (e.g. a
          // Slavic-specific _few form for counts like 2-4), so a key like
          // "missedRunsCount_few" existing only in sr is CORRECT, not a translation
          // gap. Suffixed keys are compared by their base name, not verbatim.
          const PLURAL_SUFFIX = /_(zero|one|two|few|many|other)$/;

          function flattenKeys(obj: Record<string, unknown>, prefix = ''): string[] {
            return Object.entries(obj).flatMap(([key, value]) => {
              const path = prefix ? `${prefix}.${key}` : key;
              return typeof value === 'object' && value !== null
                ? flattenKeys(value as Record<string, unknown>, path)
                : [path.replace(PLURAL_SUFFIX, '')];
            });
          }

          // __dirname isn't available here (this config file runs under Cypress's ESM
          // loader) — process.cwd() is always the frontend/ directory this config
          // lives in, since Cypress is always launched from there (same assumption
          // queryDb's own baseUrl config already relies on).
          const localesDir = join(process.cwd(), 'src/i18n/locales');
          const namespaces = readdirSync(join(localesDir, 'en')).filter((f) => f.endsWith('.json'));
          const result: Record<string, { en: string[]; sr: string[] }> = {};
          for (const file of namespaces) {
            const en = JSON.parse(readFileSync(join(localesDir, 'en', file), 'utf-8'));
            const sr = JSON.parse(readFileSync(join(localesDir, 'sr', file), 'utf-8'));
            result[file] = { en: flattenKeys(en), sr: flattenKeys(sr) };
          }
          return result;
        },
      });

      return config;
    },
  },
});
