import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthContext';
import { projectsApi } from '../../api/projects';
import { projectLinksApi } from '../../api/links';
import type { ProjectStatus } from '../../types';

const PROJECT_ID_RE = /^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[A-Za-z]{2,6}-\d+)$/i;

export function useProjectList(status?: ProjectStatus | 'ALL') {
  return useQuery({
    queryKey: ['projects', { status: status ?? 'ACTIVE' }],
    queryFn: () => projectsApi.list(status),
  });
}

export function useProject(projectId: string) {
  return useQuery({
    queryKey: ['projects', projectId],
    queryFn: () => projectsApi.get(projectId),
    enabled: PROJECT_ID_RE.test(projectId),
  });
}

export function useProjectMembers(projectId: string) {
  return useQuery({
    queryKey: ['projects', projectId, 'members'],
    queryFn: () => projectsApi.listMembers(projectId),
    enabled: PROJECT_ID_RE.test(projectId),
  });
}

export function useProjectRole(projectId: string) {
  const { userId } = useAuth();
  const { data: members } = useProjectMembers(projectId);
  return members?.find((m) => m.userId === userId)?.role ?? null;
}

export function useCreateProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; description?: string; blockReasons?: string[] }) =>
      projectsApi.create(body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['projects'] }),
  });
}

export function useUpdateProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      projectId,
      body,
    }: {
      projectId: string;
      body: { name: string; description?: string };
    }) => projectsApi.update(projectId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useUpdateProjectStatus(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (status: ProjectStatus) => projectsApi.updateStatus(projectId, status),
    onSuccess: (data) => {
      queryClient.setQueryData(['projects', projectId], data);
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useDeleteProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (projectId: string) => projectsApi.delete(projectId),
    onSuccess: (_, projectId) => {
      queryClient.removeQueries({ queryKey: ['projects', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useAddMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { userId: string; role: string }) =>
      projectsApi.addMember(projectId, body),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'members'] }),
  });
}

export function useUpdateMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ memberId, role }: { memberId: string; role: string }) =>
      projectsApi.updateMember(projectId, memberId, { role }),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'members'] }),
  });
}

export function useRemoveMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) => projectsApi.removeMember(projectId, memberId),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'members'] }),
  });
}

export function useCreateProjectLink(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { url: string; label?: string }) => projectLinksApi.create(projectId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId] });
    },
  });
}

export function useUpdateProjectLink(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ linkId, body }: { linkId: string; body: { url: string; label?: string } }) =>
      projectLinksApi.update(projectId, linkId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId] });
    },
  });
}

export function useDeleteProjectLink(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (linkId: string) => projectLinksApi.delete(projectId, linkId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['projects', projectId] });
    },
  });
}
