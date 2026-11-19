import { describe, expect, it } from 'vitest';
import {
  PENDING_INVITE_KEY,
  PENDING_INVITE_TTL_MS,
  clearPendingInvite,
  inviteView,
  readPendingInvite,
  sameEmail,
  savePendingInvite,
} from './inviteLink';

function memoryStorage() {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => void values.set(key, value),
    removeItem: (key: string) => void values.delete(key),
    has: (key: string) => values.has(key),
  };
}

describe('pending invite token', () => {
  it('reads the saved token without removing it, until it is cleared', () => {
    const storage = memoryStorage();
    savePendingInvite('abc', storage, 1_000);
    expect(readPendingInvite(storage, 2_000)).toBe('abc');
    expect(readPendingInvite(storage, 2_000)).toBe('abc');
    clearPendingInvite(storage);
    expect(storage.has(PENDING_INVITE_KEY)).toBe(false);
    expect(readPendingInvite(storage, 2_000)).toBeNull();
  });

  it('ignores a token older than the TTL', () => {
    const storage = memoryStorage();
    savePendingInvite('abc', storage, 0);
    expect(readPendingInvite(storage, PENDING_INVITE_TTL_MS + 1)).toBeNull();
  });

  it('keeps a token right at the TTL', () => {
    const storage = memoryStorage();
    savePendingInvite('abc', storage, 0);
    expect(readPendingInvite(storage, PENDING_INVITE_TTL_MS)).toBe('abc');
  });

  it('ignores an unreadable value', () => {
    const storage = memoryStorage();
    storage.setItem(PENDING_INVITE_KEY, 'not json');
    expect(readPendingInvite(storage)).toBeNull();
    storage.setItem(PENDING_INVITE_KEY, JSON.stringify({ token: '', savedAt: Date.now() }));
    expect(readPendingInvite(storage)).toBeNull();
    storage.setItem(PENDING_INVITE_KEY, JSON.stringify({ token: 'abc' }));
    expect(readPendingInvite(storage)).toBeNull();
  });
});

describe('sameEmail', () => {
  it('ignores case and surrounding spaces', () => {
    expect(sameEmail('Nikola@Example.com', 'nikola@example.com')).toBe(true);
    expect(sameEmail(' nikola@example.com ', 'nikola@example.com')).toBe(true);
  });

  it('is false for different or missing addresses', () => {
    expect(sameEmail('nikola@example.com', 'ivana@example.com')).toBe(false);
    expect(sameEmail('nikola@example.com', null)).toBe(false);
    expect(sameEmail(undefined, 'nikola@example.com')).toBe(false);
  });
});

describe('inviteView', () => {
  const pending = { status: 'PENDING' as const, email: 'Nikola@example.com' };

  it('shows a closed invite whoever is signed in', () => {
    expect(inviteView({ ...pending, status: 'EXPIRED' }, true, 'nikola@example.com')).toBe('expired');
    expect(inviteView({ ...pending, status: 'REVOKED' }, false, null)).toBe('revoked');
    expect(inviteView({ ...pending, status: 'ACCEPTED' }, true, 'nikola@example.com')).toBe('accepted');
  });

  it('asks a signed-out visitor to sign up or log in', () => {
    expect(inviteView(pending, false, null)).toBe('signIn');
  });

  it('joins only when the signed-in address is the invited one', () => {
    expect(inviteView(pending, true, 'nikola@example.com')).toBe('join');
    expect(inviteView(pending, true, 'ivana@example.com')).toBe('wrongAccount');
    expect(inviteView(pending, true, null)).toBe('wrongAccount');
  });
});
