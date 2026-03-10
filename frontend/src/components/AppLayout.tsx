import { Outlet, useLocation, useNavigate, NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useProjectRole } from '../features/projects/useProjects';
import { useApprovalQueue } from '../features/approvals/useApprovalQueue';
import UserMenu from './UserMenu';

function ProjectNav({ projectId }: Readonly<{ projectId: string }>) {
  const role = useProjectRole(projectId);
  const { data: pending = [] } = useApprovalQueue(projectId);
  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `text-sm font-medium px-1 py-1 border-b-2 transition-colors ${
      isActive
        ? 'border-white text-white'
        : 'border-transparent text-white/70 hover:text-white'
    }`;

  return (
    <div className="flex items-center gap-5">
      <NavLink to={`/projects/${projectId}/dashboard`} className={linkClass}>
        Dashboard
      </NavLink>
      <NavLink to={`/projects/${projectId}/jobs`} className={linkClass}>
        Jobs
      </NavLink>
      {isOwnerOrAdmin && (
        <NavLink to={`/projects/${projectId}/approvals`} className={linkClass}>
          <span className="flex items-center gap-1.5">
            Approvals
            {pending.length > 0 && (
              <span className="text-xs font-semibold px-1.5 py-0.5 rounded-full bg-orange-500 text-white leading-none">
                {pending.length}
              </span>
            )}
          </span>
        </NavLink>
      )}
      <NavLink to={`/projects/${projectId}/settings`} className={linkClass}>
        Settings
      </NavLink>
    </div>
  );
}

export default function AppLayout() {
  const { name } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const segments = location.pathname.split('/');
  const projectId =
    segments[1] === 'projects' && segments[2] && segments[2].length > 8
      ? segments[2]
      : null;

  const isRoot = location.pathname === '/projects';

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <nav
        className="shrink-0 px-4 text-white"
        style={{ backgroundColor: 'var(--brand)' }}
      >
        {/* Main header row */}
        <div className="flex items-center justify-between h-14">
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

          <div className="flex items-center gap-6">
            {projectId && (
              <div className="hidden md:block">
                <ProjectNav projectId={projectId} />
              </div>
            )}
            <UserMenu name={name ?? ''} />
          </div>
        </div>

        {/* Project nav second row — mobile only */}
        {projectId && (
          <div className="md:hidden overflow-x-auto -mx-4 px-4 pb-2">
            <ProjectNav projectId={projectId} />
          </div>
        )}
      </nav>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
