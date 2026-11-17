import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { MutationObserver, QueryClient } from '@tanstack/react-query';
import type { OrgInviteResponse } from '../../types';

interface PendingResend {
  inviteId: string;
  resolve: () => void;
  reject: (status: number, retryAfter?: string) => void;
}

// A fake server: each resend waits until the test resolves or rejects it.
const resends: PendingResend[] = [];

vi.mock('../../api/organisations', () => ({
  organisationsApi: {
    resendInvite: vi.fn(
      (_orgId: string, inviteId: string) =>
        new Promise<OrgInviteResponse>((resolve, reject) => {
          resends.push({
            inviteId,
            resolve: () => resolve({ id: inviteId } as OrgInviteResponse),
            reject: (status, retryAfter) => {
              const headers = new AxiosHeaders(retryAfter ? { 'retry-after': retryAfter } : {});
              const response = { status, headers, data: {} } as AxiosResponse;
              reject(new AxiosError(String(status), 'ERR_BAD_REQUEST', undefined, undefined, response));
            },
          });
        }),
    ),
  },
}));

const { resendInviteKey, resendInviteMutationOptions } = await import('./useOrganisation');

const ORG = 'org-1';

/** Lets queued promises and TanStack's scheduling run. */
const flush = () => new Promise((r) => setTimeout(r, 0));

function setUp() {
  const queryClient = new QueryClient();
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  const feedback = { sent: vi.fn(), cooldown: vi.fn() };
  // One observer, like the page's single useMutation, so a second mutate() replaces the first
  const observer = new MutationObserver(queryClient, resendInviteMutationOptions(queryClient, ORG, feedback));
  const resend = (inviteId: string) =>
    observer.mutate({ inviteId, language: 'en' }).catch(() => undefined);
  const resendingIds = () =>
    queryClient
      .getMutationCache()
      .findAll({ mutationKey: resendInviteKey(ORG), status: 'pending' })
      .map((m) => (m.state.variables as { inviteId: string }).inviteId);
  return { resend, resendingIds, feedback, invalidate };
}

describe('resend invite', () => {
  beforeEach(() => {
    resends.length = 0;
  });

  it('gives feedback for each of two overlapping resends and tracks both as running', async () => {
    const { resend, resendingIds, feedback } = setUp();

    const a = resend('A');
    await flush();
    const b = resend('B');
    await flush();
    expect(resendingIds()).toEqual(['A', 'B']);

    // A answers after B started: its cooldown still shows, and only B stays running
    resends[0].reject(429, '240');
    await a;
    await flush();
    expect(feedback.cooldown).toHaveBeenCalledExactlyOnceWith(4);
    expect(resendingIds()).toEqual(['B']);

    resends[1].resolve();
    await b;
    await flush();
    expect(feedback.sent).toHaveBeenCalledTimes(1);
    expect(resendingIds()).toEqual([]);
  });

  it('passes null minutes when Retry-After is unreadable', async () => {
    const { resend, feedback } = setUp();
    const a = resend('A');
    await flush();
    resends[0].reject(429);
    await a;
    await flush();
    expect(feedback.cooldown).toHaveBeenCalledExactlyOnceWith(null);
  });

  it('refreshes the invite list after a 409 too, and leaves other errors to the client toast', async () => {
    const { resend, feedback, invalidate } = setUp();
    const a = resend('A');
    await flush();
    resends[0].reject(409);
    await a;
    await flush();
    expect(feedback.sent).not.toHaveBeenCalled();
    expect(feedback.cooldown).not.toHaveBeenCalled();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['organisations', ORG, 'invites'] });
  });
});
