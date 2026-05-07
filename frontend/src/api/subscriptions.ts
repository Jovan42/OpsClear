import apiClient from './client';
import type { CatalogResponse, OrgSubscriptionResponse } from '../types';

export const subscriptionsApi = {
  catalog: () =>
    apiClient.get<CatalogResponse>('/api/subscriptions/catalog').then((r) => r.data),

  getOrgSubscription: (orgId: string) =>
    apiClient
      .get<OrgSubscriptionResponse>(`/api/organisations/${orgId}/subscription`, {
        validateStatus: (s) => s === 200 || s === 404,
      })
      .then((r) => (r.status === 404 ? null : r.data)),

  upsertOrgSubscription: (
    orgId: string,
    body: { tierId: string; billingCycle: 'MONTHLY' | 'ANNUAL'; addonIds: string[] },
  ) =>
    apiClient
      .put<OrgSubscriptionResponse>(`/api/organisations/${orgId}/subscription`, body)
      .then((r) => r.data),
};
