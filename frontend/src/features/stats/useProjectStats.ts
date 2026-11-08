import { useQuery } from '@tanstack/react-query';
import { statsApi } from '../../api/stats';
import { useCurrentOrg } from '../org/OrgContext';
import type { StatsWeeks } from '../../types';

/** The viewer's IANA zone, so the backend buckets weeks on the viewer's calendar (ADR-0050). */
function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

/**
 * `memberId` filters to one member (Owner/Admin drill-down, ADR-0050). Pass `enabled: false` to hold
 * the request, e.g. until the page knows whether the viewer may use the filter at all.
 */
export function useProjectStats(projectId: string, weeks: StatsWeeks, memberId?: string, enabled = true) {
  const { hasAddon } = useCurrentOrg();
  const tz = browserTimeZone();
  return useQuery({
    queryKey: ['projects', projectId, 'stats', weeks, tz, memberId],
    queryFn: () => statsApi.get(projectId, { weeks, tz, memberId }),
    staleTime: 30 * 1000,
    // Keep the previous range/member on screen while a new one loads, instead of flashing skeletons.
    // Only within the same project: after a project switch, show the skeleton, not the old project's numbers.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === projectId ? previous : undefined,
    enabled: enabled && !!projectId && hasAddon('DASHBOARD'),
  });
}
