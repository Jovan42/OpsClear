import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '../../api/dashboard';

export function useDashboard(projectId: string) {
  return useQuery({
    queryKey: ['dashboard', projectId],
    queryFn: () => dashboardApi.get(projectId),
    staleTime: 30 * 1000,
    enabled: !!projectId,
  });
}
