import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useScheduleMissedRuns, useMaterializeMissedRun, useDismissMissedRun, useDismissAllMissedRuns } from './useSchedules';
import Button from '../../components/Button';
import type { JobResponse } from '../../types';

interface Props {
  projectId: string;
  scheduleId: string;
}

export default function MissedRunsPanel({ projectId, scheduleId }: Readonly<Props>) {
  const [open, setOpen] = useState(false);
  const [createdJob, setCreatedJob] = useState<JobResponse | null>(null);

  const { data: missedRuns = [], isLoading } = useScheduleMissedRuns(projectId, scheduleId);
  const materialize = useMaterializeMissedRun(projectId, scheduleId);
  const dismiss = useDismissMissedRun(projectId, scheduleId);
  const dismissAll = useDismissAllMissedRuns(projectId, scheduleId);
  const navigate = useNavigate();

  if (!isLoading && missedRuns.length === 0 && !open) return null;

  const count = missedRuns.length;

  return (
    <div className="border-t border-amber-200 dark:border-amber-800/40 bg-amber-50/50 dark:bg-amber-900/10">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center gap-2 px-4 py-2 text-left text-xs font-medium text-amber-700 dark:text-amber-400 hover:bg-amber-100/60 dark:hover:bg-amber-900/20 transition-colors"
      >
        <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-amber-500 text-white text-[10px] font-bold shrink-0">
          {count}
        </span>
        {count === 1 ? '1 missed run' : `${count} missed runs`}
        <span className="ml-auto">{open ? '▲' : '▼'}</span>
      </button>

      {open && (
        <div className="px-4 pb-3 space-y-2">
          {count > 1 && (
            <div className="flex justify-end">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => dismissAll.mutate()}
                loading={dismissAll.isPending}
                className="text-xs text-gray-500 dark:text-gray-400"
              >
                Dismiss all
              </Button>
            </div>
          )}

          {createdJob && (
            <div className="flex items-center gap-2 rounded-lg bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 px-3 py-2 text-xs text-green-700 dark:text-green-400">
              <span>Job created: <strong>{createdJob.friendlyId}</strong> — {createdJob.title}</span>
              <button
                type="button"
                onClick={() => navigate(`/projects/${projectId}/jobs/${createdJob.friendlyId}`)}
                className="underline hover:no-underline ml-1"
              >
                View
              </button>
              <button
                type="button"
                onClick={() => setCreatedJob(null)}
                className="ml-auto text-green-500 hover:text-green-700"
                aria-label="Dismiss"
              >
                ✕
              </button>
            </div>
          )}

          {isLoading ? (
            <p className="text-xs text-gray-400 py-2">Loading…</p>
          ) : (
            <ul className="space-y-1">
              {missedRuns.map((run) => (
                <li
                  key={run.id}
                  className="flex items-center justify-between gap-3 rounded-lg bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 px-3 py-2"
                >
                  <span className="text-xs text-gray-600 dark:text-gray-300">
                    {new Date(run.expectedAt).toLocaleString(undefined, {
                      weekday: 'short',
                      month: 'short',
                      day: 'numeric',
                      year: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                  <div className="flex items-center gap-1 shrink-0">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() =>
                        materialize.mutate(run.id, {
                          onSuccess: (job) => setCreatedJob(job),
                        })
                      }
                      loading={materialize.isPending && materialize.variables === run.id}
                      className="text-xs"
                    >
                      Create job
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => dismiss.mutate(run.id)}
                      loading={dismiss.isPending && dismiss.variables === run.id}
                      className="text-xs text-gray-400 hover:text-red-500"
                    >
                      Dismiss
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
