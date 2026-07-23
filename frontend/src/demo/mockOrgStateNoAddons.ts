import type { OrgState } from '../features/org/OrgContext';

/**
 * The opposite of mockOrgState — every addon locked, simulating the base plan with
 * nothing purchased. Used by demos whose whole point is showing the *plain*, no-add-on
 * experience (e.g. job tracking), in contrast to every other demo's "everything
 * unlocked" treatment.
 */
export const mockOrgStateNoAddons: OrgState = {
  org: null,
  subscription: null,
  hasAddon: () => false,
  setOrg: () => {},
  setSubscription: () => {},
  clearOrg: () => {},
};
