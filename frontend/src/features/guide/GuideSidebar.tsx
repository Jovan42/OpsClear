import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronDown } from 'lucide-react';
import { GUIDE_PAGES, GUIDE_SECTIONS, guidePath, type GuidePageEntry, type GuideSection } from '../../guide/pages';
import GuideSearch from './GuideSearch';

interface GuideSidebarProps {
  current: GuidePageEntry;
  /** Called after a link is followed, so the mobile drawer can close. */
  onNavigate?: () => void;
}

/** Search plus the page list in its five sections, with the current page and section highlighted. */
export default function GuideSidebar({ current, onNavigate }: Readonly<GuideSidebarProps>) {
  const { t } = useTranslation('guide');
  const [collapsed, setCollapsed] = useState<ReadonlySet<GuideSection>>(new Set());

  function toggle(section: GuideSection) {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(section)) next.delete(section);
      else next.add(section);
      return next;
    });
  }

  return (
    <div className="space-y-5" data-testid="guide-sidebar">
      <GuideSearch onNavigate={onNavigate} />
      <nav aria-label={t('heading')} className="space-y-4">
        {GUIDE_SECTIONS.map((section) => {
          const isCurrentSection = section === current.section;
          const open = !collapsed.has(section);
          const label = t(`sections.${section}`);
          return (
            <div key={section} data-testid={`guide-section-${section}`}>
              <button
                type="button"
                onClick={() => toggle(section)}
                aria-expanded={open}
                aria-label={t('toggleSection', { section: label })}
                className={`flex w-full items-center justify-between text-xs font-semibold uppercase tracking-wide cursor-pointer ${
                  isCurrentSection ? 'text-brand dark:text-green-400' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
                }`}
              >
                <span>{label}</span>
                <ChevronDown className={`h-4 w-4 transition-transform ${open ? '' : '-rotate-90'}`} aria-hidden />
              </button>
              {open && (
                <ul className="mt-2 space-y-0.5 border-l border-gray-200 dark:border-gray-700">
                  {GUIDE_PAGES.filter((p) => p.section === section).map((p) => {
                    const active = p.slug === current.slug;
                    return (
                      <li key={p.slug}>
                        <Link
                          to={guidePath(p.slug)}
                          onClick={onNavigate}
                          aria-current={active ? 'page' : undefined}
                          className={`-ml-px block border-l-2 py-1 pl-3 text-sm transition-colors ${
                            active
                              ? 'border-brand font-medium text-brand dark:border-green-400 dark:text-green-400'
                              : 'border-transparent text-gray-600 dark:text-gray-300 hover:border-gray-300 hover:text-gray-900 dark:hover:text-white'
                          }`}
                        >
                          {t(p.titleKey)}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          );
        })}
      </nav>
    </div>
  );
}
