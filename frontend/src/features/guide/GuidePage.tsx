import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { MDXProvider } from '@mdx-js/react';
import { Clock, Languages } from 'lucide-react';
import Button from '../../components/Button';
import Skeleton from '../../components/Skeleton';
import { usePageTitle } from '../../hooks/usePageTitle';
import { usePreferences, type Locale } from '../../hooks/usePreferences';
import { guideComponents } from '../../guide/components';
import { loadGuidePage } from '../../guide/content';
import { findGuidePage, GUIDE_INDEX_SLUG, guidePath, type GuidePageEntry } from '../../guide/pages';
import GuideLayout from './GuideLayout';
import GuidePrevNext from './GuidePrevNext';
import OnThisPage from './OnThisPage';
import { readHeadings, type GuideHeading } from './headings';

const LANGS: Locale[] = ['en', 'sr'];

const PROSE =
  'prose dark:prose-invert max-w-none prose-headings:scroll-mt-6 prose-headings:font-semibold ' +
  'prose-h1:text-3xl prose-a:text-brand dark:prose-a:text-green-400 prose-a:no-underline hover:prose-a:underline ' +
  // Headings are wrapped in a link to themselves (rehype-autolink-headings): keep them heading-coloured.
  '[&_:is(h1,h2,h3,h4)_a]:text-inherit [&_:is(h1,h2,h3,h4)_a]:no-underline';

/** Changes the app language preference, the same setting as in Settings. */
function LanguageSwitch() {
  const { t } = useTranslation('guide');
  const { prefs, update } = usePreferences();
  return (
    <div className="flex items-center gap-2" role="group" aria-label={t('language')}>
      <Languages className="h-4 w-4 text-gray-400" aria-hidden />
      <div className="flex overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
        {LANGS.map((lang) => (
          <button
            key={lang}
            type="button"
            aria-pressed={prefs.locale === lang}
            data-testid={`guide-lang-${lang}`}
            onClick={() => update({ locale: lang })}
            className={`px-2.5 py-1 text-xs font-semibold cursor-pointer transition-colors ${
              prefs.locale === lang
                ? 'bg-brand text-white'
                : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
            }`}
          >
            {lang.toUpperCase()}
          </button>
        ))}
      </div>
    </div>
  );
}

function ComingSoon({ page }: Readonly<{ page: GuidePageEntry }>) {
  const { t } = useTranslation('guide');
  return (
    <div data-testid="guide-coming-soon">
      <h1 className="text-3xl font-semibold text-gray-900 dark:text-gray-100">{t(page.titleKey)}</h1>
      <div className="mt-6 flex gap-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 px-4 py-4">
        <Clock className="mt-0.5 h-5 w-5 shrink-0 text-gray-400" aria-hidden />
        <div>
          <p className="font-medium text-gray-900 dark:text-gray-100">{t('comingSoon.title')}</p>
          <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{t('comingSoon.body')}</p>
          <Link to={guidePath(GUIDE_INDEX_SLUG)} className="mt-2 inline-block text-sm font-medium text-brand dark:text-green-400 hover:underline">
            {t('pages.index')}
          </Link>
        </div>
      </div>
    </div>
  );
}

function GuideArticle({ page }: Readonly<{ page: GuidePageEntry }>) {
  const { t } = useTranslation('guide');
  const { prefs } = usePreferences();
  const { hash } = useLocation();
  const lang = prefs.locale;
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['guide', lang, page.slug],
    queryFn: () => loadGuidePage(lang, page.slug),
    staleTime: Infinity,
  });

  usePageTitle(data?.frontmatter?.title ?? t(page.titleKey), t('title'));

  // Headings are read from the rendered page (rehype-slug ids), keyed by the page shown, so
  // a page without content never shows the previous page's list.
  const contentKey = data ? `${data.lang}/${page.slug}` : null;
  const [headings, setHeadings] = useState<{ key: string; items: GuideHeading[] } | null>(null);
  const articleRef = useCallback(
    (el: HTMLElement | null) => {
      if (el && contentKey) setHeadings({ key: contentKey, items: readHeadings(el) });
    },
    [contentKey],
  );
  const currentHeadings = headings && headings.key === contentKey ? headings.items : [];

  // Client-side navigation doesn't scroll by itself, and the content loads after the
  // browser's own jump to the hash: go to the anchor, or to the top of a new page.
  useEffect(() => {
    if (isLoading) return;
    const target = hash ? document.getElementById(decodeURIComponent(hash.slice(1))) : null;
    if (target) target.scrollIntoView({ block: 'start' });
    else window.scrollTo({ top: 0 });
  }, [isLoading, contentKey, hash]);

  return (
    <GuideLayout current={page} aside={<OnThisPage headings={currentHeadings} />}>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm font-medium text-brand dark:text-green-400">{t(`sections.${page.section}`)}</p>
        <LanguageSwitch />
      </div>

      {isLoading ? (
        <div className="space-y-4" aria-busy>
          <Skeleton className="h-9 w-2/3" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-5/6" />
          <Skeleton className="h-4 w-4/6" />
        </div>
      ) : isError ? (
        <div className="py-12 text-center">
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('loadError')}</p>
          <div className="mt-4">
            <Button variant="secondary" onClick={() => void refetch()}>
              {t('retry')}
            </Button>
          </div>
        </div>
      ) : data ? (
        <>
          {data.lang !== lang && (
            <p
              className="mb-6 rounded-lg bg-amber-50 dark:bg-amber-900/20 px-3 py-2 text-sm text-amber-800 dark:text-amber-300"
              data-testid="guide-not-translated"
            >
              {t('notYetTranslated')}
            </p>
          )}
          <article key={contentKey} ref={articleRef} className={PROSE} lang={data.lang} data-testid="guide-article">
            <MDXProvider components={guideComponents}>
              <data.Content />
            </MDXProvider>
          </article>
        </>
      ) : (
        <ComingSoon page={page} />
      )}

      <GuidePrevNext current={page} />
    </GuideLayout>
  );
}

/** `/guide` and `/guide/:slug` (ADR-0062). Public: no login needed. */
export default function GuidePage() {
  const { slug } = useParams();
  const page = findGuidePage(slug ?? GUIDE_INDEX_SLUG);
  // Unknown slugs, and `/guide/index` (the index lives at `/guide`), go to the index.
  if (!page || slug === GUIDE_INDEX_SLUG) return <Navigate to={guidePath(GUIDE_INDEX_SLUG)} replace />;
  return <GuideArticle page={page} />;
}
