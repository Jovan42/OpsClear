import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * Minimal wrapper for a demo slide that only needs its own QueryClient — no
 * MemoryRouter/AuthContext/OrgContext override, because the wrapped component takes
 * its data as plain props rather than reading useParams()/useAuth()/useCurrentOrg()
 * itself (e.g. ApprovalList). Prefer this over DemoRouteWrapper whenever the real
 * component doesn't actually need routing.
 */
export default function DemoQueryScope({ children }: Readonly<{ children: ReactNode }>) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 5 * 60 * 1000 } },
  });

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
