import { useQuery } from '@tanstack/react-query';
import { billingApi } from '../../api/billing';
import { useOrgSubscription } from './useSubscription';

export function useBillingHistory(orgId: string) {
  return useQuery({
    queryKey: ['organisations', orgId, 'billing-history'],
    queryFn: () => billingApi.getBillingHistory(orgId),
  });
}

// Reuses useOrgSubscription's cache (same query key) rather than issuing a second
// fetch — this is just a narrower view onto the same data for callers that only
// care about the status.
export function useSubscriptionStatus(orgId: string) {
  const { data } = useOrgSubscription(orgId);
  return data?.subscriptionStatus ?? null;
}
