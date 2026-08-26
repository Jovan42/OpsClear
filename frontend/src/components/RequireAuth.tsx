import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { POST_LOGIN_REDIRECT_KEY } from '../auth/postLoginRedirect';

// JOB-237: an unauthenticated deep-link visit gets bounced through Keycloak's real
// login page (a full-page navigation, not client-side routing) before landing back on
// the app — router state doesn't survive that round-trip, so the intended path is
// saved here and read back by LandingPage (the actual post-login landing target,
// see its own `authenticated` redirect) rather than always falling through to the
// app's generic default page.
export default function RequireAuth() {
  const { authenticated } = useAuth();
  const location = useLocation();
  if (!authenticated) {
    sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, location.pathname + location.search);
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
