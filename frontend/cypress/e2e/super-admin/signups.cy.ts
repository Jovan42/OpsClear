// ADR-0066 (JOB-442): the super-admin Sign-ups tab. The list, CSV and delete endpoints, their
// CSV quoting and the 403 for a non-super user are covered by EmailSignupIntegrationTest; this
// file is the browser half.

import { API, createOrgAs, makeSuperUser, uniqueEmail, uniqueSlug } from '../../support/orgApi';

function signUp(email: string) {
  return cy.request({
    method: 'POST',
    url: `${API}/api/public/signups`,
    body: {
      list: 'early-access',
      email,
      consent: true,
      locale: 'sr',
      utmSource: 'linkedin',
      utmMedium: 'social',
      utmCampaign: 'e2e-launch',
      code: 'TRIAL',
      website: '',
    },
  });
}

describe('Super Admin Sign-ups tab', () => {
  it('lists a sign-up with its code and campaign, downloads the CSV and deletes the row', { tags: '@smoke' }, () => {
    const admin = uniqueEmail('signups-admin');
    const subscriber = uniqueEmail('signups-subscriber');
    cy.createKeycloakUser(admin, 'E2E', 'SuperAdmin');
    signUp(subscriber);
    makeSuperUser(admin).then(() => {
      cy.intercept('GET', '**/api/super-admin/signups/export*').as('export');
      cy.intercept('DELETE', '**/api/super-admin/signups/*').as('delete');
      cy.loginAs(admin);
      cy.visit('/admin');
      cy.contains('a', 'Sign-ups').click();
      cy.location('pathname').should('eq', '/admin/signups');
      cy.contains('h1', 'Sign-ups').should('be.visible');
      cy.get('#signup-list').should('have.value', 'early-access');
      cy.get('[data-testid="signups-count"]').should('contain.text', 'sign-up');

      cy.contains('[data-testid="signup-row"]', subscriber).within(() => {
        cy.contains('td', /^TRIAL$/).should('exist');
        cy.contains('td', /^sr$/).should('exist');
        cy.contains('td', /^linkedin$/).should('exist');
        cy.contains('td', /^social$/).should('exist');
        cy.contains('td', /^e2e-launch$/).should('exist');
      });

      cy.contains('button', 'Download CSV').click();
      cy.wait('@export').then(({ response }) => {
        expect(response?.statusCode).to.eq(200);
        expect(response?.headers['content-type']).to.include('text/csv');
      });

      cy.get(`button[aria-label="Delete ${subscriber}"]`).click();
      cy.get('[role="dialog"]').within(() => {
        cy.contains(`Remove ${subscriber} from the list?`).should('be.visible');
        cy.contains('button', 'Delete').click();
      });
      cy.wait('@delete').its('response.statusCode').should('eq', 204);
      cy.contains('[data-testid="signup-row"]', subscriber).should('not.exist');
      cy.task('queryDb', {
        sql: 'SELECT 1 FROM email_signups WHERE lower(email) = lower($1)',
        params: [subscriber],
      }).should('have.length', 0);
    });

    cy.deleteKeycloakUser(admin);
  });

  it('redirects a non-super user to /projects with no error toast', () => {
    const email = uniqueEmail('signups-not-super');
    cy.createKeycloakUser(email, 'E2E', 'Regular');
    createOrgAs(email, 'Regular Signups Org', uniqueSlug()).then(() => {
      cy.loginAs(email);
      cy.visit('/admin/signups');
      cy.location('pathname').should('eq', '/projects');
      cy.get('[data-sonner-toast]').should('not.exist');
    });
    cy.deleteKeycloakUser(email);
  });
});
