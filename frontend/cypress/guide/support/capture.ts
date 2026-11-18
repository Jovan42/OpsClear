/* eslint-disable cypress/no-unnecessary-waiting -- a screenshot needs a short fixed settle time (fonts, animations, toasts) that no element state signals */
// Screenshot helpers shared by the guide specs. A file is named `<slug>-<n>`, the same name
// the guide page uses (`/guide/<lang>/<slug>-<n>.webp` after the copy step).

import { VIEWPORT } from './sample';

/** Call from `beforeEach`: fixed viewport, and the app's `Date` frozen five minutes from now
 *  (rows made later in the same test then never read as "in the future").
 *  The backend stamps rows with the real time, so a made-up date would make every "3 days
 *  ago" and expiry read wrong; frozen at "now", they stay consistent and "just now" stays
 *  "just now". Only `Date` is faked: timers keep running, so debounces and toasts still work. */
export function prepareCapture() {
  cy.viewport(VIEWPORT.width, VIEWPORT.height);
  cy.clock(Date.now() + 5 * 60_000, ['Date']);
}

interface ShotOptions {
  /** Scroll to the top first (default), so a page is captured from its header down. */
  scroll?: boolean;
  /** Extra wait in ms for animations (charts) to finish. */
  settle?: number;
}

/** Screenshots `target` (a selector or an element chain), or the viewport when omitted. */
export function shot(
  slug: string,
  n: number,
  target?: string | Cypress.Chainable<JQuery<HTMLElement>>,
  { scroll = true, settle = 600 }: ShotOptions = {},
) {
  const name = `${slug}-${n}`;
  const options = { overwrite: true, padding: 12 } as const;
  if (!target && scroll) cy.scrollTo('top', { duration: 0, ensureScrollable: false });
  // Let layout settle (fonts, toasts sliding in, charts) before capturing.
  cy.wait(settle, { log: false });
  if (!target) {
    cy.screenshot(name, { ...options, capture: 'viewport' });
  } else if (typeof target === 'string') {
    cy.get(target).first().screenshot(name, options);
  } else {
    target.screenshot(name, options);
  }
}
