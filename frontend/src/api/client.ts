import axios from 'axios';
import { toast } from 'sonner';
import keycloak from '../auth/keycloak';
import i18n from '../i18n';

// Keys into the `errors` i18n namespace. Only the coarse category is translated —
// the detailed `message` text stays English (ADR-0037's deliberate scope cut).
const ERROR_CATEGORY_KEYS: Record<string, string> = {
  'Not Found': 'errors:notFound',
  'Conflict': 'errors:conflict',
  'Bad Request': 'errors:badRequest',
  'Validation Error': 'errors:validationError',
  'Forbidden': 'errors:forbidden',
  'Internal Server Error': 'errors:internalServerError',
};

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
      const url = error.config?.url ?? '';
      // 401 — keycloak handles auth redirects
      // 404 on GET — components handle missing state silently (e.g. deleted schedule)
      // 403 on /super-admin/** — the route redirects as if it doesn't exist, so a
      // toast revealing "Forbidden" would defeat that (see SuperAdminPricingPage)
      if (status === 401) return Promise.reject(error);
      if (status === 404 && method === 'get') return Promise.reject(error);
      if (status === 403 && url.includes('/super-admin/')) return Promise.reject(error);

      const data = error.response?.data as { error?: string; message?: string } | undefined;
      const message: string = data?.message ?? error.message ?? i18n.t('errors:somethingWentWrong');
      const categoryKey = data?.error ? ERROR_CATEGORY_KEYS[data.error] : undefined;

      if (categoryKey) {
        toast.error(i18n.t(categoryKey), { description: message });
      } else {
        toast.error(message);
      }
    } else {
      toast.error(i18n.t('errors:networkError'));
    }
    return Promise.reject(error);
  },
);

export default apiClient;
