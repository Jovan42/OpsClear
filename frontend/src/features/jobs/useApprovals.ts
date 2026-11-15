import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { approvalsApi } from '../../api/approvals';
import { useCurrentOrg } from '../org/OrgContext';

export function useApprovals(projectId: string, jobId: string) {
  const { hasAddon } = useCurrentOrg();
  return useQuery({
    queryKey: ['jobs', projectId, jobId, 'approvals'],
    queryFn: () => approvalsApi.listByJob(projectId, jobId),
    enabled: !!projectId && !!jobId && hasAddon('APPROVALS'),
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
  });
}
