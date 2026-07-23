import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AuthContext } from '../auth/AuthContext';
import { OrgContext } from '../features/org/OrgContext';
import PreferencesProvider from '../hooks/PreferencesProvider';
import { mockAuthState } from './mockAuthState';
import { mockOrgState } from './mockOrgState';

interface DemoRouteWrapperProps {
  /** The real route path (e.g. "/projects/:projectFriendlyId/approvals") so the
   *  wrapped page's useParams()/useNavigate() work exactly as they do in production. */
  path: string;
  initialEntry: string;
  children: ReactNode;
}

/**
 * Wraps one real, unmodified page component in a self-contained tree: its own
 * QueryClient (nothing shared with, or leaking into, the real app's cache) and a
 * MemoryRouter matching the real route. Used for every /features demo "slide" (a demo
 * can show more than one real page — e.g. Approvals' pending queue and a job's
 * decided-approval history — navigated via DemoOverlay's prev/next arrows).
 *
 * The caller must give the mounted element a fresh `key` per slide (see
 * DemoTrigger.tsx) — otherwise React treats a slide switch as a prop *update* rather
 * than a remount, and neither the QueryClient nor MemoryRouter's history actually reset.
 */
export default function DemoRouteWrapper({ path, initialEntry, children }: Readonly<DemoRouteWrapperProps>) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 5 * 60 * 1000 } },
  });

  return (
    <QueryClientProvider client={queryClient}>
      <PreferencesProvider>
        <AuthContext.Provider value={mockAuthState}>
          <OrgContext.Provider value={mockOrgState}>
            <MemoryRouter initialEntries={[initialEntry]}>
              <Routes>
                <Route path={path} element={children} />
              </Routes>
            </MemoryRouter>
          </OrgContext.Provider>
        </AuthContext.Provider>
      </PreferencesProvider>
    </QueryClientProvider>
  );
}
