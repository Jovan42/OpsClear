import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { CalendarClock } from 'lucide-react';
import { useSchedules, useDeleteSchedule, usePauseSchedule, useResumeSchedule } from './useSchedules';
import { useProject, useProjectRole } from '../projects/useProjects';
import { useCurrentOrg } from '../org/OrgContext';
import { usePageTitle } from '../../hooks/usePageTitle';
import { toReadable } from '../../utils/cron';
import ScheduleFormModal from './ScheduleFormModal';
import MissedRunsPanel from './MissedRunsPanel';
import PauseDialog from './PauseDialog';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import EmptyState from '../../components/EmptyState';
import UpgradeCard from '../../components/UpgradeCard';
import PageError from '../../components/PageError';
import type { RecurringScheduleResponse, ScheduleStatus } from '../../types';

function StatusBadge({ status }: Readonly<{ status: ScheduleStatus }>) {
  const { t } = useTranslation('milestonesTemplatesSchedules');
  const map: Record<ScheduleStatus, { label: string; cls: string }> = {
    ACTIVE: { label: t('milestonesTemplatesSchedules:schedulesPage.statusActive'), cls: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
    PAUSED: { label: t('milestonesTemplatesSchedules:schedulesPage.statusPaused'), cls: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400' },
    PAUSED_NO_ASSIGNEES: {
      label: t('milestonesTemplatesSchedules:schedulesPage.statusNoAssignees'),
      cls: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    },
    EXPIRED: { label: t('milestonesTemplatesSchedules:schedulesPage.statusExpired'), cls: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400' },
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
  canManage: boolean;
  onEdit: (s: RecurringScheduleResponse) => void;
  onDelete: (s: RecurringScheduleResponse) => void;
  onPause: (s: RecurringScheduleResponse) => void;
  onResume: (s: RecurringScheduleResponse) => void;
  isResuming: boolean;
}

function ScheduleRow({
  schedule,
  projectId,
  canManage,
  onEdit,
  onDelete,
  onPause,
  onResume,
  isResuming,
}: Readonly<ScheduleRowProps>) {
  const { t } = useTranslation(['milestonesTemplatesSchedules', 'common']);
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
          <p className="text-xs text-gray-500 dark:text-gray-400">{t('milestonesTemplatesSchedules:schedulesPage.templateLabel', { name: schedule.templateName })}</p>
        )}
        <p className="text-xs text-gray-500 dark:text-gray-400">{toReadable(schedule.cronExpression)}</p>
        <div className="flex items-center gap-4 text-xs text-gray-400 dark:text-gray-500">
          <span>
            {t('milestonesTemplatesSchedules:schedulesPage.nextLabel')}{' '}
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
      {canManage && (
        <div className="flex items-center gap-1 shrink-0">
          {isPaused ? (
            <Button variant="ghost" size="sm" onClick={() => onResume(schedule)} loading={isResuming}>
              {t('milestonesTemplatesSchedules:schedulesPage.resumeButton')}
            </Button>
          ) : (
            <Button variant="ghost" size="sm" onClick={() => onPause(schedule)}>
              {t('milestonesTemplatesSchedules:schedulesPage.pauseAction')}
            </Button>
          )}
          <Button variant="ghost" size="sm" onClick={() => onEdit(schedule)}>
            {t('common:edit')}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onDelete(schedule)}
            className="text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
          >
            {t('common:delete')}
          </Button>
        </div>
      )}
    </div>
    <MissedRunsPanel projectId={projectId} scheduleId={schedule.id} canManage={canManage} />
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
  const { t } = useTranslation(['milestonesTemplatesSchedules', 'common']);
  const { projectFriendlyId: projectId = '' } = useParams();
  const { data: project } = useProject(projectId);
  const { data: schedules = [], isLoading, isError, refetch } = useSchedules(projectId);
  const deleteSchedule = useDeleteSchedule(projectId);
  const pauseSchedule = usePauseSchedule(projectId);
  const resumeSchedule = useResumeSchedule(projectId);
  const { hasAddon } = useCurrentOrg();
  const role = useProjectRole(projectId);
  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';

  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<RecurringScheduleResponse | null>(null);
  const [deleting, setDeleting] = useState<RecurringScheduleResponse | null>(null);
  const [pausing, setPausing] = useState<RecurringScheduleResponse | null>(null);
  const [resumingId, setResumingId] = useState<string | null>(null);

  usePageTitle(t('milestonesTemplatesSchedules:schedulesPage.pageTitle'), project?.name);

  if (!hasAddon('RECURRING_SCHEDULING')) return <UpgradeCard featureName={t('milestonesTemplatesSchedules:schedulesPage.upgradeFeatureName')} />;

  if (isLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-10">
        <LoadingSkeleton />
      </div>
    );
  }

  if (isError) {
    return <PageError message={t('milestonesTemplatesSchedules:schedulesPage.loadFailedMessage')} onRetry={() => void refetch()} />;
  }

  return (
    <>
      <div className="max-w-3xl mx-auto px-4 py-10 space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">{t('milestonesTemplatesSchedules:schedulesPage.pageTitle')}</h1>
          {isOwnerOrAdmin && (
            <Button variant="primary" size="sm" onClick={() => setShowCreate(true)}>
              {t('milestonesTemplatesSchedules:schedulesPage.newButton')}
            </Button>
          )}
        </div>

        {schedules.length === 0 ? (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl">
            <EmptyState
              icon={CalendarClock}
              message={t('milestonesTemplatesSchedules:schedulesPage.emptyText')}
              action={{
                label: t('milestonesTemplatesSchedules:schedulesPage.createFirstButton'),
                onClick: () => setShowCreate(true),
                canPerform: isOwnerOrAdmin,
              }}
            />
          </div>
        ) : (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
            {schedules.map((s) => (
              <ScheduleRow
                key={s.id}
                schedule={s}
                projectId={projectId}
                canManage={isOwnerOrAdmin}
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
        title={t('milestonesTemplatesSchedules:schedulesPage.deleteModalTitle')}
        message={t('milestonesTemplatesSchedules:schedulesPage.deleteModalMessage', { name: deleting?.name })}
        confirmLabel={t('common:delete')}
        variant="danger"
        isPending={deleteSchedule.isPending}
      />
    </>
  );
}
