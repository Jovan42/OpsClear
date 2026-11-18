import { useEffect } from 'react';
import { useMyOrg } from '../features/org/useOrganisation';
import { useOrgSubscription } from '../features/org/useSubscription';
import { useCurrentOrg } from '../features/org/OrgContext';

/** Loads the signed-in user's org and subscription into OrgContext. Renders nothing. */
export default function OrgLoader() {
  const { setOrg, setSubscription, clearOrg } = useCurrentOrg();
  const { data: myOrg } = useMyOrg();
  const { data: sub } = useOrgSubscription(myOrg?.id ?? null);

  useEffect(() => {
    if (myOrg) setOrg(myOrg);
    else if (myOrg === null) clearOrg();
  }, [myOrg, setOrg, clearOrg]);

  useEffect(() => {
    if (sub !== undefined) setSubscription(sub);
  }, [sub, setSubscription]);

  return null;
}
