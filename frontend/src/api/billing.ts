import apiClient from './client';
import type { PaddleBillingTransactionResponse } from '../types';

export const billingApi = {
  getBillingHistory: (orgId: string) =>
    apiClient
      .get<PaddleBillingTransactionResponse[]>(`/api/organisations/${orgId}/subscription/paddle/transactions`)
      .then((r) => r.data),
};
