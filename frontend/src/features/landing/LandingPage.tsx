import { Navigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';

export default function LandingPage() {
  const { authenticated } = useAuth();

  // TODO MIL-012: branch on subscription state → setup wall
  if (authenticated) return <Navigate to="/projects" replace />;

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <p className="text-gray-400">Landing page coming soon.</p>
    </div>
  );
}
