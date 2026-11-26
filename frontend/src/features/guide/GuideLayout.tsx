import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Menu, X } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import AppHeader from '../../components/AppHeader';
import OrgLoader from '../../components/OrgLoader';
import PublicNav from '../../components/PublicNav';
import SupportLink from '../../components/SupportLink';
import { useTheme } from '../../hooks/useTheme';
import type { GuidePageEntry } from '../../guide/pages';
import GuideSidebar from './GuideSidebar';

interface GuideLayoutProps {
  current: GuidePageEntry;
  /** The right column ("On this page"), desktop only. */
  aside?: ReactNode;
  children: ReactNode;
}

/**
 * ADR-0062: the guide's docs layout. The public nav when logged out, the app header when
 * logged in; the page list on the left (a drawer on mobile), the page in the centre and
 * "On this page" on the right.
 */
export default function GuideLayout({ current, aside, children }: Readonly<GuideLayoutProps>) {
  useTheme();
  const { t } = useTranslation('guide');
  const { authenticated } = useAuth();
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <div className="min-h-screen flex flex-col bg-white dark:bg-gray-900">
      {authenticated ? (
        <>
          <OrgLoader />
          <AppHeader />
        </>
      ) : (
        <PublicNav />
      )}

      <div className="flex-1 w-full max-w-7xl mx-auto flex">
        <aside className="hidden md:block w-64 shrink-0 border-r border-gray-200 dark:border-gray-800">
          <div className="sticky top-0 max-h-screen overflow-y-auto px-4 py-8">
            <GuideSidebar current={current} />
          </div>
        </aside>

        <main className="flex-1 min-w-0 px-4 sm:px-8 py-6 md:py-8">
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            aria-label={t('openMenu')}
            aria-expanded={drawerOpen}
            data-testid="guide-menu-button"
            className="md:hidden mb-4 inline-flex items-center gap-2 rounded-lg border border-gray-200 dark:border-gray-700 px-3 py-1.5 text-sm text-gray-700 dark:text-gray-200 cursor-pointer"
          >
            <Menu className="h-4 w-4" aria-hidden />
            {t(`sections.${current.section}`)}
          </button>
          {children}
        </main>

        {aside && (
          <aside className="hidden lg:block w-56 shrink-0">
            <div className="sticky top-0 max-h-screen overflow-y-auto px-4 py-8">{aside}</div>
          </aside>
        )}
      </div>

      {!authenticated && (
        <footer className="shrink-0 border-t border-gray-200 dark:border-gray-800 px-6 py-6">
          <SupportLink />
        </footer>
      )}

      {/* `inert` while closed: the off-screen close button mustn't take keyboard focus. */}
      <div className={`fixed inset-0 z-50 md:hidden ${drawerOpen ? '' : 'pointer-events-none'}`} aria-hidden={!drawerOpen} inert={!drawerOpen}>
        <div
          className={`absolute inset-0 bg-black/40 transition-opacity duration-200 ${drawerOpen ? 'opacity-100' : 'opacity-0'}`}
          onClick={() => setDrawerOpen(false)}
        />
        <div
          className={`absolute inset-y-0 left-0 w-72 max-w-[85vw] overflow-y-auto bg-white dark:bg-gray-900 shadow-xl transition-transform duration-200 ease-out ${
            drawerOpen ? 'translate-x-0' : '-translate-x-full'
          }`}
        >
          <div className="flex h-14 items-center justify-between border-b border-gray-200 dark:border-gray-700 px-4">
            <span className="font-semibold text-gray-900 dark:text-gray-100">{t('heading')}</span>
            <button
              type="button"
              onClick={() => setDrawerOpen(false)}
              aria-label={t('closeMenu')}
              className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 cursor-pointer"
            >
              <X className="h-5 w-5" aria-hidden />
            </button>
          </div>
          <div className="p-4">
            {/* Only mounted while open, so its search and links aren't in the page twice. */}
            {drawerOpen && <GuideSidebar current={current} onNavigate={() => setDrawerOpen(false)} />}
          </div>
        </div>
      </div>
    </div>
  );
}
