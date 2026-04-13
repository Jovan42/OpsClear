import { Navigate, Outlet } from 'react-router-dom';
import { useMyOrg } from '../features/org/useOrganisation';

export default function OrgRequiredRoute() {
  const { data: org, isLoading } = useMyOrg();

  if (isLoading) return null;
  if (org === null) return <Navigate to="/onboarding" replace />;
  return <Outlet />;
}
