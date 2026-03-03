import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import keycloak from '../auth/keycloak';

export default function AppLayout() {
  const { name } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const isRoot = location.pathname === '/projects';

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <nav
        className="shrink-0 h-14 px-4 flex items-center justify-between text-white"
        style={{ backgroundColor: 'var(--brand)' }}
      >
        <div className="flex items-center gap-2">
          {!isRoot && (
            <button
              onClick={() => navigate(-1)}
              className="flex items-center justify-center w-8 h-8 rounded-lg text-white/70 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
              aria-label="Go back"
            >
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M11 4L6 9L11 14" />
              </svg>
            </button>
          )}
          <span className="font-semibold text-lg tracking-tight">OpsClear</span>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm text-white/80">{name}</span>
          <button
            onClick={() => keycloak.logout()}
            className="text-sm text-white/80 hover:text-white transition-colors cursor-pointer"
          >
            Sign out
          </button>
        </div>
      </nav>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
