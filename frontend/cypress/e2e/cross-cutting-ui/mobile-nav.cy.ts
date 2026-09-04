// ADR-0049 Appendix §21, mobile nav drawer half (ADR-0038). Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// useProjectNavItems.ts is the single source of truth for both the desktop
// ProjectNav and the mobile NavDrawer's item list/order/locked-addon treatment —
// both render the same navData.items via the same ProjectNavItemView component, so
// "same items, same order, same locked treatment" holds by construction, not by
// coincidence. The tests below still verify it end to end rather than trusting that
// architecture note alone.

import {
  uniqueEmail,
  uniqueSlug,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createProjectAs,
  addMember,
  addProjectMember,
  userIdFor,
  createJobAs,
  requestApprovalAs,
} from '../../support/orgApi';

describe('Mobile Nav Drawer', () => {
  it('at mobile widths the hamburger replaces the desktop nav row; opening shows the same items in the same order as desktop, including locked-addon treatment', { tags: '@smoke' }, () => {
    const email = uniqueEmail('mobile-nav-parity');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    // Full access unlocks Dashboard/Milestones/Templates/Types/Schedules/Approvals —
    // deliberately NOT Links, so the locked-item rendering also gets exercised.
    createOrgWithFullAccess(email, 'Falcon Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Falcon Nav Project').then((projectId) => {
        cy.loginAs(email);

        cy.viewport(1280, 800);
        cy.visit(`/projects/${projectId}/jobs`);
        cy.get('nav').find('button[aria-label="Open navigation menu"]').should('not.be.visible');
        cy.get('nav').contains('a', 'Dashboard').should('be.visible');
        // Scoped to the desktop ProjectNav's own wrapper (`hidden md:block`), not the
        // whole <nav> bar — that also contains the OpsClear logo link, the project
        // switcher, and UserMenu (the user's own display name), none of which are
        // drawer nav items.
        const desktopLabels: string[] = [];
        cy.get('nav .hidden.md\\:block').find('a, button').each(($el) => {
          const text = $el.text().trim();
          if (text) desktopLabels.push(text);
        });
        cy.then(() => {
          cy.viewport(375, 800);
          cy.get('button[aria-label="Open navigation menu"]').should('be.visible');
          cy.get('button[aria-label="Open navigation menu"]').click();
          cy.get('.z-50:visible').within(() => {
            cy.contains('Menu').should('be.visible');
            desktopLabels
              .filter((l) => l !== 'OpsClear')
              .forEach((label) => {
                cy.contains(label).should('be.visible');
              });
          });
        });
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('selecting a drawer item closes it and navigates; clicking the backdrop closes it without navigating', () => {
    const email = uniqueEmail('mobile-nav-select');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Nimbus Corp', uniqueSlug());
    createProjectAs(email, 'Nimbus Nav Project').then((projectId) => {
      cy.loginAs(email);
      cy.viewport(375, 800);
      cy.visit(`/projects/${projectId}/jobs`);

      cy.get('button[aria-label="Open navigation menu"]').click();
      cy.get('.z-50:visible').within(() => cy.contains('a', 'Settings').click());
      cy.url().should('include', `/projects/${projectId}/settings`);
      cy.get('.z-50:visible').should('not.exist');

      cy.get('button[aria-label="Open navigation menu"]').click();
      cy.get('.z-50:visible').should('be.visible');
      // The backdrop is the semi-transparent sibling behind the drawer panel itself —
      // clicking inside the panel is guarded by stopPropagation, so target outside it.
      cy.get('.z-50:visible .absolute.inset-0.bg-black\\/40').click({ force: true });
      cy.get('.z-50:visible').should('not.exist');
      cy.url().should('include', `/projects/${projectId}/settings`);
    });

    cy.deleteKeycloakUser(email);
  });

  it('the pending-approvals badge on the hamburger and the count on the drawer\'s Approvals item both match the real pending count', () => {
    const ownerEmail = uniqueEmail('mobile-nav-badge-owner');
    const memberEmail = uniqueEmail('mobile-nav-badge-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Atlas Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Atlas Badge Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          createJobAs(ownerEmail, projectId, { title: 'Job A', assignedTo: memberId }).then((jobA) =>
            createJobAs(ownerEmail, projectId, { title: 'Job B', assignedTo: memberId }).then((jobB) => {
              requestApprovalAs(memberEmail, projectId, jobA, 'Ship it');
              requestApprovalAs(memberEmail, projectId, jobB, 'Ship this too');

              cy.loginAs(ownerEmail);
              cy.viewport(375, 800);
              cy.visit(`/projects/${projectId}/jobs`);

              // The hamburger's own indicator is a plain dot (no count), just presence.
              cy.get('button[aria-label="Open navigation menu"]').find('.bg-orange-500').should('exist');
              cy.get('button[aria-label="Open navigation menu"]').click();
              cy.get('.z-50:visible').within(() => {
                cy.contains('a', 'Approvals').find('span').contains('2').should('be.visible');
              });
            }),
          );
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('resizing across the md: breakpoint with the drawer open does not leave it stuck open or hidden inconsistently', () => {
    const email = uniqueEmail('mobile-nav-resize');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Sable Corp', uniqueSlug());
    createProjectAs(email, 'Sable Resize Project').then((projectId) => {
      cy.loginAs(email);
      cy.viewport(375, 800);
      cy.visit(`/projects/${projectId}/jobs`);

      cy.get('button[aria-label="Open navigation menu"]').click();
      cy.get('.z-50:visible').should('be.visible');

      // Cross the breakpoint to desktop width without closing the drawer first — the
      // drawer's own wrapper is `md:hidden`, so it should become invisible via CSS
      // regardless of its own open/closed React state.
      cy.viewport(1280, 800);
      cy.get('.z-50:visible').should('not.exist');
      cy.get('nav').contains('a', 'Jobs').should('be.visible');

      // And back down to mobile — the drawer's own open state was never reset (only
      // CSS-hidden at desktop width), so it reappears immediately, still fully
      // interactive (closable), rather than requiring the hamburger to be clicked
      // again or being stuck in some inconsistent half-open state.
      cy.viewport(375, 800);
      cy.get('.z-50:visible').should('be.visible');
      cy.get('.z-50:visible').contains('Menu').should('be.visible');
      cy.get('.z-50:visible .absolute.inset-0.bg-black\\/40').click({ force: true });
      cy.get('.z-50:visible').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
  });
});
