import { Navigate } from 'react-router-dom';
import { usePreferences } from '../hooks/usePreferences';

const PAGE_PATHS = { DASHBOARD: 'dashboard', JOBS: 'jobs', APPROVALS: 'approvals' } as const;

export default function ProjectRedirect() {
  const { prefs } = usePreferences();
  return <Navigate to={PAGE_PATHS[prefs.defaultProjectPage]} replace />;
}
