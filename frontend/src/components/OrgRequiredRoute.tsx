import { Navigate, Outlet } from 'react-router-dom';
import { useMyOrg } from '../features/org/useOrganisation';
import { useOrgSubscription } from '../features/org/useSubscription';
import SubscriptionWall from './SubscriptionWall';

export default function OrgRequiredRoute() {
  const { data: org, isLoading: orgLoading } = useMyOrg();
  const { data: subscription, isLoading: subLoading } = useOrgSubscription(org?.id ?? null);

  if (orgLoading || (org && subLoading)) return null;
  if (org === null) return <Navigate to="/onboarding" replace />;
  if (subscription === null) return <SubscriptionWall />;
  return <Outlet />;
}
