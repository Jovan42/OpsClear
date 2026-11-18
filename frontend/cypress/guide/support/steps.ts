// Steps every guide spec shares: logging in, clicking by label, the dialog, and showing an
// email on a page for a screenshot.

import { exact } from './i18n';

const MAILPIT = 'http://localhost:8025';

/** Logs in as `email` through the token endpoint and opens `path`. */
export function visitAs(email: string, path: string) {
  cy.loginAs(email);
  cy.visit(path);
}

export function clickButton(text: string, pick = 0) {
  cy.contains('button', exact(text, pick)).click();
}

export const dialog = () => cy.get('[role="dialog"]:visible');

export function clickInDialog(text: string, pick = 0) {
  dialog().contains('button', exact(text, pick)).click();
}

/** The sample org's slug is global: move an earlier run's org out of the way, so this one
 *  can be "ACM" again and every image shows the same job ids. Local databases only. */
export function freeSampleSlug() {
  cy.task('queryDb', {
    sql: "UPDATE organisations SET slug = 'R' || upper(substr(md5(random()::text), 1, 6)) WHERE slug = 'ACM'",
  });
}

/** Shows the newest email to `to` on the current page (its HTML, no inbox around it) and
 *  returns the first link containing `fragment`. `cy.mailpitLink` finds the link (and waits
 *  for the email); the page shows the same message with its links hidden. */
export function showEmail(to: string, fragment: string) {
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
