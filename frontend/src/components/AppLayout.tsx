import { useEffect } from 'react';
import { Outlet, useLocation, NavLink, Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useProject, useProjectRole } from '../features/projects/useProjects';
import { useApprovalQueue } from '../features/approvals/useApprovalQueue';
import { useMyOrg } from '../features/org/useOrganisation';
import { useCurrentOrg } from '../features/org/OrgContext';
import UserMenu from './UserMenu';
import { useTheme } from '../hooks/useTheme';

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
      <NavLink to={`/projects/${projectId}/milestones`} className={linkClass}>
        Milestones
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

function ProjectBreadcrumb({ projectId }: Readonly<{ projectId: string }>) {
  const { data: project } = useProject(projectId);
  if (!project) return null;
  return (
    <>
      <span className="text-white/40 text-lg font-light">/</span>
      <Link
        to={`/projects/${projectId}`}
        className="text-sm font-medium text-white/80 hover:text-white transition-colors truncate max-w-[160px] sm:max-w-xs"
      >
        {project.name}
      </Link>
    </>
  );
}

function OrgLoader() {
  const { setOrg, clearOrg } = useCurrentOrg();
  const { data: myOrg } = useMyOrg();

  useEffect(() => {
    if (myOrg) {
      setOrg(myOrg);
    } else if (myOrg === null) {
      clearOrg();
    }
  }, [myOrg, setOrg, clearOrg]);

  return null;
}

export default function AppLayout() {
  useTheme();
  const { name } = useAuth();
  const location = useLocation();

  const segments = location.pathname.split('/');
  const projectId =
    segments[1] === 'projects' && segments[2]
      ? segments[2]
      : null;

  return (
    <div className="min-h-screen flex flex-col bg-gray-50 dark:bg-gray-900">
      <nav
        className="shrink-0 px-4 text-white"
        style={{ backgroundColor: 'var(--brand)' }}
      >
        {/* Main header row */}
        <div className="flex items-center justify-between h-14">
          <div className="flex items-center gap-2 min-w-0">
            <Link
              to="/projects"
              className="font-semibold text-lg tracking-tight text-white hover:text-white/80 transition-colors shrink-0"
            >
              OpsClear
            </Link>
            {projectId && <ProjectBreadcrumb projectId={projectId} />}
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
        <OrgLoader />
        <Outlet />
      </main>
    </div>
  );
}
