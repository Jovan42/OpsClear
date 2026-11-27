import apiClient from './client';
import type {
  InviteLanguage,
  OrgInviteResponse,
  OrgMemberResponse,
  OrgRole,
  OrganisationResponse,
  OrgSettings,
  PublicInviteResponse,
  ProjectDirectoryEntryResponse,
  UpdateOrgSettingsRequest,
} from '../types';

export const organisationsApi = {
  mine: () =>
    apiClient
      .get<OrganisationResponse>('/api/organisations/mine', { validateStatus: (s) => s === 200 || s === 204 })
      .then((r) => (r.status === 204 ? null : r.data)),

  get: (id: string) =>
    apiClient.get<OrganisationResponse>(`/api/organisations/${id}`).then((r) => r.data),

  // 403 only means "sign-up is closed until launch" (ADR-0066): the page shows its own card instead
  create: (body: { name: string; slug: string }) =>
    apiClient
      .post<OrganisationResponse>('/api/organisations', body, { skipErrorToastStatuses: [403] })
      .then((r) => r.data),

  update: (id: string, body: { name: string; slug: string }) =>
    apiClient.patch<OrganisationResponse>(`/api/organisations/${id}`, body).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete(`/api/organisations/${id}`),

  getOrgSettings: (orgId: string) =>
    apiClient.get<OrgSettings>(`/api/organisations/${orgId}/settings`).then((r) => r.data),

  // The ID prefixes section shows a 400's message on the field it names (or its own
  // toast), so the global error toast skips it
  updateOrgSettings: (orgId: string, patch: UpdateOrgSettingsRequest) =>
    apiClient
      .patch<OrgSettings>(`/api/organisations/${orgId}/settings`, patch, {
        skipErrorToastStatuses: [400],
      })
      .then((r) => r.data),

  listMembers: (orgId: string) =>
    apiClient.get<OrgMemberResponse[]>(`/api/organisations/${orgId}/members`).then((r) => r.data),

  updateMemberRole: (orgId: string, userId: string, role: OrgRole) =>
    apiClient
      .patch<OrgMemberResponse>(`/api/organisations/${orgId}/members/${userId}`, { role })
      .then((r) => r.data),

  removeMember: (orgId: string, userId: string) =>
    apiClient.delete(`/api/organisations/${orgId}/members/${userId}`),

  listInvites: (orgId: string) =>
    apiClient.get<OrgInviteResponse[]>(`/api/organisations/${orgId}/invites`).then((r) => r.data),

  sendInvite: (orgId: string, email: string, language: InviteLanguage) =>
    apiClient
      .post<OrgInviteResponse>(`/api/organisations/${orgId}/invites`, { email, language })
      .then((r) => r.data),

  // The page shows its own cooldown message for a 429, so the global error toast skips it
  resendInvite: (orgId: string, inviteId: string, language: InviteLanguage) =>
    apiClient
      .post<OrgInviteResponse>(
        `/api/organisations/${orgId}/invites/${inviteId}/resend`,
        { language },
        { skipErrorToastStatuses: [429] },
      )
      .then((r) => r.data),

  revokeInvite: (orgId: string, inviteId: string) =>
    apiClient.delete(`/api/organisations/${orgId}/invites/${inviteId}`),

  // Public (no login needed): the invite page shows it before the visitor has an account.
  // Its 404 (unknown token, deleted organisation) has its own message on the page.
  inviteByToken: (token: string) =>
    apiClient.get<PublicInviteResponse>(`/api/invites/${token}`).then((r) => r.data),

  // The invite page shows every accept error itself, so the global error toast skips them
  acceptInvite: (token: string) =>
    apiClient
      .post<OrgInviteResponse>(`/api/invites/${token}/accept`, undefined, { skipErrorToast: true })
      .then((r) => r.data),

  projectDirectory: (orgId: string) =>
    apiClient
      .get<ProjectDirectoryEntryResponse[]>(`/api/organisations/${orgId}/projects/directory`)
      .then((r) => r.data),
};
