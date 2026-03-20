import apiClient from './client';
import type { ApiKeyResponse, CreateApiKeyResponse } from '../types';

export const apiKeysApi = {
  list: () =>
    apiClient.get<ApiKeyResponse[]>('/api/user/api-keys').then((r) => r.data),

  create: (body: { name: string }) =>
    apiClient.post<CreateApiKeyResponse>('/api/user/api-keys', body).then((r) => r.data),

  revoke: (id: string) =>
    apiClient.delete(`/api/user/api-keys/${id}`),
};
