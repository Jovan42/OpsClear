import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { GUIDE_PAGES, guidePath, type GuidePageEntry } from '../../guide/pages';

/** Previous/next links, in sidebar order. */
export default function GuidePrevNext({ current }: Readonly<{ current: GuidePageEntry }>) {
  const { t } = useTranslation('guide');
  const i = GUIDE_PAGES.findIndex((p) => p.slug === current.slug);
  const prev = i > 0 ? GUIDE_PAGES[i - 1] : null;
  const next = i < GUIDE_PAGES.length - 1 ? GUIDE_PAGES[i + 1] : null;

  const card =
    'group flex flex-col rounded-xl border border-gray-200 dark:border-gray-700 px-4 py-3 hover:border-brand dark:hover:border-green-500 transition-colors';

  return (
    <nav className="mt-12 grid gap-4 border-t border-gray-200 dark:border-gray-700 pt-6 sm:grid-cols-2" data-testid="guide-prev-next">
      {prev ? (
        <Link to={guidePath(prev.slug)} className={card} data-testid="guide-prev">
          <span className="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
            <ChevronLeft className="h-3.5 w-3.5" aria-hidden />
            {t('previous')}
          </span>
          <span className="mt-1 text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-brand dark:group-hover:text-green-400">
            {t(prev.titleKey)}
          </span>
        </Link>
      ) : (
        <span />
      )}
      {next && (
        <Link to={guidePath(next.slug)} className={`${card} sm:items-end sm:text-right`} data-testid="guide-next">
          <span className="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
            {t('next')}
            <ChevronRight className="h-3.5 w-3.5" aria-hidden />
          </span>
          <span className="mt-1 text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-brand dark:group-hover:text-green-400">
            {t(next.titleKey)}
          </span>
        </Link>
      )}
    </nav>
  );
}
