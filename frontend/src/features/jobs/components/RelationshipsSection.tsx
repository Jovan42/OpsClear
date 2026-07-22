import { Link } from 'react-router-dom';
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
}

const TYPE_LABELS: Record<JobRelationshipType, string> = {
  BLOCKED_BY: 'Blocked by',
  RELATED_TO: 'Related to',
  DUPLICATES: 'Duplicates',
};

const TYPE_COLORS: Record<JobRelationshipType, string> = {
  BLOCKED_BY: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  RELATED_TO: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300',
  DUPLICATES: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
};

function directionLabel(type: JobRelationshipType, direction: JobRelationshipDirection): string {
  if (direction === 'OUTGOING') return TYPE_LABELS[type];
  // Incoming — flip the label
  if (type === 'BLOCKED_BY') return 'Blocks';
  if (type === 'DUPLICATES') return 'Duplicated by';
  return 'Related to';
}

export default function RelationshipsSection({
  projectId,
  jobId,
  relationships,
  canManage,
  onAdd,
  projectCompleted,
}: Props) {
  const { mutate: deleteRel, isPending: isDeleting } = useDeleteRelationship(projectId, jobId);

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl px-6 py-4">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-medium text-gray-900 dark:text-gray-100">Relationships</h2>
        {canManage && !projectCompleted && (
          <button
            onClick={onAdd}
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
          >
            + Add
          </button>
        )}
      </div>

      {relationships.length === 0 ? (
        <p className="text-sm text-gray-400 dark:text-gray-500">No relationships yet.</p>
      ) : (
        <ul className="space-y-2">
          {relationships.map((rel) => (
            <li key={rel.id} className="flex items-center gap-3 text-sm">
              <span
                className={`shrink-0 text-xs font-medium px-1.5 py-0.5 rounded-full ${TYPE_COLORS[rel.type]}`}
              >
                {directionLabel(rel.type, rel.direction)}
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
                  {rel.job.title ?? '(deleted)'}
                </Link>
              ) : (
                <span className="flex-1 truncate text-gray-400 dark:text-gray-500 italic">
                  {rel.job.title ?? '(deleted)'}
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
                  title="Remove relationship"
                >
                  ×
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
