import { useEffect, useState } from 'react';
import { Outlet, useLocation, Link } from 'react-router-dom';
import { Toaster } from 'sonner';
import { useAuth } from '../auth/AuthContext';
import { useProject } from '../features/projects/useProjects';
import { useMyOrg } from '../features/org/useOrganisation';
import { useOrgSubscription } from '../features/org/useSubscription';
import { useCurrentOrg } from '../features/org/OrgContext';
import UserMenu from './UserMenu';
import ProjectNavItemView from './ProjectNavItemView';
import { useProjectNavItems, type ProjectNavData } from '../hooks/useProjectNavItems';
import { useTheme } from '../hooks/useTheme';

function ProjectNav({ projectId, navData }: Readonly<{ projectId: string; navData: ProjectNavData }>) {
  const { items, links, canManageLinks } = navData;

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `text-sm font-medium px-1 py-1 border-b-2 transition-colors ${
      isActive
        ? 'border-white text-white'
        : 'border-transparent text-white/70 hover:text-white'
    }`;

  return (
    <div className="flex items-center gap-5">
      {items.map((item) => (
        <ProjectNavItemView
          key={item.key}
          item={item}
          projectId={projectId}
          links={links}
          canManageLinks={canManageLinks}
          linkClassName={linkClass}
        />
      ))}
    </div>
  );
}

function NavDrawer({
  projectId,
  navData,
  open,
  onClose,
}: Readonly<{ projectId: string; navData: ProjectNavData; open: boolean; onClose: () => void }>) {
  const { items, links, canManageLinks } = navData;

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center px-4 py-3 text-sm font-medium rounded-lg transition-colors ${
      isActive
        ? 'bg-[var(--brand)] text-white'
        : 'text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800'
    }`;

  return (
    <div className={`fixed inset-0 z-50 md:hidden ${open ? '' : 'pointer-events-none'}`} aria-hidden={!open}>
      <div
        className={`absolute inset-0 bg-black/40 transition-opacity duration-200 ${open ? 'opacity-100' : 'opacity-0'}`}
        onClick={onClose}
      />
      <div
        className={`absolute inset-y-0 left-0 w-72 max-w-[85vw] bg-white dark:bg-gray-900 shadow-xl overflow-y-auto
          transition-transform duration-200 ease-out ${open ? 'translate-x-0' : '-translate-x-full'}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 h-14 border-b border-gray-200 dark:border-gray-700">
          <span className="font-semibold text-gray-900 dark:text-gray-100">Menu</span>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 transition-colors text-xl leading-none cursor-pointer"
          >
            ×
          </button>
        </div>
        <div className="flex flex-col gap-1 p-3">
          {items.map((item) => (
            <ProjectNavItemView
              key={item.key}
              item={item}
              projectId={projectId}
              links={links}
              canManageLinks={canManageLinks}
              linkClassName={linkClass}
              linksDropdownClassName="mt-2 w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 p-3 space-y-2"
              onNavigate={onClose}
            />
          ))}
        </div>
      </div>
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
  const { setOrg, setSubscription, clearOrg } = useCurrentOrg();
  const { data: myOrg } = useMyOrg();
  const { data: sub } = useOrgSubscription(myOrg?.id ?? null);

  useEffect(() => {
    if (myOrg) setOrg(myOrg);
    else if (myOrg === null) clearOrg();
  }, [myOrg, setOrg, clearOrg]);

  useEffect(() => {
    if (sub !== undefined) setSubscription(sub);
  }, [sub, setSubscription]);

  return null;
}

export default function AppLayout() {
  useTheme();
  const { name } = useAuth();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const segments = location.pathname.split('/');
  const projectId =
    segments[1] === 'projects' && segments[2]
      ? segments[2]
      : null;

  // Called unconditionally (empty string when there's no project route) so this and
  // ProjectNav/NavDrawer below share one subscription instead of tripling queries.
  const navData = useProjectNavItems(projectId ?? '');
  const showApprovalsBadge = navData.isOwnerOrAdmin && navData.pendingApprovalsCount > 0;

  return (
    <div className="min-h-screen flex flex-col bg-gray-50 dark:bg-gray-900">
      <nav
        className="shrink-0 px-4 text-white"
        style={{ backgroundColor: 'var(--brand)' }}
      >
        {/* Main header row */}
        <div className="flex items-center justify-between h-14">
          <div className="flex items-center gap-2 min-w-0">
            {projectId && (
              <button
                onClick={() => setDrawerOpen(true)}
                className="relative md:hidden -ml-1 p-1.5 text-white/80 hover:text-white transition-colors cursor-pointer"
                aria-label="Open navigation menu"
                aria-expanded={drawerOpen}
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="3" y1="6" x2="21" y2="6" />
                  <line x1="3" y1="12" x2="21" y2="12" />
                  <line x1="3" y1="18" x2="21" y2="18" />
                </svg>
                {showApprovalsBadge && (
                  <span className="absolute top-0.5 right-0.5 w-2 h-2 rounded-full bg-orange-500" />
                )}
              </button>
            )}
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
                <ProjectNav projectId={projectId} navData={navData} />
              </div>
            )}
            <UserMenu name={name ?? ''} />
          </div>
        </div>
      </nav>
      {projectId && (
        <NavDrawer
          projectId={projectId}
          navData={navData}
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
        />
      )}
      <main className="flex-1">
        <OrgLoader />
        <Outlet />
      </main>
      <Toaster
        position="bottom-right"
        theme="system"
        richColors
        toastOptions={{ duration: 5000 }}
      />
    </div>
  );
}
