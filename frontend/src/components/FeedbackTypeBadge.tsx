import { useTranslation } from 'react-i18next';
import type { FeedbackType } from '../types';

const classNames: Record<FeedbackType, string> = {
  BUG: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  FEATURE: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  OTHER: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
};

interface FeedbackTypeBadgeProps {
  readonly type: FeedbackType;
}

export default function FeedbackTypeBadge({ type }: FeedbackTypeBadgeProps) {
  const { t } = useTranslation('feedback');
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${classNames[type]}`}>
      {t(`type.${type}`)}
    </span>
  );
}
