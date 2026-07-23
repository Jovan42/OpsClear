import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import keycloak from './keycloak';

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
    keycloak
      .init({ onLoad: 'check-sso', checkLoginIframe: false })
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
