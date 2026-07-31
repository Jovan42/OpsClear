import { useQuery } from '@tanstack/react-query';
import { creditsApi } from '../../api/credits';

// `enabled` is the caller's own Owner/Admin check — the backend 403s for a plain
// member, so the request shouldn't fire at all for a viewer who can't see it.
export function useOrgCreditBalance(orgId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: ['organisations', orgId, 'credits', 'balance'],
    queryFn: () => creditsApi.getBalance(orgId!),
    enabled: !!orgId && enabled,
  });
}
