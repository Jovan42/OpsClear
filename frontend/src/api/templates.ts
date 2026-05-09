import apiClient from './client';
import type { JobTemplateResponse, JobPriority, AssigneeMode } from '../types';

export interface TemplateBody {
  name: string;
  title?: string;
  description?: string;
  client?: string;
  priority?: JobPriority;
  assigneeMode?: AssigneeMode;
  assigneeId?: string;
  milestoneId?: string;
  deadlineOffsetDays?: number;
}

export const templatesApi = {
  list: (projectId: string) =>
    apiClient.get<JobTemplateResponse[]>(`/api/projects/${projectId}/templates`).then((r) => r.data),

  create: (projectId: string, body: TemplateBody) =>
    apiClient.post<JobTemplateResponse>(`/api/projects/${projectId}/templates`, body).then((r) => r.data),

  update: (projectId: string, templateId: string, body: TemplateBody) =>
    apiClient.put<JobTemplateResponse>(`/api/projects/${projectId}/templates/${templateId}`, body).then((r) => r.data),

  delete: (projectId: string, templateId: string) =>
    apiClient.delete(`/api/projects/${projectId}/templates/${templateId}`),

  recordUsage: (projectId: string, templateId: string) =>
    apiClient.post(`/api/projects/${projectId}/templates/${templateId}/use`),
};
