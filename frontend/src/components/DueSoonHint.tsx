import { useTranslation } from 'react-i18next';
import type { DeadlineState } from '../utils/deadline';

interface DueSoonHintProps {
  readonly state: DeadlineState;
  readonly className?: string;
}

/** The small amber "Due soon" pill shown next to a due-soon deadline (ADR-0061); nothing otherwise. */
export default function DueSoonHint({ state, className = '' }: DueSoonHintProps) {
  const { t } = useTranslation('shared2');
  if (state !== 'dueSoon') return null;
  return (
    <span
      data-testid="due-soon-hint"
      className={`inline-flex items-center px-1.5 py-0.5 rounded text-[11px] leading-none font-medium whitespace-nowrap bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400 ${className}`}
    >
      {t('deadline.dueSoon')}
    </span>
  );
}
