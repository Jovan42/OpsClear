import { useState, type ReactNode } from 'react';

interface Props {
  title: string;
  badge?: ReactNode;
  defaultExpanded?: boolean;
  children: ReactNode;
}

/**
 * Mirrors the exact accordion markup JobDetailPage builds inline for its Notes/
 * Approvals/Links sections (bordered card, header button with optional badge,
 * chevron, border-t content) — those three components don't render this chrome
 * themselves, JobDetailPage supplies it. Reused here so the demo cards for those
 * three sections look identical to the real job page, not a simplified rendering.
 *
 * Deliberately NOT used for Relationships/API keys — both already render their own
 * self-contained bordered card + header in the real app (non-collapsible), so
 * wrapping them in this a second time would just double the header/border.
 */
export default function DemoAccordion({ title, badge, defaultExpanded = true, children }: Readonly<Props>) {
  const [expanded, setExpanded] = useState(defaultExpanded);

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
      <button
        onClick={() => setExpanded((v) => !v)}
        className="w-full flex items-center justify-between px-6 py-4 text-sm font-medium text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
      >
        <div className="flex items-center gap-2">
          <span>{title}</span>
          {badge}
        </div>
        <span className="text-gray-400 dark:text-gray-500">{expanded ? '▲' : '▼'}</span>
      </button>
      {expanded && (
        <div className="px-6 pt-4 pb-4 border-t border-gray-100 dark:border-gray-700">{children}</div>
      )}
    </div>
  );
}
