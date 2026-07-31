import apiClient from './client';
import type { CreditBalanceResponse, CreditLedgerEntryResponse, OrganisationResponse } from '../types';

export const creditsApi = {
  getBalance: (orgId: string) =>
    apiClient.get<CreditBalanceResponse>(`/api/organisations/${orgId}/credits/balance`).then((r) => r.data),

  // --- Super admin only ---

  grant: (body: { orgId: string; amount: number; reason: string; submissionId?: string }) =>
    apiClient.post<CreditLedgerEntryResponse>('/api/super-admin/credits/grant', body).then((r) => r.data),

  getLedger: (orgId: string) =>
    apiClient
      .get<CreditLedgerEntryResponse[]>(`/api/super-admin/organisations/${orgId}/credits`)
      .then((r) => r.data),

  listOrganisations: () =>
    apiClient.get<OrganisationResponse[]>('/api/super-admin/organisations').then((r) => r.data),
};
