import { useTranslation } from 'react-i18next';
import type { JobStatus } from '../types';
import { STATUS_KEYS } from '../utils/jobStatusLabel';

const classNames: Record<JobStatus, string> = {
  NEW: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  IN_PROGRESS: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  BLOCKED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  COMPLETED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
};

interface StatusBadgeProps {
  status: JobStatus;
}

export default function StatusBadge({ status }: StatusBadgeProps) {
  const { t } = useTranslation('shared2');
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${classNames[status]}`}>
      {t(STATUS_KEYS[status])}
    </span>
  );
}
