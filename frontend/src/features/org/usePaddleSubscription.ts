import { useMutation, useQueryClient } from '@tanstack/react-query';
import { paddleSubscriptionApi } from '../../api/paddleSubscription';

export function useInitiatePaddleSubscription(orgId: string) {
  return useMutation({
    mutationFn: () => paddleSubscriptionApi.initiate(orgId),
  });
}

export function useUpdatePaymentMethod(orgId: string) {
  return useMutation({
    mutationFn: () => paddleSubscriptionApi.getUpdatePaymentMethodTransaction(orgId),
  });
}

export function useCancelSubscription(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => paddleSubscriptionApi.cancel(orgId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'subscription'] });
    },
  });
}
