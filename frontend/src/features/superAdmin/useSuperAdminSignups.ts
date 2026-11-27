import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { signupsApi } from '../../api/signups';
import { signupCsvFilename } from '../earlyAccess/emailSignup';
import type { SignupList } from '../../types';

const signupsKey = (list: SignupList) => ['superAdmin', 'signups', list];

// 403 (not a super_user) is a permanent state for this session, see useSuperAdminPricing.ts.
export function useSignups(list: SignupList) {
  return useQuery({
    queryKey: signupsKey(list),
    queryFn: () => signupsApi.listSignups(list),
    retry: false,
  });
}

export function useDeleteSignup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => signupsApi.deleteSignup(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['superAdmin', 'signups'] }),
  });
}

/** Fetches the list's CSV with the auth header and saves it through a temporary blob link. */
export async function downloadSignupsCsv(list: SignupList): Promise<void> {
  const { blob, contentDisposition } = await signupsApi.exportSignups(list);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = signupCsvFilename(contentDisposition, list);
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Revoked after the click has handed the blob to the download, not synchronously (Safari)
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
