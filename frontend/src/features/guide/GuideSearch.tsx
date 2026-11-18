import { useId, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Search } from 'lucide-react';
import { usePreferences } from '../../hooks/usePreferences';
import { guidePath } from '../../guide/pages';
import { buildGuideSearch, type GuideSearchResult } from '../../guide/searchIndex';
import { buildIndexState, shouldBuildIndex, type SearchIndexState } from '../../guide/searchLoader';

/**
 * ADR-0062: search over the guide pages in the current language. The index is built in the
 * browser on the first focus of the box (and again after a language change). If it fails to
 * load, the results show an error with a "Try again" button; the next focus also retries.
 */
export default function GuideSearch({ onNavigate }: Readonly<{ onNavigate?: () => void }>) {
  const { t } = useTranslation('guide');
  const { prefs } = usePreferences();
  const navigate = useNavigate();
  const lang = prefs.locale;
  const [indexState, setIndexState] = useState<SearchIndexState>({ status: 'idle' });
  const [query, setQuery] = useState('');
  const inputId = useId();

  const ready = indexState.status === 'ready' && indexState.lang === lang ? indexState.search : null;
  const failed = indexState.status === 'error' && indexState.lang === lang;
  const trimmed = query.trim();
  const results: GuideSearchResult[] = ready && trimmed ? ready(trimmed) : [];

  /** `retryError`: retry a failed build (on focus), not on every keystroke. */
  function ensureIndex(retryError: boolean) {
    if (!shouldBuildIndex(indexState, lang, retryError)) return;
    void buildIndexState(
      lang,
      () => buildGuideSearch(lang, (slug) => t(`pages.${slug}`)),
      // A slower build for a language the user has since switched away from mustn't win.
      (next) => setIndexState((prev) => (prev.status === 'loading' && (next.status === 'ready' || next.status === 'error') && prev.lang !== next.lang ? prev : next)),
    );
  }

  function open(result: GuideSearchResult) {
    setQuery('');
    onNavigate?.();
    navigate(guidePath(result.slug, result.anchor ?? undefined));
  }

  return (
    <div className="relative">
      <label className="sr-only" htmlFor={inputId}>{t('search.label')}</label>
      <Search className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-gray-400" aria-hidden />
      <input
        id={inputId}
        type="search"
        data-testid="guide-search"
        value={query}
        placeholder={t('search.placeholder')}
        autoComplete="off"
        onFocus={() => ensureIndex(true)}
        onChange={(e) => {
          setQuery(e.target.value);
          ensureIndex(false); // the language may have changed since the box was focused
        }}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && results[0]) open(results[0]);
          if (e.key === 'Escape') setQuery('');
        }}
        className="w-full rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 py-2 pl-8 pr-3 text-sm text-gray-900 dark:text-gray-100 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-brand/40"
      />
      {trimmed && (
        <div
          className="absolute left-0 right-0 z-20 mt-1 max-h-80 overflow-y-auto rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-lg"
          data-testid="guide-search-results"
        >
          {failed ? (
            <div className="flex items-center justify-between gap-2 px-3 py-2" data-testid="guide-search-error">
              <p className="text-sm text-red-600 dark:text-red-400">{t('search.error')}</p>
              {/* The box usually still has focus here, so focusing it again can't be the retry. */}
              <button
                type="button"
                onClick={() => ensureIndex(true)}
                className="shrink-0 text-sm font-medium text-brand dark:text-green-400 hover:underline cursor-pointer"
              >
                {t('retry')}
              </button>
            </div>
          ) : !ready ? (
            <p className="px-3 py-2 text-sm text-gray-500 dark:text-gray-400">{t('search.loading')}</p>
          ) : results.length === 0 ? (
            <p className="px-3 py-2 text-sm text-gray-500 dark:text-gray-400">{t('search.noResults', { query: trimmed })}</p>
          ) : (
            <ul>
              {results.map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    onClick={() => open(r)}
                    className="block w-full px-3 py-2 text-left hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
                  >
                    <span className="block text-sm font-medium text-gray-900 dark:text-gray-100">{r.pageTitle}</span>
                    {r.heading && r.heading !== r.pageTitle && (
                      <span className="block text-xs text-gray-500 dark:text-gray-400"># {r.heading}</span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
