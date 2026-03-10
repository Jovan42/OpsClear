import apiClient from './client';
import type { ProjectResponse, ProjectMemberResponse } from '../types';

export const projectsApi = {
  list: () =>
    apiClient.get<ProjectResponse[]>('/api/projects').then((r) => r.data),

  get: (projectId: string) =>
    apiClient.get<ProjectResponse>(`/api/projects/${projectId}`).then((r) => r.data),

  create: (body: { name: string; description?: string; blockReasons?: string[] }) =>
    apiClient.post<ProjectResponse>('/api/projects', body).then((r) => r.data),

  update: (projectId: string, body: { name: string; description?: string }) =>
    apiClient.put<ProjectResponse>(`/api/projects/${projectId}`, body).then((r) => r.data),

  delete: (projectId: string) =>
    apiClient.delete(`/api/projects/${projectId}`),

  listMembers: (projectId: string) =>
    apiClient
      .get<ProjectMemberResponse[]>(`/api/projects/${projectId}/members`)
      .then((r) => r.data),

  addMember: (projectId: string, body: { userId: string; role: string }) =>
    apiClient
      .post<ProjectMemberResponse>(`/api/projects/${projectId}/members`, body)
      .then((r) => r.data),

  updateMember: (projectId: string, memberId: string, body: { role: string }) =>
    apiClient
      .put<ProjectMemberResponse>(`/api/projects/${projectId}/members/${memberId}`, body)
      .then((r) => r.data),

  removeMember: (projectId: string, memberId: string) =>
    apiClient.delete(`/api/projects/${projectId}/members/${memberId}`),
};
