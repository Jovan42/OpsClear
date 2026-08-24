import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OrgContext } from '../features/org/OrgContext';
import { mockOrgState } from './mockOrgState';

/**
 * Minimal wrapper for a demo slide that only needs its own QueryClient — no
 * MemoryRouter/AuthContext override, because the wrapped component takes its data as
 * plain props rather than reading useParams()/useAuth() itself (e.g. ApprovalList).
 * Prefer this over DemoRouteWrapper whenever the real component doesn't actually need
 * routing.
 *
 * Still provides OrgContext (mockOrgState — every addon unlocked), even though the
 * wrapped component itself doesn't read useCurrentOrg(): several data hooks it calls
 * internally do, for their own `enabled: hasAddon(...)` guard (JOB-181). Without a
 * provider here, useCurrentOrg() falls back to OrgContext's default value
 * (`hasAddon: () => false`), silently disabling the query — the demo renders with no
 * data and no error (JOB-199: hit this for useNotes/useJobHistory/useApprovals).
 */
export default function DemoQueryScope({ children }: Readonly<{ children: ReactNode }>) {
  const queryClient = new QueryClient({
    // Mutations need `retry: false` too, not just queries — a retried create/update
    // request hits the mock handler again and creates another real record (this is
    // what caused "creating one milestone/job/note creates several").
    defaultOptions: { queries: { retry: false, staleTime: 5 * 60 * 1000 }, mutations: { retry: false } },
  });

  return (
    <QueryClientProvider client={queryClient}>
      <OrgContext.Provider value={mockOrgState}>{children}</OrgContext.Provider>
    </QueryClientProvider>
  );
}
