import { Children, type ReactNode } from 'react';

/** Numbered steps joined by a vertical line. Children are `<Step>`s. */
export function Steps({ children }: Readonly<{ children: ReactNode }>) {
  const steps = Children.toArray(children);
  return (
    <ol className="not-prose my-6 list-none p-0" data-testid="guide-steps">
      {steps.map((step, i) => (
        <li key={i} className="relative pl-12 pb-6 last:pb-0">
          {i < steps.length - 1 && (
            <span aria-hidden className="absolute left-[15px] top-8 bottom-0 w-px bg-gray-200 dark:bg-gray-700" />
          )}
          <span
            aria-hidden
            className="absolute left-0 top-0 flex h-8 w-8 items-center justify-center rounded-full bg-brand-light text-sm font-semibold text-brand dark:bg-green-900/40 dark:text-green-300"
          >
            {i + 1}
          </span>
          {step}
        </li>
      ))}
    </ol>
  );
}

/** One step: a short title and its body (Markdown). */
export function Step({ title, children }: Readonly<{ title?: string; children?: ReactNode }>) {
  return (
    <div className="pt-1">
      {title && <p className="font-semibold text-gray-900 dark:text-gray-100">{title}</p>}
      {children && (
        <div className="prose prose-sm dark:prose-invert max-w-none mt-1 prose-p:my-1 prose-a:text-brand dark:prose-a:text-green-400">
          {children}
        </div>
      )}
    </div>
  );
}
