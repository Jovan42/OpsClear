import { useQuery } from '@tanstack/react-query';
import { creditsApi } from '../../api/credits';

// Balance changes land here asynchronously, via a Paddle webhook reacting to a
// transaction elsewhere (grant sync, consumption on spend) — there's no local
// mutation to invalidate off of, so a light poll is what actually keeps this
// current instead of requiring a manual page reload after every such event.
const CREDIT_BALANCE_POLL_MS = 10_000;

// `enabled` is the caller's own Owner/Admin check — the backend 403s for a plain
// member, so the request shouldn't fire at all for a viewer who can't see it.
export function useOrgCreditBalance(orgId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: ['organisations', orgId, 'credits', 'balance'],
    queryFn: () => creditsApi.getBalance(orgId!),
    enabled: !!orgId && enabled,
    refetchInterval: enabled ? CREDIT_BALANCE_POLL_MS : false,
  });
}
