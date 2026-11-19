import apiClient from './client';
import type { UserSearchResponse } from '../types';

export const usersApi = {
  /** Members of the caller's organisation whose name contains, or email starts with, `q` (min 2 chars, max 10 results). */
  search: (q: string) =>
    apiClient
      .get<UserSearchResponse[]>('/api/users', { params: { q } })
      .then((r) => r.data),
};
