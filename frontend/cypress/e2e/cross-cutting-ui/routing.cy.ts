// ADR-0049 Appendix §21, app shell / routing half.
//
// NOT covered (documented, not silently skipped): "a route-level render error is
// caught by RouteErrorPage rather than a blank white screen" — confirmed by reading
// router.tsx that every top-level route group (/, /features, /onboarding, /org/new,
// the bare invite route, and the main authenticated RequireAuth subtree) has its own
// `errorElement: <RouteErrorPage />`, so the boundary IS wired everywhere the ADR
// asks for. Forcing a genuine render-time throw to prove it catches, though, would
// need either a test-only error-injection hook (none exists in app source — adding
// one for a single edge-case test wasn't judged worth the app-source footprint,
// same call made for the analogous ErrorBoundary case in JOB-226's features-demos
// spec) or response-tampering via cy.intercept to find an undefended crash point,
// which would be fragile (breaks the moment defensive coding improves, which is a
// good thing, not something a test should punish) and isn't a substitute for a
// deliberate, stable throw. Static confirmation via source read is the coverage
// here; a real per-route throw test is out of practical E2E reach without one of
// those additions.

import { uniqueEmail, uniqueSlug, createOrgWithSubscription, createProjectAs } from '../../support/orgApi';

describe('App Shell — Unknown Route Redirect', () => {
  it('navigating to an unknown path under a project redirects to /projects, same no-hint-given pattern as unauthorized admin access', () => {
    const email = uniqueEmail('unknown-route');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Falcon Corp', uniqueSlug());
    createProjectAs(email, 'Falcon Routing Project').then((projectId) => {
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/this-route-does-not-exist`);
      cy.url().should('include', '/projects');
      cy.url().should('not.include', 'this-route-does-not-exist');
    });

    cy.deleteKeycloakUser(email);
  });
});
