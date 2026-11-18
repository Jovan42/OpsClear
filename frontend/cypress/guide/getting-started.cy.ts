/* eslint-disable cypress/no-unnecessary-waiting -- a screenshot needs a short fixed settle time (fonts, animations, toasts) that no element state signals */
// ADR-0062 (JOB-357): screenshots for the Getting started guide pages (sign-up through
// job-approval), in the language given by GUIDE_LANG. Not a test of the app: it walks the
// onboarding journey once, with the sample data in support/sample.ts, and captures what
// each page shows. It lives outside cypress/e2e/ so CI's specPattern never runs it; run it
// with `npm run guide:screenshots` (see cypress/guide/README.md).
//
// One continuous journey, so the `it`s share state (testIsolation off) and run in order.
// A failing `it` usually means a selector or label moved: the guide page needs a look too.
// Images are named `<slug>-<n>` after the guide page they illustrate.

import {
  API,
  addMember,
  addProjectMember,
  createJobAs,
  makeOrgRealisticSubscription,
  tokenFor,
  updateJobStatusAs,
  userIdFor,
} from '../support/orgApi';
import { setPreferencesAs } from '../support/preferences';
import { prepareCapture, shot } from './support/capture';
import { exact, tr } from './support/i18n';
import {
  APPROVAL_TEXT,
  BLOCK_REASON,
  guideEmail,
  guideLang,
  JOBS,
  NOTE_TEXT,
  ORG_NAME,
  PEOPLE,
  PROJECT_NAME,
} from './support/sample';

const KEYCLOAK_ORIGIN = 'http://localhost:8180';
const MAILPIT = 'http://localhost:8025';
const lang = guideLang();

const owner = guideEmail('owner');
const teammate = guideEmail('teammate');
const member = guideEmail('member');

