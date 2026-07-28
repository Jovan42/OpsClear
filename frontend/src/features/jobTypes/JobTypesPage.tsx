import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { Tag, ChevronUp, ChevronDown } from 'lucide-react';
import { useJobTypes, useDeleteJobType, useUpdateJobType } from './useJobTypes';
import { useProject, useProjectRole } from '../projects/useProjects';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useCurrentOrg } from '../org/OrgContext';
import JobTypeFormModal from './JobTypeFormModal';
import ConfirmModal from '../../components/ConfirmModal';
import Button from '../../components/Button';
import EmptyState from '../../components/EmptyState';
import PageError from '../../components/PageError';
import UpgradeCard from '../../components/UpgradeCard';
import JobTypeBadge from '../../components/JobTypeBadge';
import type { JobTypeResponse } from '../../types';

function LoadingSkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 animate-pulse"
        >
          <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-1/3" />
        </div>
      ))}
    </div>
  );
}

interface TypeRowProps {
  type: JobTypeResponse;
  isFirst: boolean;
  isLast: boolean;
  canManage: boolean;
  onEdit: (t: JobTypeResponse) => void;
  onDelete: (t: JobTypeResponse) => void;
  onMove: (t: JobTypeResponse, direction: 'up' | 'down') => void;
}

function TypeRow({ type, isFirst, isLast, canManage, onEdit, onDelete, onMove }: Readonly<TypeRowProps>) {
  const { t } = useTranslation(['jobTypes', 'common']);
  return (
    <div className="flex items-center justify-between gap-4 p-4">
      <div className="flex items-center gap-3 min-w-0">
        {canManage && (
          <div className="flex flex-col shrink-0">
            <button
              onClick={() => onMove(type, 'up')}
              disabled={isFirst}
              className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
              aria-label={t('jobTypes:typesPage.moveUp')}
            >
              <ChevronUp className="w-4 h-4" />
            </button>
            <button
              onClick={() => onMove(type, 'down')}
              disabled={isLast}
              className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
              aria-label={t('jobTypes:typesPage.moveDown')}
            >
              <ChevronDown className="w-4 h-4" />
            </button>
          </div>
        )}
        <JobTypeBadge name={type.name} color={type.color} />
      </div>
      {canManage && (
        <div className="flex items-center gap-2 shrink-0">
          <Button variant="ghost" size="sm" onClick={() => onEdit(type)}>{t('common:edit')}</Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onDelete(type)}
            className="text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
          >
            {t('common:delete')}
          </Button>
        </div>
      )}
    </div>
  );
}

export default function JobTypesPage() {
  const { t } = useTranslation(['jobTypes', 'common']);
  const { projectFriendlyId: projectId = '' } = useParams();
  const { data: project } = useProject(projectId);
  const { data: types = [], isLoading, isError, refetch } = useJobTypes(projectId);
  const deleteType = useDeleteJobType(projectId);
  const updateType = useUpdateJobType(projectId);
  const { hasAddon } = useCurrentOrg();
  const role = useProjectRole(projectId);
  const isOwnerOrAdmin = role === 'OWNER' || role === 'ADMIN';

  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<JobTypeResponse | null>(null);
  const [deleting, setDeleting] = useState<JobTypeResponse | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  usePageTitle(t('jobTypes:typesPage.browserTitle'), project?.name);

  if (!hasAddon('JOB_TYPES')) return <UpgradeCard featureName={t('jobTypes:typesPage.pageHeading')} />;

  if (isLoading) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-10">
        <LoadingSkeleton />
      </div>
    );
  }

  if (isError) {
    return <PageError message={t('jobTypes:typesPage.loadFailedMessage')} onRetry={() => void refetch()} />;
  }

  const sorted = types.slice().sort((a, b) => a.displayOrder - b.displayOrder);

  function handleMove(type: JobTypeResponse, direction: 'up' | 'down') {
    const idx = sorted.findIndex((t) => t.id === type.id);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;
    const other = sorted[swapIdx];
    updateType.mutate({ typeId: type.id, body: { name: type.name, color: type.color, displayOrder: other.displayOrder } });
    updateType.mutate({ typeId: other.id, body: { name: other.name, color: other.color, displayOrder: type.displayOrder } });
  }

  return (
    <>
      <div className="max-w-2xl mx-auto px-4 py-10 space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">{t('jobTypes:typesPage.pageHeading')}</h1>
          {isOwnerOrAdmin && (
            <Button variant="primary" size="sm" onClick={() => setShowCreate(true)}>
              {t('jobTypes:typesPage.newButton')}
            </Button>
          )}
        </div>

        {sorted.length === 0 ? (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl">
            <EmptyState
              icon={Tag}
              message={t('jobTypes:typesPage.emptyText')}
              action={{
                label: t('jobTypes:typesPage.createFirstButton'),
                onClick: () => setShowCreate(true),
                canPerform: isOwnerOrAdmin,
              }}
            />
          </div>
        ) : (
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl divide-y divide-gray-100 dark:divide-gray-700">
            {sorted.map((type, i) => (
              <TypeRow
                key={type.id}
                type={type}
                isFirst={i === 0}
                isLast={i === sorted.length - 1}
                canManage={isOwnerOrAdmin}
                onEdit={setEditing}
                onDelete={(tp) => { setDeleting(tp); setDeleteError(null); }}
                onMove={handleMove}
              />
            ))}
          </div>
        )}
      </div>

      <JobTypeFormModal
        key={showCreate ? 'create-open' : 'create-closed'}
        open={showCreate}
        onClose={() => setShowCreate(false)}
        projectId={projectId}
      />

      <JobTypeFormModal
        key={editing?.id ?? 'edit-closed'}
        open={editing !== null}
        onClose={() => setEditing(null)}
        projectId={projectId}
        type={editing ?? undefined}
      />

      <ConfirmModal
        open={deleting !== null}
        onClose={() => { setDeleting(null); setDeleteError(null); }}
        onConfirm={() => {
          if (!deleting) return;
          deleteType.mutate(deleting.id, {
            onSuccess: () => { setDeleting(null); setDeleteError(null); },
            onError: (err) => {
              if (isAxiosError(err) && err.response?.status === 409) {
                setDeleteError(err.response.data.message as string);
              }
            },
          });
        }}
        title={t('jobTypes:typesPage.deleteModalTitle')}
        message={t('jobTypes:typesPage.deleteModalMessage', { name: deleting?.name })}
        confirmLabel={t('common:delete')}
        variant="danger"
        isPending={deleteType.isPending}
        errorMessage={deleteError}
      />
    </>
  );
}
