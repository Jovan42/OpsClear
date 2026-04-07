import apiClient from './client';
import type { OrganisationResponse } from '../types';

export const organisationsApi = {
  get: (id: string) =>
    apiClient.get<OrganisationResponse>(`/api/organisations/${id}`).then((r) => r.data),

  create: (body: { name: string; slug: string }) =>
    apiClient.post<OrganisationResponse>('/api/organisations', body).then((r) => r.data),

  update: (id: string, body: { name: string; slug: string }) =>
    apiClient.patch<OrganisationResponse>(`/api/organisations/${id}`, body).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete(`/api/organisations/${id}`),
};
