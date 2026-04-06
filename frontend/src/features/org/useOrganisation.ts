import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { organisationsApi } from '../../api/organisations';

export function useOrganisation(id: string | null) {
  return useQuery({
    queryKey: ['organisations', id],
    queryFn: () => organisationsApi.get(id!),
    enabled: !!id,
  });
}

export function useCreateOrganisation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; slug: string }) => organisationsApi.create(body),
    onSuccess: (data) => {
      queryClient.setQueryData(['organisations', data.id], data);
    },
  });
}

export function useUpdateOrganisation(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; slug: string }) => organisationsApi.update(id, body),
    onSuccess: (data) => {
      queryClient.setQueryData(['organisations', id], data);
    },
  });
}

export function useDeleteOrganisation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => organisationsApi.delete(id),
    onSuccess: (_, id) => {
      queryClient.removeQueries({ queryKey: ['organisations', id] });
    },
  });
}
