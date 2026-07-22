import { useTranslation } from 'react-i18next';
import type { JobPriority } from '../types';

const classNames: Record<JobPriority, string> = {
  LOW: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
  MEDIUM: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  HIGH: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  CRITICAL: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
};

interface PriorityBadgeProps {
  priority: JobPriority;
}

export default function PriorityBadge({ priority }: PriorityBadgeProps) {
  const { t } = useTranslation('shared2');
  const labels: Record<JobPriority, string> = {
    LOW: t('priority.low'),
    MEDIUM: t('priority.medium'),
    HIGH: t('priority.high'),
    CRITICAL: t('priority.critical'),
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${classNames[priority]}`}>
      {labels[priority]}
    </span>
  );
}
