import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { jobTypesApi, type CreateJobTypeBody, type UpdateJobTypeBody } from '../../api/jobTypes';

export function useJobTypes(projectId: string) {
  return useQuery({
    queryKey: ['job-types', projectId],
    queryFn: () => jobTypesApi.list(projectId),
    enabled: !!projectId,
  });
}

export function useCreateJobType(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateJobTypeBody) => jobTypesApi.create(projectId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['job-types', projectId] });
    },
  });
}

export function useUpdateJobType(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ typeId, body }: { typeId: string; body: UpdateJobTypeBody }) =>
      jobTypesApi.update(projectId, typeId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['job-types', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['jobs', projectId] });
    },
  });
}

export function useDeleteJobType(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (typeId: string) => jobTypesApi.delete(projectId, typeId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['job-types', projectId] });
    },
  });
}
