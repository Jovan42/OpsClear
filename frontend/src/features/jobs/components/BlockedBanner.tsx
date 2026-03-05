import type { JobResponse } from '../../../types';

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export default function BlockedBanner({ job }: { job: JobResponse }) {
  if (job.status !== 'BLOCKED') return null;

  return (
    <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3">
      <div className="flex items-start gap-2">
        <span className="text-red-500 text-base mt-0.5">⚠</span>
        <div>
          <p className="text-sm font-medium text-red-700">
            Blocked since {job.blockedAt ? formatDate(job.blockedAt) : '—'}
          </p>
          {job.blockedReason && (
            <p className="mt-0.5 text-sm text-red-600">"{job.blockedReason}"</p>
          )}
        </div>
      </div>
    </div>
  );
}
