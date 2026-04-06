import { useState } from 'react';
import { useJobHistory } from '../useJobs';
import { formatDuration } from '../utils/statusHistoryUtils';
import type { JobHistoryEntry } from '../../../types';

function transitionLabel(entry: JobHistoryEntry): string {
  if (entry.changedFrom === null) return `Created as ${entry.changedTo}`;
  return `${entry.changedFrom} → ${entry.changedTo}`;
}

interface Props {
  projectId: string;
  jobId: string;
}

export default function StatusHistory({ projectId, jobId }: Props) {
  const [expanded, setExpanded] = useState(false);
  const { data: entries = [], isLoading } = useJobHistory(projectId, jobId);

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
      <button
        onClick={() => setExpanded((v) => !v)}
        className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
      >
        <div className="flex items-center gap-2">
          <span>Status history</span>
          {!isLoading && entries.length > 0 && (
            <span className="bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300 text-xs font-medium px-1.5 py-0.5 rounded-full">
              {entries.length}
            </span>
          )}
        </div>
        <span className="text-gray-400 dark:text-gray-500">{expanded ? '▲' : '▼'}</span>
      </button>

      {expanded && (
        <div className="px-6 pt-2 pb-4 border-t border-gray-100 dark:border-gray-700">
          {isLoading && (
            <p className="text-sm text-gray-500 dark:text-gray-400 py-2">Loading…</p>
          )}
          {!isLoading && entries.length === 0 && (
            <p className="text-sm text-gray-500 dark:text-gray-400 py-2">No history yet.</p>
          )}
          {!isLoading && entries.length > 0 && (
            <ol className="space-y-0">
              {entries.map((entry, index) => {
                const next = entries[index + 1];
                const durationLabel = next
                  ? `${formatDuration(
                      new Date(entry.changedAt).getTime(),
                      new Date(next.changedAt).getTime(),
                    )} in ${entry.changedTo}`
                  : null;

                return (
                  <li key={entry.id}>
                    <div className="flex items-start gap-3 py-3">
                      <div className="mt-0.5 w-2 h-2 rounded-full bg-gray-300 dark:bg-gray-600 shrink-0 mt-1.5" />
                      <div className="min-w-0 flex-1 space-y-0.5">
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                          {transitionLabel(entry)}
                        </p>
                        <p className="text-xs text-gray-500 dark:text-gray-400">
                          {entry.changedByName ?? 'Unknown'} · {formatDateTime(entry.changedAt)}
                        </p>
                        {entry.blockReason && (
                          <p className="text-xs text-red-600 dark:text-red-400 mt-0.5">
                            Reason: {entry.blockReason}
                          </p>
                        )}
                      </div>
                    </div>
                    {durationLabel && (
                      <p className="text-xs text-gray-400 dark:text-gray-500 pl-5 pb-1 italic">
                        {durationLabel}
                      </p>
                    )}
                  </li>
                );
              })}
            </ol>
          )}
        </div>
      )}
    </div>
  );
}
