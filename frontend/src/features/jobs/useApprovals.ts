import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { approvalsApi } from '../../api/approvals';
import { refreshApprovalsAfterConflict } from '../approvals/approvalRefresh';
import { useProject } from '../projects/useProjects';
import { useSectionVisibility } from './useSectionVisibility';

export function useApprovals(projectId: string, jobId: string) {
  const { data: project } = useProject(projectId);
  const { isVisible } = useSectionVisibility(project);
  return useQuery({
    queryKey: ['jobs', projectId, jobId, 'approvals'],
    queryFn: () => approvalsApi.listByJob(projectId, jobId),
    enabled: !!projectId && !!jobId && isVisible('APPROVALS'),
  });
}

export function useRequestApproval(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (description: string) =>
      approvalsApi.request(projectId, jobId, { description }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId, 'approvals'] });
      void queryClient.invalidateQueries({ queryKey: ['approvals', projectId, 'pending'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
    },
  });
}

export function useDecideApproval(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      approvalId,
      status,
      comment,
    }: {
      approvalId: string;
      status: 'APPROVED' | 'REJECTED';
      comment?: string;
    }) => approvalsApi.decide(projectId, jobId, approvalId, { status, comment }),
    onSuccess: () => {
      // ['jobs', projectId] covers the job itself (approving a completion approval can complete
      // it, ADR-0052), its approvals and the job lists. ['approvals'] covers both pending queues.
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['approvals'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
    },
    // 409: someone else already decided or withdrew it, so drop the stale pending card.
    onError: (error) => refreshApprovalsAfterConflict(queryClient, projectId, error),
  });
}

/** ADR-0052: withdraw a pending completion approval (requester or Owner/Admin). */
export function useCancelApproval(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (approvalId: string) => approvalsApi.cancel(projectId, jobId, approvalId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['approvals'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
    },
    onError: (error) => refreshApprovalsAfterConflict(queryClient, projectId, error),
  });
}
