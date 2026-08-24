import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import keycloak from './keycloak';
import { E2E_TOKEN_KEY, E2E_REFRESH_TOKEN_KEY, E2E_ID_TOKEN_KEY } from './e2eAuth';

export interface AuthState {
  ready: boolean;
  authenticated: boolean;
  initError: boolean;
  userId: string | null;
  email: string | null;
  name: string | null;
  token: string | null;
}

// Exported (not just useAuth()) so the /features interactive-demo infrastructure can
// locally override it within a demo's subtree — the demo simulates a logged-in mock
// user without touching real Keycloak auth. See src/demo/mockAuthState.ts.
// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthState>({
  ready: false,
  authenticated: false,
  initError: false,
  userId: null,
  email: null,
  name: null,
  token: null,
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    ready: false,
    authenticated: false,
    initError: false,
    userId: null,
    email: null,
    name: null,
    token: null,
  });

  useEffect(() => {
    // cy.loginAs() (JOB-204/ADR-0049 §3) fetches real tokens directly from Keycloak's
    // token endpoint (Resource Owner Password Credentials grant) and seeds these
    // before the app's scripts run (`cy.visit(url, { onBeforeLoad })`) — initializing
    // with pre-obtained tokens is keycloak-js's own documented mechanism for this,
    // and skips the check-sso iframe/redirect entirely, so a spec doesn't have to
    // drive the real login UI on every run. Not a security bypass: the backend
    // independently verifies the JWT's Keycloak signature on every request regardless
    // of what's in sessionStorage — a forged value here just fails there instead.
    const e2eToken = sessionStorage.getItem(E2E_TOKEN_KEY);
    const initOptions = e2eToken
      ? {
          token: e2eToken,
          refreshToken: sessionStorage.getItem(E2E_REFRESH_TOKEN_KEY) ?? undefined,
          idToken: sessionStorage.getItem(E2E_ID_TOKEN_KEY) ?? undefined,
          checkLoginIframe: false,
        }
      : { onLoad: 'check-sso' as const, checkLoginIframe: false };

    keycloak
      .init(initOptions)
      .then((authenticated) => {
        setState({
          ready: true,
          authenticated,
          initError: false,
          userId: keycloak.subject ?? null,
          email: (keycloak.tokenParsed?.['email'] as string) ?? null,
          name: (keycloak.tokenParsed?.['name'] as string) ?? null,
          token: keycloak.token ?? null,
        });
      })
      .catch(() => {
        setState((prev) => ({ ...prev, ready: true, initError: true }));
      });

    keycloak.onAuthRefreshSuccess = () => {
      setState((prev) => ({ ...prev, token: keycloak.token ?? null }));
    };
  }, []);

  if (!state.ready) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <span className="text-gray-500">Loading…</span>
      </div>
    );
  }

  if (state.initError) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center space-y-3">
          <p className="text-gray-500">Authentication service is temporarily unavailable.</p>
          <button
            onClick={() => window.location.reload()}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            Try again
          </button>
        </div>
      </div>
    );
  }

  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  return useContext(AuthContext);
}
