import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { jobsApi } from '../../api/jobs';
import { jobLinksApi } from '../../api/links';
import { useProject } from '../projects/useProjects';
import { useSectionVisibility } from './useSectionVisibility';
import type { JobPriority, JobRelationshipType, JobResponse, JobStatus } from '../../types';

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

/**
 * `jobId` should be the same id the job's detail query is keyed by (the route's friendly id on
 * the job page), so the returned job can be written straight into that cache entry.
 *
 * ADR-0052: on a gated project a Member's COMPLETED answers 202 with the job still IN_PROGRESS
 * and its `pendingCompletionApproval` set; use {@link isCompletionRequested} to tell it apart.
 */
export function useUpdateJobStatus(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      jobId,
      status,
      reason,
      message,
    }: {
      jobId: string;
      status: JobStatus;
      reason?: string;
      message?: string;
    }) => jobsApi.updateStatus(projectId, jobId, { status, reason, message }),
    onSuccess: (job, variables) => {
      queryClient.setQueryData(['jobs', projectId, variables.jobId], job);
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
      if (variables.status === 'BLOCKED') {
        void queryClient.invalidateQueries({ queryKey: ['block-reasons', projectId] });
      }
      // A completion request creates an approval; a direct completion auto-cancels a pending one.
      if (variables.status === 'COMPLETED') {
        void queryClient.invalidateQueries({ queryKey: ['approvals'] });
      }
    },
  });
}

/** ADR-0052: true when a COMPLETED request created a completion approval instead (the 202 case). */
export function isCompletionRequested(job: JobResponse): boolean {
  return job.status !== 'COMPLETED' && job.pendingCompletionApproval !== null;
}

export function useDeleteJob(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (jobId: string) => jobsApi.delete(projectId, jobId),
    onSuccess: (_, jobId) => {
      queryClient.removeQueries({ queryKey: ['jobs', projectId, jobId] });
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId], exact: true });
      void queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
      // Deleting a job cancels its pending completion approval (ADR-0052), so refresh the queues.
      void queryClient.invalidateQueries({ queryKey: ['approvals'] });
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
  const { data: project } = useProject(projectId);
  const { isVisible } = useSectionVisibility(project);
  return useQuery({
    queryKey: ['jobs', projectId, jobId, 'history'],
    queryFn: () => jobsApi.history(projectId, jobId),
    enabled: !!projectId && !!jobId && isVisible('STATUS_HISTORY'),
  });
}
