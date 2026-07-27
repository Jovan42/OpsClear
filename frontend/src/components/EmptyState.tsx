import type { ReactNode } from 'react';
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
  /** Usually a plain string, but ReactNode so call sites needing inline formatting
   *  (e.g. TemplatesPage's wildcard example with <code> spans) aren't forced to
   *  flatten it to plain text just to use this component. */
  message: ReactNode;
  description?: ReactNode;
  action?: EmptyStateAction;
  /** Override the default page-level padding — smaller inline sections (e.g. a
   *  card's own empty list) don't want the same py-16 a full page does. */
  className?: string;
  /** Override the default icon size — a cramped inline context (e.g. a modal's
   *  small scrollable results list) doesn't want the same w-10 h-10 a full page
   *  empty state does. */
  iconClassName?: string;
}

const DEFAULT_PADDING_CLASS = 'py-16';
const DEFAULT_ICON_CLASS = 'w-10 h-10 text-gray-300 dark:text-gray-600 mb-3';

/**
 * Shared empty-state block (ADR-0041) — icon + message + optional description +
 * optional role-gated action. Standardizes the ~15 previously hand-rolled variants
 * across the app onto one component and one CTA-placement convention (co-located
 * inside the empty state itself, not decoupled into a section header).
 */
export default function EmptyState({ icon: Icon, message, description, action, className, iconClassName }: Readonly<EmptyStateProps>) {
  const canPerform = action?.canPerform ?? true;

  return (
    // `className` REPLACES the default padding rather than being appended alongside
    // it — two conflicting Tailwind utilities (e.g. py-16 and py-6) in the same
    // string don't resolve by string order, only by generated-CSS order, so
    // appending an override here would silently lose to py-16 unpredictably.
    <div className={`flex flex-col items-center justify-center text-center ${className ?? DEFAULT_PADDING_CLASS}`}>
      <Icon className={iconClassName ?? DEFAULT_ICON_CLASS} strokeWidth={1.5} />
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
