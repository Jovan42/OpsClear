import apiClient from './client';
import type { OrgMemberResponse, OrgRole, OrganisationResponse } from '../types';

export const organisationsApi = {
  get: (id: string) =>
    apiClient.get<OrganisationResponse>(`/api/organisations/${id}`).then((r) => r.data),

  create: (body: { name: string; slug: string }) =>
    apiClient.post<OrganisationResponse>('/api/organisations', body).then((r) => r.data),

  update: (id: string, body: { name: string; slug: string }) =>
    apiClient.patch<OrganisationResponse>(`/api/organisations/${id}`, body).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete(`/api/organisations/${id}`),

  listMembers: (orgId: string) =>
    apiClient.get<OrgMemberResponse[]>(`/api/organisations/${orgId}/members`).then((r) => r.data),

  addMember: (orgId: string, body: { userId: string; role: OrgRole }) =>
    apiClient.post<OrgMemberResponse>(`/api/organisations/${orgId}/members`, body).then((r) => r.data),

  updateMemberRole: (orgId: string, userId: string, role: OrgRole) =>
    apiClient
      .patch<OrgMemberResponse>(`/api/organisations/${orgId}/members/${userId}`, { role })
      .then((r) => r.data),

  removeMember: (orgId: string, userId: string) =>
    apiClient.delete(`/api/organisations/${orgId}/members/${userId}`),
};
