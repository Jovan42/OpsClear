import { describe, expect, it, vi } from 'vitest';
import { MutationObserver, QueryClient, QueryObserver } from '@tanstack/react-query';
import type { OrgInviteResponse, PublicInviteResponse } from '../../types';

vi.mock('../../api/organisations', () => ({
  organisationsApi: {
    acceptInvite: vi.fn((token: string) => Promise.resolve({ token } as OrgInviteResponse)),
  },
}));

const { organisationsApi } = await import('../../api/organisations');
const { acceptInviteMutationOptions, publicInviteKey } = await import('./useOrganisation');

const MY_ORG = ['organisations', 'mine'];

/** Lets queued promises and TanStack's scheduling run. */
const flush = () => new Promise((r) => setTimeout(r, 0));

/** An invite query with an observer, as when the invite page is open; counts its loads. */
function openInvitePage(queryClient: QueryClient) {
  const queryFn = vi.fn(() => Promise.resolve({ status: 'PENDING' } as PublicInviteResponse));
  const observer = new QueryObserver(queryClient, { queryKey: publicInviteKey('tok'), queryFn, staleTime: Infinity });
  const unsubscribe = observer.subscribe(() => {});
  return { queryFn, unsubscribe };
}

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

  // JOB-399 CI: reloading the invite on success made the open page show "already used"
  // instead of moving on to the projects list.
  it('on success marks the invite stale without reloading it', async () => {
    const queryClient = new QueryClient();
    const page = openInvitePage(queryClient);
    await flush();
    expect(page.queryFn).toHaveBeenCalledTimes(1);

    await new MutationObserver(queryClient, acceptInviteMutationOptions(queryClient)).mutate('tok');
    await flush();

    expect(page.queryFn).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryState(publicInviteKey('tok'))?.isInvalidated).toBe(true);
    page.unsubscribe();
  });

  it('on error reloads the invite, so the page shows a status that changed meanwhile', async () => {
    vi.mocked(organisationsApi.acceptInvite).mockRejectedValueOnce(new Error('409'));
    const queryClient = new QueryClient();
    const page = openInvitePage(queryClient);
    await flush();

    await expect(
      new MutationObserver(queryClient, acceptInviteMutationOptions(queryClient)).mutate('tok'),
    ).rejects.toThrow('409');
    await flush();

    expect(page.queryFn).toHaveBeenCalledTimes(2);
    page.unsubscribe();
  });
});