describe(`Guide screenshots: Getting started (${lang})`, { testIsolation: false }, () => {
  let orgId = '';
  let projectId = '';
  const jobIds: Record<string, string> = {};

  beforeEach(prepareCapture);

  /** Logs in as `email` through the token endpoint and opens `path`. */
  function visitAs(email: string, path: string) {
    cy.loginAs(email);
    cy.visit(path);
  }

  function clickButton(text: string, pick = 0) {
    cy.contains('button', exact(text, pick)).click();
  }

  const dialog = () => cy.get('[role="dialog"]:visible');

  function clickInDialog(text: string, pick = 0) {
    dialog().contains('button', exact(text, pick)).click();
  }

  /** The friendly id of the job called `title` in the sample project. */
  function jobIdOf(title: string) {
    return tokenFor(owner)
      .then((token) =>
        cy.request({ url: `${API}/api/projects/${projectId}/jobs`, headers: { Authorization: `Bearer ${token}` } }),
      )
      .then((res) => (res.body as Array<{ friendlyId: string; title: string }>).find((j) => j.title === title)!.friendlyId);
  }

  /** Creates `job` through the API, assigned to `assignee`, and remembers its id as `key`. */
  function seedJob(key: string, job: { title: string; client: string; deadline: string }, assignee: string) {
    return userIdFor(assignee).then((assignedTo) =>
      createJobAs(owner, projectId, { ...job, deadline: `${job.deadline}T00:00:00Z`, assignedTo, priority: 'MEDIUM' }).then((id) => {
        jobIds[key] = id;
      }),
    );
  }

  /** Shows the newest email to `to` on the current page (its HTML, no inbox around it)
   *  and returns the first link containing `fragment`. */
  function showEmail(to: string, fragment: string) {
    return cy.mailpitLink(to, fragment).then((link) => {
      cy.request(`${MAILPIT}/api/v1/search?query=to:${to}`).then(({ body }) => {
        cy.request(`${MAILPIT}/api/v1/message/${body.messages[0].ID}`).then(({ body: message }) => {
          cy.document().then((doc) => {
            doc.open();
            doc.write(message.HTML);
            doc.close();
            // Links carry one-time tokens: never show them in a published image.
            const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT);
            for (let node = walker.nextNode(); node; node = walker.nextNode()) {
              node.textContent = (node.textContent ?? '').replace(/https?:\/\/\S+/g, 'https://opsclear.example/…');
            }
            doc.querySelectorAll('a').forEach((a) => a.setAttribute('href', '#'));
            // Shrink the page to the email itself, so the image has no empty space around it
            doc.body.style.cssText += ';display:inline-block;padding:24px;margin:0';
          });
        });
      });
      return cy.wrap(link);
    });
  }

  before(() => {
    // The sample org's slug is global: move an earlier run's org out of the way, so this one
    // can be "ACM" again and every image shows the same job ids.
    cy.task('queryDb', {
      sql: "UPDATE organisations SET slug = 'R' || upper(substr(md5(random()::text), 1, 6)) WHERE slug = 'ACM'",
    });
    cy.createKeycloakUser(teammate, PEOPLE.teammate.first, PEOPLE.teammate.last);
    cy.createKeycloakUser(member, PEOPLE.member.first, PEOPLE.member.last);
  });

  after(() => {
    cy.deleteKeycloakUser(owner);
    cy.deleteKeycloakUser(teammate);
    cy.deleteKeycloakUser(member);
  });

  it('sign-up: the register page, verify-email page and the email', () => {
    // Keycloak picks its language from this cookie (shared by every port on localhost)
    if (lang === 'sr') cy.setCookie('KEYCLOAK_LOCALE', 'sr');
    cy.visit(
      `${KEYCLOAK_ORIGIN}/realms/opsclear/protocol/openid-connect/logout?client_id=opsclear-frontend&post_logout_redirect_uri=http://localhost:5173`,
    );
    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem('opsclear:preferences', JSON.stringify({ locale: lang, theme: 'light' }));
      },
    });
    cy.contains('button', /^(Log in|Prijava)$/).click();
    cy.origin(
      KEYCLOAK_ORIGIN,
      { args: { owner, first: PEOPLE.owner.first, last: PEOPLE.owner.last } },
      ({ owner, first, last }) => {
        cy.get('a[href*="registration"]').click();
        cy.get('#email').should('be.visible');
        cy.wait(500);
        cy.screenshot('sign-up-1', { overwrite: true, capture: 'viewport' });
        cy.get('#firstName').type(first);
        cy.get('#lastName').type(last);
        cy.get('#email').type(owner);
        cy.get('#password').type('password123');
        cy.get('#password-confirm').type('password123');
        cy.get('#password-confirm').blur();
        cy.screenshot('sign-up-2', { overwrite: true, capture: 'viewport' });
        cy.get('#kc-form-buttons').find('input[type=submit]').click();
        cy.get('#kc-verify-email-resend').should('be.visible');
        cy.wait(500);
        cy.screenshot('sign-up-3', { overwrite: true, capture: 'viewport' });
      },
    );

    // The email, shown on an app page; opening its link signs the new user in. One test, so
    // it never ends on Keycloak's page (cy.clock and cy.viewport can't reach it).
    cy.visit('/');
    showEmail(owner, '/login-actions/action-token').then((link) => {
      shot('sign-up', 4, 'body');
      // Opens the link as a click would: Keycloak verifies, then sends the user into the app.
      cy.window().then((win) => {
        win.location.href = link as unknown as string;
      });
    });
    cy.wait(5000);
    cy.location('pathname', { timeout: 15000 }).should('eq', '/onboarding');
  });

  it('create-organisation: the form and the new organisation', () => {
    // The owner's language and theme come from the server, not from the browser's storage
    cy.clearLocalStorage();
    cy.then(() => setPreferencesAs(owner, { locale: lang, theme: 'light' }));
    visitAs(owner, '/onboarding');
    cy.get('#org-name').type(ORG_NAME);
    cy.get('#org-slug').type('ACM');
    shot('create-organisation', 1);
    clickButton('Create organisation', 1);
    cy.location('pathname', { timeout: 10000 }).should('eq', '/org/settings');

    tokenFor(owner)
      .then((token) =>
        cy.request({ url: `${API}/api/organisations/mine`, headers: { Authorization: `Bearer ${token}` } }),
      )
      .then((res) => {
        orgId = res.body.id;
      });
    // A paid subscription with every add-on, set up directly: billing isn't what's shown here.
    cy.then(() => makeOrgRealisticSubscription(orgId));
    cy.visit('/org/settings');
    cy.get('input').first().should('have.value', ORG_NAME);
    shot('create-organisation', 2);

    // The rest of the sample team: every person's language follows the run's language.
    for (const email of [teammate, member]) {
      cy.then(() => userIdFor(email)).then(() => setPreferencesAs(email, { locale: lang, theme: 'light' }));
    }
    cy.then(() => userIdFor(member)).then((id) => addMember(orgId, owner, id, 'MEMBER'));
  });

  it('invite-team: invites a teammate, copies the link and accepts the invite', () => {
    visitAs(owner, '/org/invites');
    cy.get('input[type=email]').type(teammate);
    shot('invite-team', 1);
    clickButton('Send invite');
    cy.contains('td', teammate).should('be.visible');
    cy.window().then((win) => {
      cy.stub(win.navigator.clipboard, 'writeText').resolves();
    });
    cy.contains('tr', teammate).contains('button', exact('Copy link')).click();
    cy.get('[data-sonner-toast]').should('be.visible');
    shot('invite-team', 2);

    // What the teammate gets: the email, and the accept page its link opens
    showEmail(teammate, '/invite/').then((link) => {
      shot('invite-team', 3, 'body');
      visitAs(teammate, new URL(link as unknown as string).pathname);
    });
    cy.contains('button', exact('Accept invite')).should('be.visible');
    shot('invite-team', 4);
    clickButton('Accept invite');
    cy.contains(tr("You've joined the organisation"), { timeout: 10000 }).should('be.visible');
    shot('invite-team', 5);
  });

  it('first-project: creates a project and adds members', () => {
    visitAs(owner, '/projects');
    clickButton('+ New Project');
    dialog().should('be.visible');
    shot('first-project', 1);
    dialog().find('input').first().type(PROJECT_NAME);
    shot('first-project', 2, dialog());
    clickInDialog('Create project');
    cy.contains(PROJECT_NAME).should('be.visible');
    tokenFor(owner)
      .then((token) => cy.request({ url: `${API}/api/projects`, headers: { Authorization: `Bearer ${token}` } }))
      .then((res) => {
        projectId = (res.body as Array<{ friendlyId: string; name: string }>).find((p) => p.name === PROJECT_NAME)!
          .friendlyId;
      });

    cy.then(() => cy.visit(`/projects/${projectId}/settings`));
    cy.get(`input[placeholder="${tr('Search by email…', 1)}"]`).type(teammate);
    cy.contains('li button', teammate).should('be.visible');
    shot('first-project', 3);
    cy.contains('li button', teammate).click();
    clickButton('Add');
    cy.contains('tr', teammate).should('be.visible');
    cy.then(() => userIdFor(member)).then((id) => addProjectMember(projectId, owner, id, 'MEMBER'));
    cy.reload();
    cy.contains('tr', member).should('be.visible');
    shot('first-project', 4, cy.get('table').first());
  });

  it('create-job: creates a job and assigns it', () => {
    visitAs(owner, '/projects');
    cy.then(() => cy.visit(`/projects/${projectId}/jobs`));
    clickButton('+ New Job');
    dialog().should('be.visible');
    shot('create-job', 1);
    dialog().find(`input[placeholder="${tr('e.g. Fix login bug')}"]`).type(JOBS.main.title);
    dialog().find(`input[placeholder="${tr('Client name')}"]`).type(JOBS.main.client);
    dialog().find('input[type=date]').type(JOBS.main.deadline);
    dialog().find('select').last().select('HIGH', { force: true });
    dialog().find(`input[placeholder="${tr('Search member…')}"]`).type(PEOPLE.teammate.first, { force: true });
    cy.contains('li button', PEOPLE.teammate.first).click({ force: true });
    shot('create-job', 2);
    clickInDialog('Create job');
    cy.get('[role="dialog"]').should('not.exist');
    cy.contains('td', JOBS.main.title).should('be.visible');
    shot('create-job', 3);
    cy.contains('td', JOBS.main.title).click();
    cy.location('pathname').should('match', /\/jobs\/[^/]+$/);
    cy.contains(JOBS.main.title).should('be.visible');
    shot('create-job', 4);

    jobIdOf(JOBS.main.title).then((id) => {
      jobIds.main = id;
    });
    // The jobs the next pages work on
    cy.then(() => seedJob('done', JOBS.done, teammate));
    cy.then(() => seedJob('blocked', JOBS.blocked, member));
    cy.then(() => seedJob('approval', JOBS.approval, teammate));
  });

  it('job-done: starts a job, adds a note, completes it and checks the dashboard', () => {
    visitAs(teammate, `/projects/${projectId}/jobs/${jobIds.done}`);
    cy.contains('button', exact('Start')).should('be.visible');
    shot('job-done', 1);
    clickButton('Start');
    cy.contains('button', exact('Complete')).should('be.visible');
    shot('job-done', 2);
    cy.contains(tr('Notes')).click();
    cy.get(`textarea[placeholder="${tr('Add a note… (markdown supported)')}"]`).type(NOTE_TEXT);
    clickButton('Add Note');
    clickInDialog('Add Note');
    cy.contains(NOTE_TEXT).should('be.visible');
    shot('job-done', 3, undefined, { scroll: false });
    clickButton('Complete');
    clickInDialog('Mark Complete');
    cy.contains('button', exact('Complete')).should('not.exist');
    shot('job-done', 4);

    visitAs(owner, `/projects/${projectId}/dashboard`);
    cy.contains('button', tr('Completed')).find('span.text-2xl').should('have.text', '1');
    shot('job-done', 5, undefined, { settle: 2000 });
  });

  it('job-blocked: blocks a job with a reason, shows it on the dashboard and unblocks it', () => {
    cy.then(() => updateJobStatusAs(member, projectId, jobIds.blocked, 'IN_PROGRESS'));
    visitAs(member, `/projects/${projectId}/jobs/${jobIds.blocked}`);
    clickButton('Block');
    dialog().find(`input[placeholder="${tr('Select or type a reason…')}"]`).type(BLOCK_REASON);
    shot('job-blocked', 1);
    clickInDialog('Block Job');
    cy.contains(BLOCK_REASON).should('be.visible');
    shot('job-blocked', 2);

    visitAs(owner, `/projects/${projectId}/dashboard`);
    cy.contains(JOBS.blocked.title).should('be.visible');
    shot('job-blocked', 3, undefined, { settle: 2000 });

    visitAs(member, `/projects/${projectId}/jobs/${jobIds.blocked}`);
    clickButton('Unblock');
    dialog().should('be.visible');
    shot('job-blocked', 4);
    clickInDialog('Unblock');
    cy.contains('button', exact('Unblock')).should('not.exist');
  });

  it('job-approval: turns on the approval gate, requests approval and approves it', () => {
    cy.then(() => updateJobStatusAs(teammate, projectId, jobIds.approval, 'IN_PROGRESS'));
    visitAs(owner, `/projects/${projectId}/settings`);
    cy.get('[data-testid="completion-approval-toggle"]').click();
    cy.get('[data-testid="completion-approval-toggle"]').should('have.attr', 'aria-checked', 'true');
    shot('job-approval', 1, '[data-testid="completion-approval-setting"]');

    visitAs(teammate, `/projects/${projectId}/jobs/${jobIds.approval}`);
    clickButton('Request Approval');
    dialog().find('textarea').type(APPROVAL_TEXT);
    shot('job-approval', 2);
    clickInDialog('Submit');
    cy.get('[role="dialog"]').should('not.exist');
    cy.contains(tr('Approvals')).click();
    cy.contains(APPROVAL_TEXT).should('be.visible');
    shot('job-approval', 3);

    visitAs(owner, `/projects/${projectId}/approvals`);
    cy.contains(APPROVAL_TEXT).should('be.visible');
    shot('job-approval', 4);
    cy.contains(APPROVAL_TEXT).parent().contains('button', exact('Approve')).click();
    dialog().should('be.visible');
    shot('job-approval', 5);
    clickInDialog('Approve');
    cy.contains(APPROVAL_TEXT).should('not.exist');
  });
});
