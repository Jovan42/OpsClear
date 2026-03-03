import apiClient from './client';
import type { UserSearchResponse } from '../types';

export const usersApi = {
  search: (email: string) =>
    apiClient
      .get<UserSearchResponse[]>('/api/users', { params: { email } })
      .then((r) => r.data),
};
