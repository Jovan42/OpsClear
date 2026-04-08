import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { organisationsApi } from '../../api/organisations';
import type { OrgRole } from '../../types';

export function useMyOrg() {
  return useQuery({
    queryKey: ['organisations', 'mine'],
    queryFn: () => organisationsApi.mine(),
  });
}

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
      queryClient.setQueryData(['organisations', 'mine'], data);
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
      queryClient.setQueryData(['organisations', 'mine'], null);
    },
  });
}

export function useOrgMembers(orgId: string | null) {
  return useQuery({
    queryKey: ['organisations', orgId, 'members'],
    queryFn: () => organisationsApi.listMembers(orgId!),
    enabled: !!orgId,
  });
}

export function useAddOrgMember(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { userId: string; role: OrgRole }) =>
      organisationsApi.addMember(orgId, body),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'members'] }),
  });
}

export function useUpdateOrgMemberRole(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: OrgRole }) =>
      organisationsApi.updateMemberRole(orgId, userId, role),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'members'] }),
  });
}

export function useRemoveOrgMember(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => organisationsApi.removeMember(orgId, userId),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'members'] }),
  });
}

export function useOrgInvites(orgId: string | null) {
  return useQuery({
    queryKey: ['organisations', orgId, 'invites'],
    queryFn: () => organisationsApi.listInvites(orgId!),
    enabled: !!orgId,
  });
}

export function useSendOrgInvite(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (email: string) => organisationsApi.sendInvite(orgId, email),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'invites'] }),
  });
}

export function useRevokeOrgInvite(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (inviteId: string) => organisationsApi.revokeInvite(orgId, inviteId),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'invites'] }),
  });
}

export function useAcceptOrgInvite() {
  return useMutation({
    mutationFn: (token: string) => organisationsApi.acceptInvite(token),
  });
}
