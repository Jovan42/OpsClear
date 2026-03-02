import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import keycloak from './keycloak';

interface AuthState {
  ready: boolean;
  authenticated: boolean;
  userId: string | null;
  email: string | null;
  name: string | null;
  token: string | null;
}

const AuthContext = createContext<AuthState>({
  ready: false,
  authenticated: false,
  userId: null,
  email: null,
  name: null,
  token: null,
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    ready: false,
    authenticated: false,
    userId: null,
    email: null,
    name: null,
    token: null,
  });

  useEffect(() => {
    keycloak
      .init({ onLoad: 'login-required', checkLoginIframe: false })
      .then((authenticated) => {
        setState({
          ready: true,
          authenticated,
          userId: keycloak.subject ?? null,
          email: (keycloak.tokenParsed?.['email'] as string) ?? null,
          name: (keycloak.tokenParsed?.['name'] as string) ?? null,
          token: keycloak.token ?? null,
        });
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

  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  return useContext(AuthContext);
}
