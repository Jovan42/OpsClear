import { Navigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import keycloak from '../../auth/keycloak';

export default function LandingPage() {
  const { authenticated } = useAuth();

  // TODO MIL-012: branch on subscription state → setup wall
  if (authenticated) return <Navigate to="/projects" replace />;

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <div className="text-center space-y-4">
        <p className="text-gray-400">Landing page coming soon.</p>
        <button
          onClick={() => keycloak.login()}
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          Log in
        </button>
      </div>
    </div>
  );
}
