import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { superAdminPricingApi } from '../../api/superAdminPricing';
import type { SubscriptionAddonResponse, SubscriptionTierResponse } from '../../types';

const TIERS_KEY = ['superAdmin', 'pricing', 'tiers'];
const ADDONS_KEY = ['superAdmin', 'pricing', 'addons'];

// 403 (not a super_user) is a permanent state for this session, not a transient
// failure — retrying just re-triggers the global "Forbidden" toast repeatedly.
export function useSuperAdminTiers() {
  return useQuery({
    queryKey: TIERS_KEY,
    queryFn: () => superAdminPricingApi.listTiers(),
    retry: false,
  });
}

export function useSuperAdminAddons() {
  return useQuery({
    queryKey: ADDONS_KEY,
    queryFn: () => superAdminPricingApi.listAddons(),
    retry: false,
  });
}

export function useUpdateTierPrice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { tierId: string; priceMonthly: number; priceAnnual: number }) =>
      superAdminPricingApi.updateTierPrice(vars.tierId, {
        priceMonthly: vars.priceMonthly,
        priceAnnual: vars.priceAnnual,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData<SubscriptionTierResponse[]>(TIERS_KEY, (prev) =>
        prev?.map((t) => (t.id === updated.id ? updated : t)),
      );
    },
  });
}

export function useUpdateAddonPrice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { addonKey: string; priceMonthly: number; priceAnnual: number }) =>
      superAdminPricingApi.updateAddonPrice(vars.addonKey, {
        priceMonthly: vars.priceMonthly,
        priceAnnual: vars.priceAnnual,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData<SubscriptionAddonResponse[]>(ADDONS_KEY, (prev) =>
        prev?.map((a) => (a.key === updated.key ? updated : a)),
      );
    },
  });
}
