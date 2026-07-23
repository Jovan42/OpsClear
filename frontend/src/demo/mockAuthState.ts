import type { AuthState } from '../auth/AuthContext';
import { DEMO_CURRENT_USER } from './mockData';

/**
 * The demo's fixed "you are logged in as..." persona (the project owner) — locally
 * overrides the real AuthContext within a demo's subtree via AuthContext.Provider.
 * Never touches real Keycloak; the rest of the app outside the demo is unaffected.
 */
export const mockAuthState: AuthState = {
  ready: true,
  authenticated: true,
  initError: false,
  userId: DEMO_CURRENT_USER.id,
  email: DEMO_CURRENT_USER.email,
  name: DEMO_CURRENT_USER.name,
  token: 'demo-token',
};
