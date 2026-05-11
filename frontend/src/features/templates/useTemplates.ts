import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { templatesApi, type TemplateBody } from '../../api/templates';

export function useTemplates(projectId: string) {
  return useQuery({
    queryKey: ['templates', projectId],
    queryFn: () => templatesApi.list(projectId),
    enabled: !!projectId,
  });
}

export function useCreateTemplate(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: TemplateBody) => templatesApi.create(projectId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['templates', projectId] });
    },
  });
}

export function useUpdateTemplate(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ templateId, body }: { templateId: string; body: TemplateBody }) =>
      templatesApi.update(projectId, templateId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['templates', projectId] });
    },
  });
}

export function useDeleteTemplate(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) => templatesApi.delete(projectId, templateId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['templates', projectId] });
    },
  });
}

export function useOrgTemplates(orgId: string | null) {
  return useQuery({
    queryKey: ['org-templates', orgId],
    queryFn: () => templatesApi.orgList(orgId!),
    enabled: !!orgId,
  });
}

export function useCreateOrgTemplate(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: TemplateBody) => templatesApi.orgCreate(orgId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['org-templates', orgId] });
    },
  });
}

export function useUpdateOrgTemplate(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ templateId, body }: { templateId: string; body: TemplateBody }) =>
      templatesApi.orgUpdate(orgId, templateId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['org-templates', orgId] });
    },
  });
}

export function useDeleteOrgTemplate(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) => templatesApi.orgDelete(orgId, templateId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['org-templates', orgId] });
    },
  });
}
