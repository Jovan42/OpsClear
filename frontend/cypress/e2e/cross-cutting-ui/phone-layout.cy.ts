// JOB-416: layouts at phone width (375px). The job list toolbar, job cards, the job screen header
// and the account settings rows stack on a phone instead of squeezing text into a narrow column,
// and switch back to side by side from Tailwind's `sm:` breakpoint. Checks positions, not pixels,
// so wording and theme changes don't break it. Uses cy.loginAs() per docs/dev/process/E2E.md.

import {
  createJobAs,
  createJobTypeAs,
  createMilestoneAs,
  createOrgWithFullAccess,
  createProjectAs,
  uniqueEmail,
  uniqueSlug,
} from '../../support/orgApi';

const LONG_TITLE = 'Replace the cracked tiles in the ground floor hallway';

/** A user in a full-access org with one project: a milestone, a job type and a job using both. */
function userWithJob(label: string) {
  const email = uniqueEmail(label);
  cy.createKeycloakUser(email, 'E2E', 'Tester');
  return createOrgWithFullAccess(email, `${label} Corp`, uniqueSlug()).then(() =>
    createProjectAs(email, `${label} Project`).then((projectId) =>
      createMilestoneAs(email, projectId, 'First fix').then((milestoneId) =>
        createJobTypeAs(email, projectId, 'Tiling', 'BLUE').then((typeId) =>
          createJobAs(email, projectId, { title: LONG_TITLE, priority: 'HIGH', milestoneId, typeId }).then(
            (jobId) => ({ email, projectId, jobId }),
          ),
        ),
      ),
    ),
  );
}

function rectOf($el: JQuery<HTMLElement>): DOMRect {
  return $el[0].getBoundingClientRect();
}

function assertNoHorizontalScroll() {
  cy.document().then((doc) => {
    expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
  });
}

describe('Phone-width layouts', () => {
  it('stacks the job list toolbar and job cards at 375px', { tags: '@smoke' }, () => {
    userWithJob('phone-list').then(({ email, projectId }) => {
      cy.viewport(375, 812);
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);

      // Search fills its own row: about as wide as the page content (375 minus px-4 on each side).
      cy.get('input[type="search"]').should('be.visible').then(($search) => {
        expect(rectOf($search).width).to.be.greaterThan(330);
      });

      // Grouped and Everyone/Mine share one row, below the search box.
      cy.contains('button', 'Grouped').then(($grouped) => {
        cy.get('[data-testid="assignee-filter-everyone"]').then(($everyone) => {
          expect(Math.abs(rectOf($grouped).top - rectOf($everyone).top)).to.be.lessThan(8);
        });
        cy.get('input[type="search"]').then(($search) => {
          expect(rectOf($grouped).top).to.be.greaterThan(rectOf($search).bottom);
        });
      });

      // The job card's badges sit under its title, so the title gets the full card width.
      cy.contains('button', LONG_TITLE).within(() => {
        cy.contains('p', LONG_TITLE).then(($title) => {
          cy.contains('High').then(($badge) => {
            expect(rectOf($badge).top).to.be.at.least(rectOf($title).bottom);
          });
        });
      });

      assertNoHorizontalScroll();
      cy.deleteKeycloakUser(email);
    });
  });

  it('puts the job screen actions above the title at 375px and beside it on desktop', () => {
    userWithJob('phone-detail').then(({ email, projectId, jobId }) => {
      cy.loginAs(email);

      cy.viewport(375, 812);
      cy.visit(`/projects/${projectId}/jobs/${jobId}`);
      cy.contains('h1', LONG_TITLE).then(($h1) => {
        cy.contains('button', 'Edit').then(($edit) => {
          expect(rectOf($edit).bottom).to.be.at.most(rectOf($h1).top);
        });
      });
      assertNoHorizontalScroll();

      cy.viewport(1280, 800);
      cy.contains('h1', LONG_TITLE).then(($h1) => {
        cy.contains('button', 'Edit').then(($edit) => {
          expect(rectOf($edit).left).to.be.greaterThan(rectOf($h1).right);
        });
      });
      cy.deleteKeycloakUser(email);
    });
  });

  it('puts each settings control under its label at 375px and beside it on desktop', () => {
    const email = uniqueEmail('phone-settings');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Phone Settings Corp', uniqueSlug()).then(() => {
      cy.loginAs(email);

      cy.viewport(375, 812);
      cy.visit('/settings');
      // Stacked: the control starts on the label's left edge, below the description.
      cy.contains('p', 'Progress format').then(($label) => {
        const description = rectOf($label.next('p'));
        cy.contains('button', 'Fraction').then(($control) => {
          expect(Math.abs(rectOf($control).left - rectOf($label).left)).to.be.lessThan(2);
          expect(rectOf($control).top).to.be.at.least(description.bottom);
        });
      });
      assertNoHorizontalScroll();

      cy.viewport(1280, 800);
      cy.contains('p', 'Progress format').then(($label) => {
        cy.contains('button', 'Percentage').then(($control) => {
          expect(rectOf($control).left).to.be.greaterThan(rectOf($label).right);
        });
      });
    });
    cy.deleteKeycloakUser(email);
  });
});
