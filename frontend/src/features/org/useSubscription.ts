import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { subscriptionsApi } from '../../api/subscriptions';

export function useCatalog() {
  return useQuery({
    queryKey: ['subscription', 'catalog'],
    queryFn: () => subscriptionsApi.catalog(),
    staleTime: Infinity,
  });
}

export function useOrgSubscription(orgId: string | null) {
  return useQuery({
    queryKey: ['organisations', orgId, 'subscription'],
    queryFn: () => subscriptionsApi.getOrgSubscription(orgId!),
    enabled: !!orgId,
  });
}

export function useUpsertOrgSubscription(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { tierId: string; billingCycle: 'MONTHLY' | 'ANNUAL'; addonIds: string[] }) =>
      subscriptionsApi.upsertOrgSubscription(orgId, body),
    onSuccess: (data) => {
      queryClient.setQueryData(['organisations', orgId, 'subscription'], data);
    },
  });
}
