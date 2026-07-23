import type { OrgState } from '../features/org/OrgContext';

/**
 * Locally overrides the real OrgContext within a demo's subtree — every addon unlocked,
 * so a prospect can explore the fully-featured product regardless of what's actually
 * purchased (the whole point of ADR-0040's demos). org/subscription are left null since
 * nothing in the wrapped pages reads them directly, only hasAddon().
 */
export const mockOrgState: OrgState = {
  org: null,
  subscription: null,
  hasAddon: () => true,
  setOrg: () => {},
  setSubscription: () => {},
  clearOrg: () => {},
};
