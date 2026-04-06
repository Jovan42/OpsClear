import { createContext, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import type { OrganisationResponse } from '../../types';

interface OrgState {
  org: OrganisationResponse | null;
  setOrg: (org: OrganisationResponse) => void;
  clearOrg: () => void;
}

const OrgContext = createContext<OrgState>({
  org: null,
  setOrg: () => {},
  clearOrg: () => {},
});

export function OrgProvider({ children }: { children: ReactNode }) {
  const [org, setOrgState] = useState<OrganisationResponse | null>(null);

  function setOrg(next: OrganisationResponse) {
    setOrgState(next);
  }

  function clearOrg() {
    setOrgState(null);
  }

  return (
    <OrgContext.Provider value={{ org, setOrg, clearOrg }}>
      {children}
    </OrgContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCurrentOrg(): OrgState {
  return useContext(OrgContext);
}
