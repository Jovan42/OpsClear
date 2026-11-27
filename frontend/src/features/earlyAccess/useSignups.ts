import { useMutation, useQuery } from '@tanstack/react-query';
import { signupsApi } from '../../api/signups';
import type { EmailSignupRequest } from '../../types';

export const SIGNUP_STATUS_KEY = ['signups', 'status'];

/**
 * Whether new organisations can be created (ADR-0066). It only changes on launch day with a
 * backend restart, so it's read once per visit: long staleTime and no refetch on focus (the
 * /features demo's service worker would also answer a refetch while a demo is open).
 */
export function useSignupStatus() {
  return useQuery({
    queryKey: SIGNUP_STATUS_KEY,
    queryFn: () => signupsApi.getSignupStatus(),
    staleTime: 30 * 60 * 1000,
    refetchOnWindowFocus: false,
  });
}

export function useEmailSignUp() {
  return useMutation({
    mutationFn: (payload: EmailSignupRequest) => signupsApi.signUp(payload),
  });
}
