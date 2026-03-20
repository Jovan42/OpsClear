import type { ProgressFormat } from '../hooks/usePreferences';

interface Props {
  completed: number;
  total: number;
  format: ProgressFormat;
}

export default function ProgressBar({ completed, total, format }: Readonly<Props>) {
  if (total === 0) return null;

  const pct = Math.round((completed / total) * 100);
  const label = format === 'PERCENTAGE' ? `${pct}%` : `${completed}/${total}`;

  return (
    <div className="flex items-center gap-2 mt-1">
      <div className="flex-1 h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          className="h-full bg-blue-500 dark:bg-blue-400 rounded-full transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="text-xs text-gray-400 dark:text-gray-500 shrink-0">{label}</span>
    </div>
  );
}
