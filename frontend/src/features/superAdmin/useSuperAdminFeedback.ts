import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { superAdminFeedbackApi } from '../../api/superAdminFeedback';
import { creditsApi } from '../../api/credits';

// 403 (not a super_user) is a permanent state for this session — see
// useSuperAdminPricing.ts for the same reasoning.
export function useFeedbackSubmissions() {
  return useQuery({
    queryKey: ['superAdmin', 'feedback'],
    queryFn: () => superAdminFeedbackApi.listAll(),
    retry: false,
  });
}

export function useSuperAdminOrganisations() {
  return useQuery({
    queryKey: ['superAdmin', 'organisations'],
    queryFn: () => creditsApi.listOrganisations(),
    retry: false,
  });
}

export function useOrgCreditLedger(orgId: string | null) {
  return useQuery({
    queryKey: ['superAdmin', 'organisations', orgId, 'credits'],
    queryFn: () => creditsApi.getLedger(orgId!),
    enabled: !!orgId,
    retry: false,
  });
}

export function useDeclineFeedback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => superAdminFeedbackApi.decline(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['superAdmin', 'feedback'] }),
  });
}

export function useGrantCredit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { orgId: string; amount: number; reason: string; submissionId?: string }) =>
      creditsApi.grant(body),
    onSuccess: (_, vars) => {
      void queryClient.invalidateQueries({ queryKey: ['superAdmin', 'feedback'] });
      void queryClient.invalidateQueries({ queryKey: ['superAdmin', 'organisations', vars.orgId, 'credits'] });
    },
  });
}
