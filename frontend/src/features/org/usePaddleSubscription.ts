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

export function usePreviewPaddleSubscriptionUpdate(orgId: string) {
  return useMutation({
    mutationFn: (body: { tierId: string; addonIds: string[] }) => paddleSubscriptionApi.preview(orgId, body),
  });
}

export function useUpdatePaddleSubscription(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { tierId: string; addonIds: string[] }) => paddleSubscriptionApi.update(orgId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'subscription'] });
    },
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

export function useResumeSubscription(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => paddleSubscriptionApi.resume(orgId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'subscription'] });
    },
  });
}

export function useCancelPendingDowngrade(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => paddleSubscriptionApi.cancelPendingDowngrade(orgId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'subscription'] });
    },
  });
}
