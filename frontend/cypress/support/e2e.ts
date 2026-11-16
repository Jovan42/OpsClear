import { register as registerCypressGrep } from '@cypress/grep';
// ADR-0057: collects window.__coverage__ after each spec; skips itself unless
// Cypress.expose('coverage') is true (set from COVERAGE=true in cypress.config.ts).
import '@cypress/code-coverage/support';
import './commands';

registerCypressGrep();
