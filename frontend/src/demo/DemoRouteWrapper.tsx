import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AuthContext } from '../auth/AuthContext';
import { OrgContext, type OrgState } from '../features/org/OrgContext';
import { PreferencesContext } from '../hooks/usePreferences';
import { mockAuthState } from './mockAuthState';
import { mockOrgState } from './mockOrgState';
import { mockPreferences } from './mockPreferences';

export interface DemoRoute {
  /** The real route path (e.g. "/projects/:projectFriendlyId/jobs/:jobFriendlyId") so
   *  the wrapped page's useParams()/useNavigate() work exactly as they do in
   *  production. */
  path: string;
  element: ReactNode;
}

interface DemoRouteWrapperProps {
  /** Usually one route (one real page), but a demo whose real component navigates
   *  between pages on its own (e.g. clicking a job row in the job list) needs every
   *  route it can land on registered up front, in the same MemoryRouter instance —
   *  otherwise that in-app navigation has nowhere to go. */
  routes: DemoRoute[];
  initialEntry: string;
  /** Defaults to every addon unlocked (mockOrgState) — override for a demo that
   *  deliberately wants to show the *base* experience with nothing purchased
   *  (e.g. the job-tracking card, whose whole point is "here's what raw job
   *  tracking looks like with no add-ons"). */
  orgState?: OrgState;
}

/**
 * Wraps one or more real, unmodified page components in a self-contained tree: its
 * own QueryClient (nothing shared with, or leaking into, the real app's cache) and a
 * MemoryRouter matching the real route(s). Used for every /features demo "slide" (a
 * demo can show more than one real page — e.g. Approvals' pending queue and a job's
 * decided-approval history — navigated via DemoOverlay's prev/next arrows, or via the
 * wrapped page's own real in-app navigation for demos that register multiple routes).
 *
 * The caller must give the mounted element a fresh `key` per slide (see
 * DemoTrigger.tsx) — otherwise React treats a slide switch as a prop *update* rather
 * than a remount, and neither the QueryClient nor MemoryRouter's history actually reset.
 */
export default function DemoRouteWrapper({
  routes,
  initialEntry,
  orgState = mockOrgState,
}: Readonly<DemoRouteWrapperProps>) {
  const queryClient = new QueryClient({
    // Mutations need `retry: false` too, not just queries — a retried create/update
    // request hits the mock handler again and creates another real record (this is
    // what caused "creating one milestone/job/note creates several").
    defaultOptions: { queries: { retry: false, staleTime: 5 * 60 * 1000 }, mutations: { retry: false } },
  });

  return (
    <QueryClientProvider client={queryClient}>
      <PreferencesContext.Provider value={mockPreferences}>
        <AuthContext.Provider value={mockAuthState}>
          <OrgContext.Provider value={orgState}>
            <MemoryRouter initialEntries={[initialEntry]}>
              <Routes>
                {routes.map((route) => (
                  <Route key={route.path} path={route.path} element={route.element} />
                ))}
              </Routes>
            </MemoryRouter>
          </OrgContext.Provider>
        </AuthContext.Provider>
      </PreferencesContext.Provider>
    </QueryClientProvider>
  );
}
