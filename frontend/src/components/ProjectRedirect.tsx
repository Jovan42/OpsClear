import { Navigate } from 'react-router-dom';
import { usePreferences } from '../hooks/usePreferences';
import { PROJECT_PAGE_PATHS } from '../utils/projectPage';

export default function ProjectRedirect() {
  const { prefs } = usePreferences();
  return <Navigate to={PROJECT_PAGE_PATHS[prefs.defaultProjectPage]} replace />;
}
