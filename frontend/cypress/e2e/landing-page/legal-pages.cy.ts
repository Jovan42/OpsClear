// JOB-420: the Terms, Privacy Policy and Refund policy pages are public and linked from the
// landing footer, /features and /guide.

const PAGES = [
  { path: '/terms', link: 'Terms of Service', heading: 'Terms of Service', testId: 'legal-terms' },
  { path: '/privacy', link: 'Privacy Policy', heading: 'Privacy Policy', testId: 'legal-privacy' },
  { path: '/refunds', link: 'Refund policy', heading: 'Refund and cancellation policy', testId: 'legal-refunds' },
];

describe('Legal pages', () => {
  PAGES.forEach(({ path, heading, testId }) => {
    it(`${path} opens logged out with a date, the draft banner and the binding-language note`, () => {
      cy.viewport(375, 700);
      cy.visit(path);
      cy.contains('h1', heading).should('be.visible');
      cy.get(`[data-testid=${testId}]`).should('contain', 'Last updated:');
      cy.get('[data-testid=legal-draft-banner]').should('be.visible');
      cy.get('[data-testid=legal-binding-note]').should('contain', 'English version');
      cy.document().then((doc) => {
        expect(doc.documentElement.scrollWidth).to.be.at.most(doc.documentElement.clientWidth);
      });
    });
  });

  it('the landing footer links to all three pages', () => {
    cy.visit('/');
    PAGES.forEach(({ path, link }) => {
      cy.get('footer [data-testid=legal-links]').contains('a', link).should('have.attr', 'href', path);
    });
    cy.get('footer [data-testid=legal-links]').contains('a', 'Terms of Service').click();
    cy.location('pathname').should('eq', '/terms');
  });

  it('/features and /guide link to the pages', () => {
    cy.visit('/features');
    cy.get('footer [data-testid=legal-links]').contains('a', 'Privacy Policy').should('have.attr', 'href', '/privacy');
    cy.visit('/guide');
    cy.get('footer [data-testid=legal-links]').contains('a', 'Refund policy').click();
    cy.location('pathname').should('eq', '/refunds');
  });

  it('shows the page in Serbian after switching language', () => {
    cy.visit('/terms');
    cy.get('nav').contains('button', 'EN').click();
    cy.contains('h1', 'Uslovi korišćenja').should('be.visible');
    cy.get('[data-testid=legal-binding-note]').should('contain', 'engleskom');
  });
});
