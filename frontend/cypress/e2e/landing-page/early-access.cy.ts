// ADR-0066 (JOB-442): the public /early-access page and the landing Sign up buttons while
// sign-up is closed. The sign-up itself hits the real backend; the backend's own rules
// (validation, codes, duplicates, honeypot) are covered by EmailSignupIntegrationTest.
//
// CI runs with sign-up open (OPSCLEAR_SIGNUP_OPEN defaults to true), so the closed state is a
// stubbed GET /api/public/signup here. The backend side of "closed" (403 on org creation) is
// covered by SignupClosedIntegrationTest.

import { uniqueEmail } from '../../support/orgApi';

const STATUS_URL = '**/api/public/signup';
const SIGNUPS_URL = '**/api/public/signups';

function deleteSignup(email: string) {
  cy.task('queryDb', { sql: 'DELETE FROM email_signups WHERE lower(email) = lower($1)', params: [email] });
}

function consentBox() {
  return cy.contains('label', 'Email me when OpsClear launches').find('input[type="checkbox"]');
}

describe('Early access page (/early-access)', () => {
  it('signs up with the code and campaign from the link and shows the thank-you', { tags: '@smoke' }, () => {
    const email = uniqueEmail('early-access');
    cy.intercept('POST', SIGNUPS_URL).as('signUp');

    cy.visit('/early-access?code=trial&utm_source=linkedin&utm_medium=social&utm_campaign=launch');
    cy.contains('h1', 'Get notified at launch').should('be.visible');
    cy.contains('30 days free, no credit card needed.').should('be.visible');
    cy.get('#signup-code').should('have.value', 'trial');
    cy.contains('a', 'Privacy Policy').should('have.attr', 'href', '/privacy');

    cy.get('#signup-email').type(email);
    consentBox().check();
    cy.contains('button', 'Get notified').click();

    cy.wait('@signUp').then(({ request, response }) => {
      expect(response?.statusCode).to.eq(204);
      expect(request.body).to.deep.include({
        list: 'early-access',
        email,
        consent: true,
        locale: 'en',
        utmSource: 'linkedin',
        utmMedium: 'social',
        utmCampaign: 'launch',
        code: 'trial',
        website: '',
      });
    });
    cy.get('[data-testid="signup-thanks"]').should('contain.text', "Thanks! We'll email you when we launch.");
    cy.get('#signup-email').should('not.exist');

    cy.task('queryDb', {
      sql: 'SELECT code, utm_source FROM email_signups WHERE lower(email) = lower($1)',
      params: [email],
    }).should('deep.equal', [{ code: 'TRIAL', utm_source: 'linkedin' }]);
    deleteSignup(email);
  });

  it('shows "This code isn\'t valid" on the code field for an unknown code', () => {
    const email = uniqueEmail('early-access-code');
    cy.visit('/early-access?code=NOPE');
    cy.get('#signup-email').type(email);
    consentBox().check();
    cy.contains('button', 'Get notified').click();

    cy.get('[data-testid="signup-code-error"]').should('have.text', "This code isn't valid");
    cy.get('[data-testid="signup-thanks"]').should('not.exist');
    cy.get('[data-sonner-toast]').should('not.exist');
  });

  it('needs the consent box ticked and a valid email before sending anything', () => {
    cy.intercept('POST', SIGNUPS_URL, cy.spy().as('signUp'));
    cy.visit('/early-access');
    cy.get('#signup-email').type('not-an-email');
    cy.contains('button', 'Get notified').click();

    cy.contains('Enter a valid email address').should('be.visible');
    cy.contains('Tick the box so we can email you at launch').should('be.visible');
    cy.get('@signUp').should('not.have.been.called');
  });

  it('shows the rate-limit message on a 429', () => {
    cy.intercept('POST', SIGNUPS_URL, { statusCode: 429, body: { error: 'Too Many Requests' } });
    cy.visit('/early-access');
    cy.get('#signup-email').type(uniqueEmail('early-access-429'));
    consentBox().check();
    cy.contains('button', 'Get notified').click();

    cy.get('[role="alert"]').should('contain.text', 'Too many attempts. Wait a minute and try again.');
    cy.get('[data-testid="signup-thanks"]').should('not.exist');
  });

  it('works at phone width', () => {
    cy.viewport(375, 740);
    cy.visit('/early-access');
    cy.contains('h1', 'Get notified at launch').should('be.visible');
    cy.contains('button', 'Get notified').should('be.visible');
    cy.document().then((doc) => {
      expect(doc.documentElement.scrollWidth).to.be.at.most(doc.documentElement.clientWidth);
    });
  });
});

describe('Landing Sign up buttons by sign-up status', () => {
  it('with sign-up closed, both landing buttons read "Get notified" and go to /early-access', () => {
    cy.intercept('GET', STATUS_URL, { body: { open: false } }).as('status');
    cy.visit('/');
    cy.wait('@status');

    cy.contains('button', 'Get started').should('not.exist');
    cy.contains('button', 'Start now').should('not.exist');
    cy.get('[data-testid="get-notified-link"]').should('have.length', 2);
    cy.get('[data-testid="get-notified-link"]').first().should('have.text', 'Get notified').click();
    cy.location('pathname').should('eq', '/early-access');
    cy.contains('h1', 'Get notified at launch').should('be.visible');
  });

  it('with sign-up closed, the Features page button goes to /early-access', () => {
    cy.intercept('GET', STATUS_URL, { body: { open: false } }).as('status');
    cy.visit('/features');
    cy.wait('@status');

    cy.get('[data-testid="get-notified-link"]').should('have.text', 'Get notified').click();
    cy.location('pathname').should('eq', '/early-access');
  });

  it('with sign-up open, the buttons open registration as before', () => {
    cy.intercept('GET', STATUS_URL, { body: { open: true } }).as('status');
    cy.visit('/');
    cy.wait('@status');

    cy.contains('button', 'Get started').should('be.visible');
    cy.contains('button', 'Start now').should('be.visible');
    cy.get('[data-testid="get-notified-link"]').should('not.exist');
  });
});
