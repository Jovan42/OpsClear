import apiClient from './client';
import type { CreditBalanceResponse } from '../types';

export const creditsApi = {
  getBalance: (orgId: string) =>
    apiClient.get<CreditBalanceResponse>(`/api/organisations/${orgId}/credits/balance`).then((r) => r.data),
};
