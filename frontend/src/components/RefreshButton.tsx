import { useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface RefreshButtonProps {
  /** TanStack Query's `dataUpdatedAt` for the page's primary query — 0 before
   *  the first successful fetch, in which case no label is shown yet. */
  lastUpdated: number;
  /** TanStack Query's `isFetching` — drives the spinner/disabled state, covers
   *  both the manual click here and any background refetch (e.g. window focus). */
  isFetching: boolean;
  onRefresh: () => void;
}

function formatAgo(updatedAt: number, t: (key: string, opts?: Record<string, unknown>) => string): string {
  const seconds = Math.floor((Date.now() - updatedAt) / 1000);
  if (seconds < 60) return t('refresh.justNow');
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return t('refresh.minutesAgo', { count: minutes });
  const hours = Math.floor(minutes / 60);
  return t('refresh.hoursAgo', { count: hours });
}

/**
 * ADR-0048: explicit, user-initiated refresh — TanStack Query already refetches
 * on window focus, but an open tab sitting idle while a teammate edits the same
 * data elsewhere goes silently stale with no signal. `isFetching`/`dataUpdatedAt`
 * are read from the caller's own query result, not fetched here, so this stays a
 * dumb presentational control reusable across pages with different data shapes.
 */
export default function RefreshButton({ lastUpdated, isFetching, onRefresh }: Readonly<RefreshButtonProps>) {
  const { t } = useTranslation('shared1');
  // Ticks every 30s purely to re-render and advance the "Xm ago" label — the
  // underlying data isn't refetched by this.
  const [, forceTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => forceTick((n) => n + 1), 30_000);
    return () => clearInterval(id);
  }, []);

  return (
    <div className="flex items-center gap-1.5">
      {lastUpdated > 0 && (
        <span className="text-xs text-gray-400 dark:text-gray-500">
          {t('refresh.lastUpdated', { time: formatAgo(lastUpdated, t) })}
        </span>
      )}
      <button
        onClick={onRefresh}
        disabled={isFetching}
        aria-label={t('refresh.button')}
        className="p-1.5 rounded-lg text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
      >
        <RefreshCw className={`w-4 h-4 ${isFetching ? 'animate-spin' : ''}`} />
      </button>
    </div>
  );
}
