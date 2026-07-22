import { useTranslation } from 'react-i18next';
import type { JobResponse } from '../../../types';

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export default function BlockedBanner({ job }: { job: JobResponse }) {
  const { t } = useTranslation('jobsComponents');
  if (job.status !== 'BLOCKED') return null;

  return (
    <div className="bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-900 rounded-lg px-4 py-3">
      <div className="flex items-start gap-2">
        <span className="text-red-500 dark:text-red-400 text-base mt-0.5">⚠</span>
        <div>
          <p className="text-sm font-medium text-red-700 dark:text-red-400">
            {t('jobsComponents:blockedBanner.blockedSince', { date: job.blockedAt ? formatDate(job.blockedAt) : '—' })}
          </p>
          {job.blockedReason && (
            <p className="mt-0.5 text-sm text-red-600 dark:text-red-400">"{job.blockedReason}"</p>
          )}
        </div>
      </div>
    </div>
  );
}
