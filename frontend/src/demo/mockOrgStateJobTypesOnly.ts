import type { OrgState } from '../features/org/OrgContext';

/**
 * Only JOB_TYPES unlocked — used by the job-types card's second slide (JOB-158),
 * which deliberately shows the plain job list with nothing else enabled (no
 * milestone grouping, no other addon UI) so types/badges/filter are the only thing
 * on screen.
 */
export const mockOrgStateJobTypesOnly: OrgState = {
  org: null,
  subscription: null,
  hasAddon: (key) => key === 'JOB_TYPES',
  setOrg: () => {},
  setSubscription: () => {},
  clearOrg: () => {},
};
