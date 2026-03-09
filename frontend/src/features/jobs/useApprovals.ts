import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { approvalsApi } from '../../api/approvals';

export function useApprovals(projectId: string, jobId: string) {
  return useQuery({
    queryKey: ['jobs', projectId, jobId, 'approvals'],
    queryFn: () => approvalsApi.listByJob(projectId, jobId),
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
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId, 'approvals'] });
      void queryClient.invalidateQueries({ queryKey: ['approvals', projectId, 'pending'] });
    },
  });
}
