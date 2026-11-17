import {
  useMutation,
  useMutationState,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { organisationsApi } from '../../api/organisations';
import { retryAfterMinutes } from './inviteEmail';
import type { InviteLanguage, OrgRole } from '../../types';

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

export function useProjectDirectory(orgId: string | null) {
  return useQuery({
    queryKey: ['organisations', orgId, 'projects', 'directory'],
    queryFn: () => organisationsApi.projectDirectory(orgId!),
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
    mutationFn: ({ email, language }: { email: string; language: InviteLanguage }) =>
      organisationsApi.sendInvite(orgId, email, language),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'invites'] }),
  });
}

export const resendInviteKey = (orgId: string) => ['organisations', orgId, 'invites', 'resend'];

export interface ResendInviteVariables {
  inviteId: string;
  language: InviteLanguage;
}

/** What the page shows for a resend. Other errors get the API client's generic toast. */
export interface ResendInviteFeedback {
  sent: () => void;
  /** A 429 inside the cooldown; minutes is null when `Retry-After` can't be read. */
  cooldown: (minutes: number | null) => void;
}

/**
 * Mutation options for resending an invite email. Exported for the unit tests; components
 * use `useResendInvite`. The feedback lives here, not in `mutate()` callbacks: TanStack
 * Query v5 only runs those for the latest call, so a second resend started before the
 * first returns would drop the first one's toast.
 */
export function resendInviteMutationOptions(
  queryClient: QueryClient,
  orgId: string,
  feedback: ResendInviteFeedback,
) {
  return {
    mutationKey: resendInviteKey(orgId),
    mutationFn: ({ inviteId, language }: ResendInviteVariables) =>
      organisationsApi.resendInvite(orgId, inviteId, language),
    onSuccess: () => feedback.sent(),
    onError: (err: unknown) => {
      // The client skips its toast for this 429 (skipErrorToastStatuses), so this is the only one
      if (isAxiosError(err) && err.response?.status === 429) {
        feedback.cooldown(retryAfterMinutes(err.response.headers['retry-after']));
      }
    },
    // Also after an error: a 409 means the invite is no longer pending, so its row should go
    onSettled: () =>
      void queryClient.invalidateQueries({ queryKey: ['organisations', orgId, 'invites'] }),
  };
}

export function useResendInvite(orgId: string, feedback: ResendInviteFeedback) {
  const queryClient = useQueryClient();
  return useMutation(resendInviteMutationOptions(queryClient, orgId, feedback));
}

/** IDs of the invites with a resend request running, one entry per running request. */
export function useResendingInviteIds(orgId: string): string[] {
  return useMutationState({
    filters: { mutationKey: resendInviteKey(orgId), status: 'pending' },
    select: (mutation) => (mutation.state.variables as ResendInviteVariables).inviteId,
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
