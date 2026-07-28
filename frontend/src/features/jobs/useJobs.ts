import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { jobsApi } from '../../api/jobs';
import { jobLinksApi } from '../../api/links';
import { useCurrentOrg } from '../org/OrgContext';
import type { JobPriority, JobRelationshipType, JobStatus } from '../../types';

export function useJobList(projectId: string, q?: string, priority?: JobPriority, milestoneId?: string, typeId?: string) {
  return useQuery({
    queryKey: ['jobs', projectId, q ?? '', priority ?? '', milestoneId ?? '', typeId ?? ''],
    queryFn: () => jobsApi.list(projectId, q, priority, milestoneId, typeId),
    placeholderData: keepPreviousData,
  });
}

export function useJob(projectId: string, jobId: string) {
  return useQuery({
    queryKey: ['jobs', projectId, jobId],
    queryFn: () => jobsApi.get(projectId, jobId),
  });
}

export function useCreateJob(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      title: string;
      description?: string;
      client?: string;
      assignedTo?: string;
      deadline?: string;
      priority?: JobPriority;
      milestoneId?: string;
      typeId?: string;
    }) => jobsApi.create(projectId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
    },
  });
}

export function useUpdateJob(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      jobId,
      body,
    }: {
      jobId: string;
      body: {
        title: string;
        description?: string;
        client?: string;
        assignedTo?: string;
        deadline?: string;
        priority?: JobPriority;
        milestoneId?: string;
        typeId?: string;
      };
    }) => jobsApi.update(projectId, jobId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
    },
  });
}

export function useUpdateJobStatus(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      jobId,
      status,
      reason,
    }: {
      jobId: string;
      status: JobStatus;
      reason?: string;
    }) => jobsApi.updateStatus(projectId, jobId, { status, reason }),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, variables.jobId] });
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
      if (variables.status === 'BLOCKED') {
        void queryClient.invalidateQueries({ queryKey: ['block-reasons', projectId] });
      }
    },
  });
}

export function useDeleteJob(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (jobId: string) => jobsApi.delete(projectId, jobId),
    onSuccess: (_, jobId) => {
      queryClient.removeQueries({ queryKey: ['jobs', projectId, jobId] });
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId], exact: true });
      void queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
    },
  });
}

export function useCreateRelationship(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { targetJobId: string; type: JobRelationshipType }) =>
      jobsApi.createRelationship(projectId, jobId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId] });
    },
  });
}

export function useDeleteRelationship(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (relationshipId: string) =>
      jobsApi.deleteRelationship(projectId, jobId, relationshipId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId] });
    },
  });
}

export function useCreateJobLink(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { url: string; label?: string }) => jobLinksApi.create(projectId, jobId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId] });
    },
  });
}

export function useUpdateJobLink(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ linkId, body }: { linkId: string; body: { url: string; label?: string } }) =>
      jobLinksApi.update(projectId, jobId, linkId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId] });
    },
  });
}

export function useDeleteJobLink(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (linkId: string) => jobLinksApi.delete(projectId, jobId, linkId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId] });
    },
  });
}

export function useJobHistory(projectId: string, jobId: string) {
  const { hasAddon } = useCurrentOrg();
  return useQuery({
    queryKey: ['jobs', projectId, jobId, 'history'],
    queryFn: () => jobsApi.history(projectId, jobId),
    enabled: !!projectId && !!jobId && hasAddon('JOB_STATUS_HISTORY'),
  });
}
