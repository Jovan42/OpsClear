import { createContext, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import type { AddonCode, OrgSubscriptionResponse, OrganisationResponse } from '../../types';

interface OrgState {
  org: OrganisationResponse | null;
  subscription: OrgSubscriptionResponse | null;
  hasAddon: (key: AddonCode) => boolean;
  setOrg: (org: OrganisationResponse) => void;
  setSubscription: (sub: OrgSubscriptionResponse | null) => void;
  clearOrg: () => void;
}

const OrgContext = createContext<OrgState>({
  org: null,
  subscription: null,
  hasAddon: () => false,
  setOrg: () => {},
  setSubscription: () => {},
  clearOrg: () => {},
});

export function OrgProvider({ children }: { children: ReactNode }) {
  const [org, setOrgState] = useState<OrganisationResponse | null>(null);
  const [subscription, setSubscriptionState] = useState<OrgSubscriptionResponse | null>(null);

  function setOrg(next: OrganisationResponse) {
    setOrgState(next);
  }

  function setSubscription(next: OrgSubscriptionResponse | null) {
    setSubscriptionState(next);
  }

  function hasAddon(key: AddonCode): boolean {
    if (subscription?.internal) return true;
    return subscription?.addons.some((a) => a.key === key) ?? false;
  }

  function clearOrg() {
    setOrgState(null);
    setSubscriptionState(null);
  }

  return (
    <OrgContext.Provider value={{ org, subscription, hasAddon, setOrg, setSubscription, clearOrg }}>
      {children}
    </OrgContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCurrentOrg(): OrgState {
  return useContext(OrgContext);
}
