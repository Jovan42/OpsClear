import { describe, expect, it, vi } from 'vitest';
import { MutationObserver, QueryClient } from '@tanstack/react-query';
import type { OrgInviteResponse } from '../../types';

vi.mock('../../api/organisations', () => ({
  organisationsApi: {
    acceptInvite: vi.fn((token: string) => Promise.resolve({ token } as OrgInviteResponse)),
  },
}));

const { acceptInviteMutationOptions, publicInviteKey } = await import('./useOrganisation');

const MY_ORG = ['organisations', 'mine'];

describe('acceptInviteMutationOptions', () => {
  // JOB-399 review: with only an invalidate, a cached "no organisation" stayed in the cache
  // (nothing on the invite page observes it), and /projects sent the new member to onboarding.
  it('removes a cached "no organisation" before the accept reports success', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(MY_ORG, null);
    const observer = new MutationObserver(queryClient, acceptInviteMutationOptions(queryClient));

    const myOrgStateAtSuccess: unknown[] = [];
    observer.subscribe((result) => {
      if (result.isSuccess) myOrgStateAtSuccess.push(queryClient.getQueryState(MY_ORG));
    });
    await observer.mutate('tok');

    expect(myOrgStateAtSuccess).toEqual([undefined]);
  });

  it('marks the invite stale after the accept settles, so the page reloads it', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(publicInviteKey('tok'), { status: 'PENDING' });
    const observer = new MutationObserver(queryClient, acceptInviteMutationOptions(queryClient));

    await observer.mutate('tok');

    expect(queryClient.getQueryState(publicInviteKey('tok'))?.isInvalidated).toBe(true);
  });
});
