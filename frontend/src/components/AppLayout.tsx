import { useEffect, useState } from 'react';
import { Outlet, useLocation, Link } from 'react-router-dom';
import { Toaster } from 'sonner';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { useMyOrg } from '../features/org/useOrganisation';
import { useOrgSubscription } from '../features/org/useSubscription';
import { useCurrentOrg } from '../features/org/OrgContext';
import UserMenu from './UserMenu';
import ProjectSwitcher from './ProjectSwitcher';
import ProjectNavItemView from './ProjectNavItemView';
import { useProjectNavItems, type ProjectNavData } from '../hooks/useProjectNavItems';
import { useTheme } from '../hooks/useTheme';
import { recordProjectVisit } from '../hooks/useRecentProjects';

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
  const { t } = useTranslation('shared1');

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
          <span className="font-semibold text-gray-900 dark:text-gray-100">{t('menu')}</span>
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

// App-wide, not buried in org settings (JOB-179) — a subscription already went
// past due should be visible wherever the user happens to be, not just on the one
// page where they'd think to look for it.
function PastDueBanner() {
  const { subscription } = useCurrentOrg();
  const { t } = useTranslation('shared1');

  if (subscription?.subscriptionStatus !== 'PAST_DUE') return null;

  return (
    <div className="shrink-0 bg-amber-50 dark:bg-amber-900/20 border-b border-amber-200 dark:border-amber-800 px-4 py-2 text-sm text-amber-800 dark:text-amber-300 text-center">
      {t('pastDueBannerText')}{' '}
      <Link to="/org/settings" className="font-medium underline hover:no-underline">
        {t('pastDueBannerLink')}
      </Link>
    </div>
  );
}

export default function AppLayout() {
  useTheme();
  const { name } = useAuth();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { t } = useTranslation('shared1');

  const segments = location.pathname.split('/');
  const projectId =
    segments[1] === 'projects' && segments[2]
      ? segments[2]
      : null;

  // ADR-0047: records every project page load for the quick switcher's recency
  // sort — a plain project-id id (not a UUID lookup) is enough, matching what's
  // already in the URL.
  useEffect(() => {
    if (projectId) recordProjectVisit(projectId);
  }, [projectId]);

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
                aria-label={t('openNavMenu')}
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
            {projectId && <ProjectSwitcher projectId={projectId} />}
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
      <PastDueBanner />
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
