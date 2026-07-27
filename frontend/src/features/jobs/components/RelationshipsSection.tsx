import { useState, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { Link } from 'react-router-dom';
import { GitBranch } from 'lucide-react';
import EmptyState from '../../../components/EmptyState';
import StatusBadge from '../../../components/StatusBadge';
import { useDeleteRelationship } from '../useJobs';
import type { JobRelationshipDirection, JobRelationshipType, JobRelationshipView } from '../../../types';

interface Props {
  projectId: string;
  jobId: string;
  relationships: JobRelationshipView[];
  canManage: boolean;
  onAdd: () => void;
  projectCompleted: boolean;
  /** Starts expanded instead of collapsed — used by the /features interactive demo
   *  (ADR-0040), same as StatusHistory's defaultExpanded, so the demo shows real
   *  relationships immediately instead of behind an extra click. */
  defaultExpanded?: boolean;
}

const TYPE_COLORS: Record<JobRelationshipType, string> = {
  BLOCKED_BY: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  RELATED_TO: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300',
  DUPLICATES: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
};

function directionLabel(type: JobRelationshipType, direction: JobRelationshipDirection, t: TFunction): string {
  if (direction === 'OUTGOING') {
    if (type === 'BLOCKED_BY') return t('jobsComponents:addRelationshipModal.types.blockedBy.label');
    if (type === 'DUPLICATES') return t('jobsComponents:addRelationshipModal.types.duplicates.label');
    return t('jobsComponents:addRelationshipModal.types.relatedTo.label');
  }
  // Incoming — flip the label
  if (type === 'BLOCKED_BY') return t('jobsComponents:relationshipsSection.blocks');
  if (type === 'DUPLICATES') return t('jobsComponents:relationshipsSection.duplicatedBy');
  return t('jobsComponents:addRelationshipModal.types.relatedTo.label');
}

export default function RelationshipsSection({
  projectId,
  jobId,
  relationships,
  canManage,
  onAdd,
  projectCompleted,
  defaultExpanded = false,
}: Props) {
  const { t } = useTranslation('jobsComponents');
  const { mutate: deleteRel, isPending: isDeleting } = useDeleteRelationship(projectId, jobId);
  // Collapsed by default, same as the sibling sections (Notes/History/Approvals/
  // Links) on this page — otherwise a job that never gets a relationship (the
  // common case) permanently shows a full empty-state block instead of the one-line
  // header everything else collapses to.
  const [expanded, setExpanded] = useState(defaultExpanded);

  function handleKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      setExpanded((v) => !v);
    }
  }

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
      {/* A plain <div role="button">, not a real <button> — the nested "+ Add"
          trigger below can't live inside a real <button> (invalid nested-button
          HTML), so this toggle needs to be its own non-button clickable element. */}
      <div
        role="button"
        tabIndex={0}
        onClick={() => setExpanded((v) => !v)}
        onKeyDown={handleKeyDown}
        className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
      >
        <div className="flex items-center gap-2">
          <span>{t('jobsComponents:relationshipsSection.heading')}</span>
          {relationships.length > 0 && (
            <span className="bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300 text-xs font-medium px-1.5 py-0.5 rounded-full">
              {relationships.length}
            </span>
          )}
        </div>
        <div className="flex items-center gap-3">
          {relationships.length > 0 && canManage && !projectCompleted && (
            <button
              onClick={(e) => { e.stopPropagation(); onAdd(); }}
              className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
            >
              {t('jobsComponents:relationshipsSection.add')}
            </button>
          )}
          <span className="text-gray-400 dark:text-gray-500">{expanded ? '▲' : '▼'}</span>
        </div>
      </div>

      {expanded && (
        <div className="px-6 pt-2 pb-4 border-t border-gray-100 dark:border-gray-700">
          {relationships.length === 0 ? (
            <EmptyState
              icon={GitBranch}
              message={t('jobsComponents:relationshipsSection.noRelationships')}
              className="py-4"
              iconClassName="w-7 h-7 text-gray-300 dark:text-gray-600 mb-2"
              action={{
                label: t('jobsComponents:relationshipsSection.add'),
                onClick: onAdd,
                canPerform: canManage && !projectCompleted,
              }}
            />
          ) : (
            <ul className="space-y-2">
              {relationships.map((rel) => (
                <li key={rel.id} className="flex items-center gap-3 text-sm">
                  <span
                    className={`shrink-0 text-xs font-medium px-1.5 py-0.5 rounded-full ${TYPE_COLORS[rel.type]}`}
                  >
                    {directionLabel(rel.type, rel.direction, t)}
                  </span>

                  {rel.job.id ? (
                    <Link
                      to={`/projects/${projectId}/jobs/${rel.job.friendlyId ?? rel.job.id}`}
                      className={`flex-1 truncate font-medium hover:underline ${
                        rel.job.status === 'COMPLETED'
                          ? 'line-through text-gray-400 dark:text-gray-500'
                          : 'text-gray-900 dark:text-gray-100'
                      }`}
                    >
                      {rel.job.title ?? t('jobsComponents:relationshipsSection.deletedJob')}
                    </Link>
                  ) : (
                    <span className="flex-1 truncate text-gray-400 dark:text-gray-500 italic">
                      {rel.job.title ?? t('jobsComponents:relationshipsSection.deletedJob')}
                    </span>
                  )}

                  {rel.job.status && (
                    <StatusBadge status={rel.job.status} />
                  )}

                  {canManage && !projectCompleted && (
                    <button
                      onClick={() => deleteRel(rel.id)}
                      disabled={isDeleting}
                      className="shrink-0 text-gray-400 dark:text-gray-500 hover:text-red-500 dark:hover:text-red-400 text-lg leading-none cursor-pointer disabled:opacity-50"
                      title={t('jobsComponents:relationshipsSection.removeRelationship')}
                    >
                      ×
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
