import apiClient from './client';
import type { EmailSignupRequest, EmailSignupResponse, SignupList, SignupStatusResponse } from '../types';

/** ADR-0066: email sign-up lists (public form) and their super-admin view. */
export const signupsApi = {
  /** 204 for a new, duplicate or honeypot sign-up alike. The form shows its own errors, so no toast. */
  signUp: (payload: EmailSignupRequest) =>
    apiClient.post<void>('/api/public/signups', payload, { skipErrorToast: true }).then(() => undefined),

  getSignupStatus: () =>
    apiClient.get<SignupStatusResponse>('/api/public/signup').then((r) => r.data),

  listSignups: (list: SignupList) =>
    apiClient
      .get<EmailSignupResponse[]>('/api/super-admin/signups', { params: { list } })
      .then((r) => r.data),

  /** The CSV as a blob (fetched with the auth header, so a plain link can't do it), plus the
   *  server's Content-Disposition when the browser may read it (same origin on prod). */
  exportSignups: (list: SignupList) =>
    apiClient
      .get<Blob>('/api/super-admin/signups/export', { params: { list }, responseType: 'blob' })
      .then((r) => ({
        blob: r.data,
        contentDisposition: (r.headers['content-disposition'] as string | undefined) ?? null,
      })),

  deleteSignup: (id: string) => apiClient.delete<void>(`/api/super-admin/signups/${id}`).then(() => undefined),
};
