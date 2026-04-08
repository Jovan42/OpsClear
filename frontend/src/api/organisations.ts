import apiClient from './client';
import type { OrgInviteResponse, OrgMemberResponse, OrgRole, OrganisationResponse } from '../types';

export const organisationsApi = {
  mine: () =>
    apiClient
      .get<OrganisationResponse>('/api/organisations/mine', { validateStatus: (s) => s === 200 || s === 204 })
      .then((r) => (r.status === 204 ? null : r.data)),

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

  listInvites: (orgId: string) =>
    apiClient.get<OrgInviteResponse[]>(`/api/organisations/${orgId}/invites`).then((r) => r.data),

  sendInvite: (orgId: string, email: string) =>
    apiClient
      .post<OrgInviteResponse>(`/api/organisations/${orgId}/invites`, { email })
      .then((r) => r.data),

  revokeInvite: (orgId: string, inviteId: string) =>
    apiClient.delete(`/api/organisations/${orgId}/invites/${inviteId}`),

  acceptInvite: (token: string) =>
    apiClient.post<OrgInviteResponse>(`/api/invites/${token}/accept`).then((r) => r.data),
};
