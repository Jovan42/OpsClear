import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { statsApi } from '../../api/stats';
import { useCurrentOrg } from '../org/OrgContext';
import type { StatsWeeks } from '../../types';

/** The viewer's IANA zone, so the backend buckets weeks on the viewer's calendar (ADR-0050). */
function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

export function useProjectStats(projectId: string, weeks: StatsWeeks, memberId?: string) {
  const { hasAddon } = useCurrentOrg();
  const tz = browserTimeZone();
  return useQuery({
    queryKey: ['projects', projectId, 'stats', weeks, tz, memberId],
    queryFn: () => statsApi.get(projectId, { weeks, tz, memberId }),
    staleTime: 30 * 1000,
    // Keep the previous range on screen while a new one loads, instead of flashing skeletons.
    placeholderData: keepPreviousData,
    enabled: !!projectId && hasAddon('DASHBOARD'),
  });
}
