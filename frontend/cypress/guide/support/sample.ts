// ADR-0062: the fixed sample data every guide screenshot is built from. Nothing here comes
// from the seed script or from a customer: the org, project, people and jobs are invented,
// so the images can be committed and published as they are.

import { uniqueEmail } from '../../support/orgApi';

export type GuideLang = 'en' | 'sr';

/** The language this run captures (`GUIDE_LANG=en|sr`, set by `npm run guide:screenshots`). */
export function guideLang(): GuideLang {
  const lang = Cypress.env('GUIDE_LANG') ?? 'en';
  if (lang !== 'en' && lang !== 'sr') throw new Error(`GUIDE_LANG must be en or sr, got "${lang}"`);
  return lang;
}

/** Every screenshot is taken at this desktop size. */
export const VIEWPORT = { width: 1280, height: 800 } as const;

/** `n` days from today, as `YYYY-MM-DD`. The backend stamps every row with the real time, so
 *  the app's clock is frozen at "now" (see `prepareCapture`) rather than at a made-up date, and
 *  deadlines are set relative to today: "due in 11 days", never overdue, whenever it runs. */
export function daysFromNow(n: number): string {
  return new Date(Date.now() + n * 86_400_000).toISOString().slice(0, 10);
}

export const ORG_NAME = 'Acme Plumbing';
export const PROJECT_NAME = 'Kitchen refit';

export const PEOPLE = {
  owner: { first: 'Priya', last: 'Nair' },
  teammate: { first: 'Marco', last: 'Rossi' },
  member: { first: 'Tom', last: 'Becker' },
  approver: { first: 'Lena', last: 'Fischer' },
} as const;

/** Job titles per guide page. Deadlines are a week or two from the day the spec runs. */
export const JOBS = {
  main: { title: 'Fit the kitchen sink', client: 'Hartley Family', deadline: daysFromNow(12) },
  done: { title: 'Replace the boiler valve', client: 'Okafor Bakery', deadline: daysFromNow(8) },
  blocked: { title: 'Install the dishwasher', client: 'Hartley Family', deadline: daysFromNow(10) },
  approval: { title: 'Move the gas pipe', client: 'Hartley Family', deadline: daysFromNow(15) },
} as const;

export const NOTE_TEXT = 'Parts delivered, starting on site tomorrow morning.';
export const BLOCK_REASON = 'Waiting for the supplier to deliver the dishwasher';
export const APPROVAL_TEXT = 'The new pipe route needs the owner to sign off before we cut into the wall.';

/** A disposable, guide-only user: `uniqueEmail('guide-<label>')`. */
export function guideEmail(label: string) {
  return uniqueEmail(`guide-${label}`);
}
