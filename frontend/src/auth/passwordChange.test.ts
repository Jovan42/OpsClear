import { afterEach, describe, expect, it, vi } from 'vitest';

const login = vi.fn<(options: unknown) => Promise<void>>(() => Promise.resolve());
vi.mock('./keycloak', () => ({ default: { login: (options: unknown) => login(options) } }));

const {
  clearPasswordChangeStatus,
  peekPasswordChangeStatus,
  recordAccountAction,
  startPasswordChange,
} = await import('./passwordChange');

afterEach(() => {
  clearPasswordChangeStatus();
  login.mockClear();
  vi.unstubAllGlobals();
});

describe('password change status', () => {
  it('is empty until Keycloak reports the update-password action', () => {
    expect(peekPasswordChangeStatus()).toBeNull();
  });

  it.each(['success', 'cancelled', 'error'] as const)('remembers a %s update-password result', (status) => {
    recordAccountAction(status, 'UPDATE_PASSWORD');
    expect(peekPasswordChangeStatus()).toBe(status);
  });

  it('takes a status without an action as the password result (Keycloak 24 sends no kc_action)', () => {
    recordAccountAction('cancelled');
    expect(peekPasswordChangeStatus()).toBe('cancelled');
  });

  it('ignores other named Keycloak actions', () => {
    recordAccountAction('success', 'UPDATE_PROFILE');
    expect(peekPasswordChangeStatus()).toBeNull();
  });

  it('keeps the result until it is cleared', () => {
    recordAccountAction('success', 'UPDATE_PASSWORD');
    expect(peekPasswordChangeStatus()).toBe('success');
    expect(peekPasswordChangeStatus()).toBe('success');
    clearPasswordChangeStatus();
    expect(peekPasswordChangeStatus()).toBeNull();
  });
});

describe('startPasswordChange', () => {
  it('asks Keycloak for the update-password action, returning to Settings in the given language', async () => {
    vi.stubGlobal('window', { location: { origin: 'https://app.example.com' } });
    await startPasswordChange('sr');
    expect(login).toHaveBeenCalledWith({
      action: 'UPDATE_PASSWORD',
      redirectUri: 'https://app.example.com/settings',
      locale: 'sr',
    });
  });
});
