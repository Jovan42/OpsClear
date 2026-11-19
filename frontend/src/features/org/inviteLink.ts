import type { PublicInviteResponse } from '../../types';

/**
 * JOB-399: the invite token, kept while the visitor signs up or logs in from the invite page.
 * Keycloak's redirect brings them back to /invite/:token, but email verification can finish
 * somewhere that redirect doesn't reach (a link opened in another browser lands on the app's
 * home page). localStorage, not sessionStorage, so a new tab sees it too.
 */
export const PENDING_INVITE_KEY = 'opsclear:pending-invite';

/** Long enough to register and verify the email, short enough that an old token doesn't
 *  pull the user back to an invite page days later. */
export const PENDING_INVITE_TTL_MS = 24 * 60 * 60 * 1000;

type KeyValueStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

export function savePendingInvite(token: string, storage: KeyValueStorage = localStorage, now = Date.now()) {
  storage.setItem(PENDING_INVITE_KEY, JSON.stringify({ token, savedAt: now }));
}

export function clearPendingInvite(storage: KeyValueStorage = localStorage) {
  storage.removeItem(PENDING_INVITE_KEY);
}

/**
 * The saved token, or null when nothing is saved, the value is unreadable or it's older than
 * the TTL. Only reads: safe in a useState initializer, which StrictMode calls twice. The
 * caller clears it (clearPendingInvite) once it has used it, so a token sends the user to the
 * invite page once; the page saves it again if they start another sign-in from there.
 */
export function readPendingInvite(storage: KeyValueStorage = localStorage, now = Date.now()): string | null {
  const raw = storage.getItem(PENDING_INVITE_KEY);
  if (raw === null) return null;
  try {
    const value = JSON.parse(raw) as { token?: unknown; savedAt?: unknown };
    if (typeof value.token !== 'string' || value.token === '') return null;
    if (typeof value.savedAt !== 'number' || now - value.savedAt > PENDING_INVITE_TTL_MS) return null;
    return value.token;
  } catch {
    return null;
  }
}

/** The backend compares the invited and signed-in addresses case-insensitively; so does the page. */
export function sameEmail(a: string | null | undefined, b: string | null | undefined): boolean {
  if (!a || !b) return false;
  return a.trim().toLowerCase() === b.trim().toLowerCase();
}

export type InviteView =
  | 'expired'
  | 'revoked'
  | 'accepted'
  | 'signIn'
  | 'wrongAccount'
  | 'join';

/** What the invite page shows for a loaded invite and the current sign-in. */
export function inviteView(
  invite: Pick<PublicInviteResponse, 'status' | 'email'>,
  authenticated: boolean,
  signedInEmail: string | null,
): InviteView {
  if (invite.status === 'EXPIRED') return 'expired';
  if (invite.status === 'REVOKED') return 'revoked';
  if (invite.status === 'ACCEPTED') return 'accepted';
  if (!authenticated) return 'signIn';
  return sameEmail(invite.email, signedInEmail) ? 'join' : 'wrongAccount';
}
