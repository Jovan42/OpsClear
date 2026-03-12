import type { JobPriority } from '../types';

const config: Record<JobPriority, { label: string; className: string }> = {
  LOW: {
    label: 'Low',
    className: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
  },
  MEDIUM: {
    label: 'Medium',
    className: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  },
  HIGH: {
    label: 'High',
    className: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  },
  CRITICAL: {
    label: 'Critical',
    className: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  },
};

interface PriorityBadgeProps {
  priority: JobPriority;
}

export default function PriorityBadge({ priority }: PriorityBadgeProps) {
  const { label, className } = config[priority];
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}
