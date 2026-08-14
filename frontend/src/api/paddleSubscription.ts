import apiClient from './client';
import type {
  InitiateSubscriptionResponse,
  PaddleSubscriptionResponse,
  UpdatePaymentMethodTransactionResponse,
} from '../types';

export const paddleSubscriptionApi = {
  initiate: (orgId: string) =>
    apiClient
      .post<InitiateSubscriptionResponse>(`/api/organisations/${orgId}/subscription/paddle`)
      .then((r) => r.data),

  update: (orgId: string, body: { tierId: string; addonIds: string[] }) =>
    apiClient
      .put<PaddleSubscriptionResponse>(`/api/organisations/${orgId}/subscription/paddle`, body)
      .then((r) => r.data),

  cancel: (orgId: string) =>
    apiClient
      .post<PaddleSubscriptionResponse>(`/api/organisations/${orgId}/subscription/paddle/cancel`)
      .then((r) => r.data),

  resume: (orgId: string) =>
    apiClient
      .post<PaddleSubscriptionResponse>(`/api/organisations/${orgId}/subscription/paddle/resume`)
      .then((r) => r.data),

  getUpdatePaymentMethodTransaction: (orgId: string) =>
    apiClient
      .get<UpdatePaymentMethodTransactionResponse>(
        `/api/organisations/${orgId}/subscription/paddle/update-payment-method-transaction`,
      )
      .then((r) => r.data),
};
