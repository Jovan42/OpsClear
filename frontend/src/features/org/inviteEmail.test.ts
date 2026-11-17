import { describe, expect, it } from 'vitest';
import { retryAfterMinutes, toInviteLanguage } from './inviteEmail';

describe('toInviteLanguage', () => {
  it('maps Serbian to sr', () => {
    expect(toInviteLanguage('sr')).toBe('sr');
    expect(toInviteLanguage('sr-Latn')).toBe('sr');
  });

  it('falls back to en for anything else', () => {
    expect(toInviteLanguage('en')).toBe('en');
    expect(toInviteLanguage('de')).toBe('en');
    expect(toInviteLanguage(undefined)).toBe('en');
  });
});

describe('retryAfterMinutes', () => {
  it('rounds the seconds up to whole minutes', () => {
    expect(retryAfterMinutes('300')).toBe(5);
    expect(retryAfterMinutes('299')).toBe(5);
    expect(retryAfterMinutes('61')).toBe(2);
    expect(retryAfterMinutes('1')).toBe(1);
    expect(retryAfterMinutes(120)).toBe(2);
  });

  it('is null for a missing or unusable header', () => {
    expect(retryAfterMinutes(undefined)).toBeNull();
    expect(retryAfterMinutes(null)).toBeNull();
    expect(retryAfterMinutes('')).toBeNull();
    expect(retryAfterMinutes('0')).toBeNull();
    expect(retryAfterMinutes('Wed, 21 Oct 2026 07:28:00 GMT')).toBeNull();
  });
});
