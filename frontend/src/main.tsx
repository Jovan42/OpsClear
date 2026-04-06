import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { AuthProvider } from './auth/AuthContext';
import ErrorBoundary from './components/ErrorBoundary';
import PreferencesProvider from './hooks/PreferencesProvider';
import { OrgProvider } from './features/org/OrgContext';
import { router } from './router';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <PreferencesProvider>
        <AuthProvider>
          <OrgProvider>
            <QueryClientProvider client={queryClient}>
              <RouterProvider router={router} />
              <ReactQueryDevtools initialIsOpen={false} />
            </QueryClientProvider>
          </OrgProvider>
        </AuthProvider>
      </PreferencesProvider>
    </ErrorBoundary>
  </StrictMode>,
);
