import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useMilestones, useDeleteMilestone } from '../jobs/useMilestones';
import { useJobList } from '../jobs/useJobs';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useProject } from '../projects/useProjects';
import { usePreferences } from '../../hooks/usePreferences';
import MilestoneFormModal from './MilestoneFormModal';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import PageError from '../../components/PageError';
import ProgressBar from '../../components/ProgressBar';
import UpgradeCard from '../../components/UpgradeCard';
import { useCurrentOrg } from '../org/OrgContext';
import type { MilestoneResponse } from '../../types';

function formatDeadline(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function isOverdue(deadline: string): boolean {
  return new Date(deadline) < new Date(new Date().toDateString());
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

interface MilestoneRowProps {
  milestone: MilestoneResponse;
  projectId: string;
  completed: number;
  total: number;
  onEdit: (milestone: MilestoneResponse) => void;
  onDelete: (milestone: MilestoneResponse) => void;
}

function MilestoneRow({ milestone, projectId, completed, total, onEdit, onDelete }: Readonly<MilestoneRowProps>) {
  const overdue = milestone.deadline ? isOverdue(milestone.deadline) : false;
  const { prefs } = usePreferences();

  return (
    <div className="flex items-start justify-between gap-4 p-5">
      <div className="min-w-0 space-y-1 flex-1">
        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{milestone.name}</p>
        {milestone.description && (
          <p className="text-sm text-gray-500 dark:text-gray-400 line-clamp-2">
            {milestone.description}
          </p>
        )}
        {milestone.deadline && (
          <p
            className={`text-xs ${
              overdue
                ? 'text-red-600 dark:text-red-400 font-medium'
                : 'text-gray-400 dark:text-gray-500'
            }`}
          >
            {overdue ? '⚠ Overdue · ' : ''}Due {formatDeadline(milestone.deadline)}
          </p>
        )}
        <ProgressBar completed={completed} total={total} format={prefs.milestoneProgressFormat} />
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <Link
          to={`/projects/${projectId}/jobs?milestone=${milestone.id}`}
          className="text-xs text-blue-600 dark:text-blue-400 hover:underline whitespace-nowrap"
        >
          View jobs →
        </Link>
        <Button variant="ghost" size="sm" onClick={() => onEdit(milestone)}>
          Edit
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onDelete(milestone)}
          className="text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
        >
          Delete
        </Button>
      </div>
    </div>
  );
}

export default function MilestonesPage() {
  const { projectFriendlyId: projectId = '' } = useParams();
  const { data: project } = useProject(projectId);
  const { data: milestones = [], isLoading, isError, refetch } = useMilestones(projectId);
  const { data: allJobs = [] } = useJobList(projectId);
  const deleteMilestone = useDeleteMilestone(projectId);
  const { hasAddon } = useCurrentOrg();

  usePageTitle('Milestones', project?.name);

  if (!hasAddon('MILESTONES')) return <UpgradeCard featureName="Milestones" />;

  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<MilestoneResponse | null>(null);
  const [deleting, setDeleting] = useState<MilestoneResponse | null>(null);

  if (isLoading) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-10">
        <LoadingSkeleton />
      </div>
    );
  }

  if (isError) {
    return <PageError message="Failed to load milestones." onRetry={() => void refetch()} />;
  }

  return (
    <>
      <div className="max-w-2xl mx-auto px-4 py-10 space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">Milestones</h1>
          <Button variant="primary" size="sm" onClick={() => setShowCreate(true)}>
            + New Milestone
          </Button>
        </div>

        {milestones.length === 0 ? (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-10 text-center">
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
              No milestones yet.
            </p>
            <Button onClick={() => setShowCreate(true)}>Create first milestone</Button>
          </div>
        ) : (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
            {milestones.map((ms) => {
              const msJobs = allJobs.filter((j) => j.milestoneId === ms.id);
              const completedCount = msJobs.filter((j) => j.status === 'COMPLETED').length;
              return (
                <MilestoneRow
                  key={ms.id}
                  milestone={ms}
                  projectId={projectId}
                  completed={completedCount}
                  total={msJobs.length}
                  onEdit={setEditing}
                  onDelete={setDeleting}
                />
              );
            })}
          </div>
        )}
      </div>

      <MilestoneFormModal
        key={showCreate ? 'create-open' : 'create-closed'}
        open={showCreate}
        onClose={() => setShowCreate(false)}
        projectId={projectId}
      />

      <MilestoneFormModal
        key={editing?.id ?? 'edit-closed'}
        open={editing !== null}
        onClose={() => setEditing(null)}
        projectId={projectId}
        milestone={editing ?? undefined}
      />

      <ConfirmModal
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => {
          if (!deleting) return;
          deleteMilestone.mutate(deleting.id, { onSuccess: () => setDeleting(null) });
        }}
        title="Delete Milestone"
        message={`Delete "${deleting?.name}"? Jobs in this milestone will become ungrouped.`}
        confirmLabel="Delete"
        variant="danger"
        isPending={deleteMilestone.isPending}
      />
    </>
  );
}
