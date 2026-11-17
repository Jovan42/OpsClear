import type { InviteLanguage } from '../../types';

// The invite email goes out in the inviter's UI language (ADR-0059); the backend only knows en and sr
export function toInviteLanguage(resolvedLanguage: string | undefined): InviteLanguage {
  return resolvedLanguage?.startsWith('sr') ? 'sr' : 'en';
}

/**
 * Whole minutes left in the resend cooldown, rounded up, from a `Retry-After` header in
 * seconds. Null when the header is missing or isn't a number of seconds: the browser hides
 * it on a cross-origin response unless the backend's CORS config exposes it.
 */
export function retryAfterMinutes(retryAfter: unknown): number | null {
  if (typeof retryAfter !== 'string' && typeof retryAfter !== 'number') return null;
  if (retryAfter === '') return null;
  const seconds = Number(retryAfter);
  if (!Number.isFinite(seconds) || seconds <= 0) return null;
  return Math.ceil(seconds / 60);
}
