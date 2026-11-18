import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Info, Lightbulb, type LucideIcon } from 'lucide-react';

type CalloutType = 'tip' | 'note' | 'warning';

const STYLES: Record<CalloutType, { box: string; icon: string; Icon: LucideIcon }> = {
  tip: {
    box: 'border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950/40',
    icon: 'text-green-600 dark:text-green-400',
    Icon: Lightbulb,
  },
  note: {
    box: 'border-blue-200 bg-blue-50 dark:border-blue-900 dark:bg-blue-950/40',
    icon: 'text-blue-600 dark:text-blue-400',
    Icon: Info,
  },
  warning: {
    box: 'border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/40',
    icon: 'text-amber-600 dark:text-amber-400',
    Icon: AlertTriangle,
  },
};

export function Callout({ type = 'note', children }: Readonly<{ type?: CalloutType; children: ReactNode }>) {
  const { t } = useTranslation('guide');
  const { box, icon, Icon } = STYLES[type];
  return (
    <aside className={`not-prose my-6 flex gap-3 rounded-xl border px-4 py-3 ${box}`} data-testid={`guide-callout-${type}`}>
      <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${icon}`} aria-hidden />
      <div className="min-w-0">
        <p className={`text-sm font-semibold ${icon}`}>{t(`callout.${type}`)}</p>
        <div className="prose prose-sm dark:prose-invert max-w-none prose-p:my-1 prose-a:text-brand dark:prose-a:text-green-400">
          {children}
        </div>
      </div>
    </aside>
  );
}
