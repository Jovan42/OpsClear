interface SwitchProps {
  readonly checked: boolean;
  /** Locked: stays focusable (so screen readers reach its description) but ignores input. */
  readonly locked?: boolean;
  readonly labelledBy: string;
  readonly describedBy?: string;
  readonly testId: string;
  readonly onChange: (checked: boolean) => void;
}

// `aria-disabled`, not native `disabled`: a disabled button leaves the tab order, so keyboard
// and screen-reader users would never hear why the switch can't be changed.
export default function Switch({ checked, locked = false, labelledBy, describedBy, testId, onChange }: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      aria-disabled={locked || undefined}
      data-testid={testId}
      onClick={() => {
        if (!locked) onChange(!checked);
      }}
      onKeyDown={(e) => {
        if (locked && (e.key === 'Enter' || e.key === ' ')) e.preventDefault();
      }}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 ${
        checked ? 'bg-[var(--brand)]' : 'bg-gray-300 dark:bg-gray-600'
      } ${locked ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}
    >
      <span
        className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
          checked ? 'translate-x-6' : 'translate-x-1'
        }`}
      />
    </button>
  );
}
