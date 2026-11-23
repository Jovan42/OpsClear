/**
 * The OpsClear wordmark (docs/brand): a ticked checkbox drawn as the "O", then "psClear".
 * Takes its colour from currentColor and its size from the surrounding font size. The real "O"
 * stays in the text (visually hidden), so the text content still reads "OpsClear".
 */
export default function Logo() {
  return (
    <span className="inline-flex items-baseline font-bold tracking-tight">
      <svg
        viewBox="0 0 176 191"
        aria-hidden="true"
        className="h-[0.73em] w-auto mr-[0.04em]"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <rect x="18" y="18" width="140" height="155" rx="52" strokeWidth="36" />
        <path d="M58 101 L84 127 L122 83" strokeWidth="24" />
      </svg>
      <span className="sr-only">O</span>psClear
    </span>
  );
}
