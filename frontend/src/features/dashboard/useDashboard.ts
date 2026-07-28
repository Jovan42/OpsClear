import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '../../api/dashboard';
import { useCurrentOrg } from '../org/OrgContext';

export function useDashboard(projectId: string) {
  const { hasAddon } = useCurrentOrg();
  return useQuery({
    queryKey: ['dashboard', projectId],
    queryFn: () => dashboardApi.get(projectId),
    staleTime: 30 * 1000,
    enabled: !!projectId && hasAddon('DASHBOARD'),
  });
}
