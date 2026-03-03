import type { JobStatus } from '../types';

const config: Record<JobStatus, { label: string; className: string }> = {
  NEW: {
    label: 'New',
    className: 'bg-gray-100 text-gray-600',
  },
  IN_PROGRESS: {
    label: 'In Progress',
    className: 'bg-blue-100 text-blue-700',
  },
  BLOCKED: {
    label: 'Blocked',
    className: 'bg-red-100 text-red-700',
  },
  COMPLETED: {
    label: 'Completed',
    className: 'bg-green-100 text-green-700',
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
