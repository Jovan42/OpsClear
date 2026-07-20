import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useSchedules, useDeleteSchedule, usePauseSchedule, useResumeSchedule } from './useSchedules';
import { useProject } from '../projects/useProjects';
import { useCurrentOrg } from '../org/OrgContext';
import { usePageTitle } from '../../hooks/usePageTitle';
import { toReadable } from '../../utils/cron';
import ScheduleFormModal from './ScheduleFormModal';
import MissedRunsPanel from './MissedRunsPanel';
import PauseDialog from './PauseDialog';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import UpgradeCard from '../../components/UpgradeCard';
import PageError from '../../components/PageError';
import type { RecurringScheduleResponse, ScheduleStatus } from '../../types';

function StatusBadge({ status }: Readonly<{ status: ScheduleStatus }>) {
  const map: Record<ScheduleStatus, { label: string; cls: string }> = {
    ACTIVE: { label: 'Active', cls: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
    PAUSED: { label: 'Paused', cls: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400' },
    PAUSED_NO_ASSIGNEES: {
      label: 'No assignees',
      cls: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    },
    EXPIRED: { label: 'Expired', cls: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400' },
  };
  const { label, cls } = map[status];
  return <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${cls}`}>{label}</span>;
}

function AssigneeAvatars({ assignees }: Readonly<{ assignees: RecurringScheduleResponse['assignees'] }>) {
  const sorted = [...assignees].sort((a, b) => a.order - b.order).slice(0, 4);
  return (
    <div className="flex -space-x-1">
      {sorted.map((a) => (
        <div
          key={a.userId}
          title={a.userName}
          className="w-6 h-6 rounded-full border-2 border-white dark:border-gray-800 flex items-center justify-center text-xs font-semibold text-white"
          style={{ backgroundColor: 'var(--brand)', opacity: 0.75 }}
        >
          {a.userName.charAt(0).toUpperCase()}
        </div>
      ))}
      {assignees.length > 4 && (
        <div className="w-6 h-6 rounded-full border-2 border-white dark:border-gray-800 bg-gray-200 dark:bg-gray-600 flex items-center justify-center text-xs text-gray-600 dark:text-gray-300">
          +{assignees.length - 4}
        </div>
      )}
    </div>
  );
}

interface ScheduleRowProps {
  schedule: RecurringScheduleResponse;
  projectId: string;
  onEdit: (s: RecurringScheduleResponse) => void;
  onDelete: (s: RecurringScheduleResponse) => void;
  onPause: (s: RecurringScheduleResponse) => void;
  onResume: (s: RecurringScheduleResponse) => void;
  isResuming: boolean;
}

function ScheduleRow({
  schedule,
  projectId,
  onEdit,
  onDelete,
  onPause,
  onResume,
  isResuming,
}: Readonly<ScheduleRowProps>) {
  const isPaused = schedule.status === 'PAUSED' || schedule.status === 'PAUSED_NO_ASSIGNEES';

  return (
    <>
    <div className="flex items-start justify-between gap-4 p-4">
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex items-center gap-2 flex-wrap">
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{schedule.name}</p>
          <StatusBadge status={schedule.status} />
        </div>
        {schedule.templateName && (
          <p className="text-xs text-gray-500 dark:text-gray-400">Template: {schedule.templateName}</p>
        )}
        <p className="text-xs text-gray-500 dark:text-gray-400">{toReadable(schedule.cronExpression)}</p>
        <div className="flex items-center gap-4 text-xs text-gray-400 dark:text-gray-500">
          <span>
            Next:{' '}
            {new Date(schedule.nextRunAt).toLocaleString(undefined, {
              weekday: 'short',
              month: 'short',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit',
            })}
          </span>
          {schedule.assignees.length > 0 && <AssigneeAvatars assignees={schedule.assignees} />}
        </div>
      </div>
      <div className="flex items-center gap-1 shrink-0">
        {isPaused ? (
          <Button variant="ghost" size="sm" onClick={() => onResume(schedule)} loading={isResuming}>
            Resume
          </Button>
        ) : (
          <Button variant="ghost" size="sm" onClick={() => onPause(schedule)}>
            Pause
          </Button>
        )}
        <Button variant="ghost" size="sm" onClick={() => onEdit(schedule)}>
          Edit
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onDelete(schedule)}
          className="text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
        >
          Delete
        </Button>
      </div>
    </div>
    <MissedRunsPanel projectId={projectId} scheduleId={schedule.id} />
    </>
  );
}

function LoadingSkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 animate-pulse"
        >
          <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-1/3 mb-2" />
          <div className="h-3 bg-gray-100 dark:bg-gray-700 rounded w-2/3" />
        </div>
      ))}
    </div>
  );
}

export default function SchedulesPage() {
  const { projectFriendlyId: projectId = '' } = useParams();
  const { data: project } = useProject(projectId);
  const { data: schedules = [], isLoading, isError, refetch } = useSchedules(projectId);
  const deleteSchedule = useDeleteSchedule(projectId);
  const pauseSchedule = usePauseSchedule(projectId);
  const resumeSchedule = useResumeSchedule(projectId);
  const { hasAddon } = useCurrentOrg();

  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<RecurringScheduleResponse | null>(null);
  const [deleting, setDeleting] = useState<RecurringScheduleResponse | null>(null);
  const [pausing, setPausing] = useState<RecurringScheduleResponse | null>(null);
  const [resumingId, setResumingId] = useState<string | null>(null);

  usePageTitle('Schedules', project?.name);

  if (!hasAddon('RECURRING_SCHEDULING')) return <UpgradeCard featureName="Recurring Scheduling" />;

  if (isLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-10">
        <LoadingSkeleton />
      </div>
    );
  }

  if (isError) {
    return <PageError message="Failed to load schedules." onRetry={() => void refetch()} />;
  }

  return (
    <>
      <div className="max-w-3xl mx-auto px-4 py-10 space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">Schedules</h1>
          <Button variant="primary" size="sm" onClick={() => setShowCreate(true)}>
            + New Schedule
          </Button>
        </div>

        {schedules.length === 0 ? (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-10 text-center">
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
              No schedules yet. Create a recurring schedule to automatically create jobs from templates.
            </p>
            <Button onClick={() => setShowCreate(true)}>Create first schedule</Button>
          </div>
        ) : (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
            {schedules.map((s) => (
              <ScheduleRow
                key={s.id}
                schedule={s}
                projectId={projectId}
                onEdit={setEditing}
                onDelete={setDeleting}
                onPause={setPausing}
                onResume={(schedule) => {
                  setResumingId(schedule.id);
                  resumeSchedule.mutate(schedule.id, { onSettled: () => setResumingId(null) });
                }}
                isResuming={resumingId === s.id && resumeSchedule.isPending}
              />
            ))}
          </div>
        )}
      </div>

      <ScheduleFormModal
        key={showCreate ? 'create-open' : 'create-closed'}
        open={showCreate}
        onClose={() => setShowCreate(false)}
        projectId={projectId}
      />

      <ScheduleFormModal
        key={editing?.id ?? 'edit-closed'}
        open={editing !== null}
        onClose={() => setEditing(null)}
        projectId={projectId}
        schedule={editing ?? undefined}
      />

      {pausing && (
        <PauseDialog
          schedule={pausing}
          open={true}
          onClose={() => setPausing(null)}
          onPause={(until) => {
            pauseSchedule.mutate(
              { scheduleId: pausing.id, body: { until } },
              { onSuccess: () => setPausing(null) },
            );
          }}
          isPending={pauseSchedule.isPending}
        />
      )}

      <ConfirmModal
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => {
          if (!deleting) return;
          deleteSchedule.mutate(deleting.id, { onSuccess: () => setDeleting(null) });
        }}
        title="Delete Schedule"
        message={`Delete "${deleting?.name}"? Jobs already created by this schedule will not be deleted.`}
        confirmLabel="Delete"
        variant="danger"
        isPending={deleteSchedule.isPending}
      />
    </>
  );
}
