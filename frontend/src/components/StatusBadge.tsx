import type { JobStatus } from '../types';

const config: Record<JobStatus, { label: string; className: string }> = {
  NEW: {
    label: 'New',
    className: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  },
  IN_PROGRESS: {
    label: 'In Progress',
    className: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  },
  BLOCKED: {
    label: 'Blocked',
    className: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  },
  COMPLETED: {
    label: 'Completed',
    className: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  },
};

interface StatusBadgeProps {
  status: JobStatus;
}

export default function StatusBadge({ status }: StatusBadgeProps) {
  const { label, className } = config[status];
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}
