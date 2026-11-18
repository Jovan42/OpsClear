// ADR-0061 (JOB-351): server-synced UI preferences. Read and reset a user's stored `ui`
// through the API, and read the browser's per-user preferences cache.

import { API, tokenFor } from './orgApi';

/** The anonymous cache, and the pre-ADR-0061 (legacy) key every preference lived in. */
export const LEGACY_PREFERENCES_KEY = 'opsclear:preferences';
export const MIGRATED_BY_KEY = 'opsclear:preferences:migratedBy';

type Ui = Record<string, string | number | boolean | null>;

export function getPreferencesAs(email: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/user/preferences`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }: { body: { ui: Ui } }) => body.ui),
  );
}

/** Removes every stored UI preference (PATCH with nulls), so a user is back on the defaults. */
export function resetPreferencesAs(email: string) {
  return getPreferencesAs(email).then((ui) => {
    const keys = Object.keys(ui);
    if (keys.length === 0) return;
    tokenFor(email).then((token) =>
      cy.request({
        method: 'PATCH',
        url: `${API}/api/user/preferences`,
        headers: { Authorization: `Bearer ${token}` },
        body: { ui: Object.fromEntries(keys.map((key) => [key, null])) },
      }),
    );
  });
}

/** The logged-in user's cached preferences (`opsclear:preferences:<userId>`), or `{}`. */
export function cachedPreferences(win: Window): Record<string, unknown> {
  // Not the marker, and not `opsclear:preferences:<userId>:unsaved` (changes not saved yet).
  const key = Object.keys(win.localStorage).find(
    (k) => /^opsclear:preferences:[^:]+$/.test(k) && k !== MIGRATED_BY_KEY,
  );
  return key ? JSON.parse(win.localStorage.getItem(key) ?? '{}') : {};
}
