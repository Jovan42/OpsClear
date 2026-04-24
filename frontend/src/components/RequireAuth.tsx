import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function RequireAuth() {
  const { authenticated } = useAuth();
  if (!authenticated) return <Navigate to="/" replace />;
  return <Outlet />;
}
