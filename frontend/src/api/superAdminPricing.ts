import apiClient from './client';
import type { SubscriptionAddonResponse, SubscriptionTierResponse } from '../types';

export interface UpdatePriceRequest {
  priceMonthly: number;
  priceAnnual: number;
}

export const superAdminPricingApi = {
  listTiers: () =>
    apiClient.get<SubscriptionTierResponse[]>('/api/super-admin/pricing/tiers').then((r) => r.data),

  updateTierPrice: (tierId: string, body: UpdatePriceRequest) =>
    apiClient
      .put<SubscriptionTierResponse>(`/api/super-admin/pricing/tiers/${tierId}`, body)
      .then((r) => r.data),

  listAddons: () =>
    apiClient.get<SubscriptionAddonResponse[]>('/api/super-admin/pricing/addons').then((r) => r.data),

  updateAddonPrice: (addonKey: string, body: UpdatePriceRequest) =>
    apiClient
      .put<SubscriptionAddonResponse>(`/api/super-admin/pricing/addons/${addonKey}`, body)
      .then((r) => r.data),
};
