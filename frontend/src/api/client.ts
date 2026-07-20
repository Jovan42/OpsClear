import axios from 'axios';
import { toast } from 'sonner';
import keycloak from '../auth/keycloak';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
});

apiClient.interceptors.request.use(async (config) => {
  if (keycloak.authenticated) {
    try {
      await keycloak.updateToken(30);
    } catch {
      keycloak.login();
    }
    if (keycloak.token) {
      config.headers['Authorization'] = `Bearer ${keycloak.token}`;
    }
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status;
      const method = error.config?.method?.toLowerCase();
      // 401 — keycloak handles auth redirects
      // 404 on GET — components handle missing state silently (e.g. deleted schedule)
      if (status === 401) return Promise.reject(error);
      if (status === 404 && method === 'get') return Promise.reject(error);

      const message: string =
        (error.response?.data as { message?: string } | undefined)?.message ??
        error.message ??
        'Something went wrong.';

      toast.error(message);
    } else {
      toast.error('Network error — check your connection.');
    }
    return Promise.reject(error);
  },
);

export default apiClient;
