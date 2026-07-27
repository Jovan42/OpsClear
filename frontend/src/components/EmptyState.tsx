import type { LucideIcon } from 'lucide-react';
import Button from './Button';

interface EmptyStateAction {
  label: string;
  onClick: () => void;
  /** Defaults to true. Set false to hide the action entirely (e.g. the current
   *  user lacks permission) rather than rendering it disabled. */
  canPerform?: boolean;
}

interface EmptyStateProps {
  icon: LucideIcon;
  message: string;
  description?: string;
  action?: EmptyStateAction;
  /** Override the default page-level padding — smaller inline sections (e.g. a
   *  card's own empty list) don't want the same py-16 a full page does. */
  className?: string;
}

/**
 * Shared empty-state block (ADR-0041) — icon + message + optional description +
 * optional role-gated action. Standardizes the ~15 previously hand-rolled variants
 * across the app onto one component and one CTA-placement convention (co-located
 * inside the empty state itself, not decoupled into a section header).
 */
export default function EmptyState({ icon: Icon, message, description, action, className }: Readonly<EmptyStateProps>) {
  const canPerform = action?.canPerform ?? true;

  return (
    <div className={`flex flex-col items-center justify-center py-16 text-center ${className ?? ''}`}>
      <Icon className="w-10 h-10 text-gray-300 dark:text-gray-600 mb-3" strokeWidth={1.5} />
      <p className="text-gray-500 dark:text-gray-400 text-sm">{message}</p>
      {description && (
        <p className="text-gray-400 dark:text-gray-500 text-xs mt-1">{description}</p>
      )}
      {action && canPerform && (
        <div className="mt-4">
          <Button onClick={action.onClick}>{action.label}</Button>
        </div>
      )}
    </div>
  );
}
