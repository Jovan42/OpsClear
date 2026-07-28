import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { milestonesApi } from '../../api/milestones';
import { useCurrentOrg } from '../org/OrgContext';

export function useMilestones(projectId: string) {
  const { hasAddon } = useCurrentOrg();
  return useQuery({
    queryKey: ['milestones', projectId],
    queryFn: () => milestonesApi.list(projectId),
    enabled: !!projectId && hasAddon('MILESTONES'),
  });
}

export function useCreateMilestone(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; description?: string; deadline?: string }) =>
      milestonesApi.create(projectId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['milestones', projectId] });
    },
  });
}

export function useUpdateMilestone(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ milestoneId, body }: { milestoneId: string; body: { name: string; description?: string; deadline?: string } }) =>
      milestonesApi.update(projectId, milestoneId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['milestones', projectId] });
    },
  });
}

export function useDeleteMilestone(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (milestoneId: string) => milestonesApi.delete(projectId, milestoneId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['milestones', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId] });
    },
  });
}
