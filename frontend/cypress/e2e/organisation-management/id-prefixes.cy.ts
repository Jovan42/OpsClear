// ADR-0060 (JOB-349): the ID prefixes section on the org settings page. Every test uses its
// own throwaway org (cy.createKeycloakUser + createOrgWithSubscription, like settings.cy.ts),
// so no prefix change touches the shared seed org and nothing needs restoring afterwards.

import {
  API,
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  userIdFor,
  createOrgWithSubscription,
  addMember,
  createProjectAs,
  createJobAs,
} from '../../support/orgApi';

function prefixesSection() {
  return cy.contains('h2', 'ID prefixes').parents('section');
}

function patchPrefixesAs(email: string, orgId: string, body: Record<string, string>) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'PATCH',
      url: `${API}/api/organisations/${orgId}/settings`,
      headers: { Authorization: `Bearer ${token}` },
      body,
    }),
  );
}

describe('Organisation Management — ID prefixes', () => {
  it('OWNER changes the job prefix: new jobs get it, old IDs still open', { tags: '@smoke' }, () => {
    const email = uniqueEmail('prefix-owner');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithSubscription(email, 'Prefix Corp', uniqueSlug());
    createProjectAs(email, 'Prefix Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Old prefix job' }).then((oldJobId) => {
        expect(oldJobId).to.eq('JOB-001');

        cy.loginAs(email);
        cy.visit('/org/settings');
        prefixesSection().within(() => {
          cy.get('#prefix-job').should('have.value', 'JOB');
          cy.contains('Next job: JOB-002').should('be.visible');
          cy.get('#prefix-job').clear();
          cy.get('#prefix-job').type('tsk');
          cy.get('#prefix-job').should('have.value', 'TSK');
          cy.contains('Next job: TSK-002').should('be.visible');
          cy.contains('button', 'Save changes').click();
        });

        cy.get('[role="dialog"]').within(() => {
          cy.contains('Existing IDs like JOB-042 keep their current prefix').should('be.visible');
          cy.contains('button', 'Change prefixes').click();
        });
        cy.get('[role="dialog"]').should('not.exist');
        prefixesSection().within(() => {
          cy.get('#prefix-job').should('have.value', 'TSK');
          cy.contains('button', 'Save changes').should('be.disabled');
        });

        createJobAs(email, projectId, { title: 'New prefix job' }).then((newJobId) => {
          expect(newJobId).to.eq('TSK-002');
          cy.visit(`/projects/${projectId}/jobs/${newJobId}`);
          cy.contains('New prefix job').should('be.visible');
        });

        cy.visit(`/projects/${projectId}/jobs/${oldJobId}`);
        cy.contains('Old prefix job').should('be.visible');
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('Cancel in the confirmation dialog keeps the edits and saves nothing', () => {
    const email = uniqueEmail('prefix-cancel');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithSubscription(email, 'Cancel Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/org/settings');

    prefixesSection().within(() => {
      cy.get('#prefix-milestone').clear();
      cy.get('#prefix-milestone').type('PHS');
      cy.contains('button', 'Save changes').click();
    });
    cy.get('[role="dialog"]').within(() => cy.contains('button', 'Cancel').click());
    cy.get('[role="dialog"]').should('not.exist');
    prefixesSection().within(() => cy.get('#prefix-milestone').should('have.value', 'PHS'));

    cy.reload();
    prefixesSection().within(() => cy.get('#prefix-milestone').should('have.value', 'MIL'));

    cy.deleteKeycloakUser(email);
  });

  it('a duplicate prefix shows an inline error and does not open the dialog', () => {
    const email = uniqueEmail('prefix-dup');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithSubscription(email, 'Dup Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/org/settings');

    prefixesSection().within(() => {
      cy.get('#prefix-milestone').clear();
      cy.get('#prefix-milestone').type('JOB');
      cy.contains('Each prefix must be different').should('be.visible');
      cy.contains('button', 'Save changes').click();
    });
    cy.get('[role="dialog"]').should('not.exist');

    cy.deleteKeycloakUser(email);
  });

  it("a prefix another type's records already use shows the backend message on that field", () => {
    const email = uniqueEmail('prefix-used');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithSubscription(email, 'Used Corp', uniqueSlug()).then((orgId) => {
      createProjectAs(email, 'Used Project').then((projectId) => {
        // JOB-001 exists, then jobs move to TSK, so JOB is free among the current four
        // prefixes but still carried by an existing job
        createJobAs(email, projectId, { title: 'Carries JOB' });
        patchPrefixesAs(email, orgId, { jobPrefix: 'TSK' });
      });
    });

    cy.loginAs(email);
    cy.visit('/org/settings');
    prefixesSection().within(() => {
      cy.get('#prefix-job').should('have.value', 'TSK');
      cy.get('#prefix-milestone').clear();
      cy.get('#prefix-milestone').type('JOB');
      cy.contains('button', 'Save changes').click();
    });
    cy.get('[role="dialog"]').within(() => cy.contains('button', 'Change prefixes').click());

    cy.get('[role="dialog"]').should('not.exist');
    cy.get('#prefix-milestone')
      .parent()
      .should('contain.text', 'Prefix "JOB" is already used by existing jobs');
    cy.get('#prefix-milestone').should('have.value', 'JOB');

    cy.deleteKeycloakUser(email);
  });

  it('a non-owner (ADMIN) sees the prefixes read-only with no Save', () => {
    const ownerEmail = uniqueEmail('prefix-ro-owner');
    const adminEmail = uniqueEmail('prefix-ro-admin');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Admin');
    createOrgWithSubscription(ownerEmail, 'RO Prefix Corp', uniqueSlug()).then((orgId) => {
      userIdFor(adminEmail).then((userId) => addMember(orgId, ownerEmail, userId, 'ADMIN'));
    });

    cy.loginAs(adminEmail);
    cy.visit('/org/settings');
    prefixesSection().within(() => {
      cy.get('#prefix-project').should('be.disabled').and('have.value', 'PRJ');
      cy.get('#prefix-job').should('be.disabled').and('have.value', 'JOB');
      cy.get('#prefix-milestone').should('be.disabled').and('have.value', 'MIL');
      cy.get('#prefix-template').should('be.disabled').and('have.value', 'TPL');
      cy.contains('Next job: JOB-').should('be.visible');
      cy.contains('button', 'Save changes').should('not.exist');
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(adminEmail);
  });
});
