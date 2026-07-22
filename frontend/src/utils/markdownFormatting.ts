export interface MarkdownFormatAction {
  /** Inserted/wrapped before the selection (or before the cursor when nothing is selected). */
  prefix: string;
  /** Inserted/wrapped after the selection (or after the cursor when nothing is selected). */
  suffix: string;
  /** Shown as the placeholder body when nothing is selected — left selected for easy overtyping. */
  placeholder: string;
  /** When true, prefix/suffix apply per-line rather than around the whole selection (e.g. blockquote, lists). */
  perLine?: boolean;
  /** When true, the placeholder is always used as the body, even if text is selected —
   *  for markers like a horizontal rule that don't "wrap" content. */
  ignoreSelection?: boolean;
}

export interface MarkdownFormatResult {
  value: string;
  selectionStart: number;
  selectionEnd: number;
}

/**
 * Wraps the current textarea selection with markdown syntax, or inserts a placeholder
 * at the cursor when nothing is selected. Returns the new full value plus where the
 * selection should be restored to (the wrapped/placeholder text, so it stays selected
 * for easy overtyping per ADR-0039).
 */
export function applyMarkdownFormat(
  value: string,
  selectionStart: number,
  selectionEnd: number,
  action: MarkdownFormatAction,
): MarkdownFormatResult {
  const selected = value.slice(selectionStart, selectionEnd);
  const hasSelection = selectionStart !== selectionEnd;

  if (action.perLine) {
    const body = hasSelection && !action.ignoreSelection ? selected : action.placeholder;
    const formatted = body
      .split('\n')
      .map((line) => `${action.prefix}${line}`)
      .join('\n');
    const before = value.slice(0, selectionStart);
    const after = value.slice(selectionEnd);
    return {
      value: before + formatted + after,
      selectionStart: before.length,
      selectionEnd: before.length + formatted.length,
    };
  }

  const body = hasSelection && !action.ignoreSelection ? selected : action.placeholder;
  const wrapped = `${action.prefix}${body}${action.suffix}`;
  const before = value.slice(0, selectionStart);
  const after = value.slice(selectionEnd);

  return {
    value: before + wrapped + after,
    selectionStart: before.length + action.prefix.length,
    selectionEnd: before.length + action.prefix.length + body.length,
  };
}

/**
 * Link is a special case: wrapping a selection should leave the "url" placeholder
 * selected (ready to paste over), not the link text — the opposite of every other
 * button, where the body itself is what gets left selected.
 */
export function applyMarkdownLink(
  value: string,
  selectionStart: number,
  selectionEnd: number,
): MarkdownFormatResult {
  const hasSelection = selectionStart !== selectionEnd;
  const before = value.slice(0, selectionStart);
  const after = value.slice(selectionEnd);
  const urlPlaceholder = 'url';

  if (hasSelection) {
    const text = value.slice(selectionStart, selectionEnd);
    const wrapped = `[${text}](${urlPlaceholder})`;
    const urlStart = before.length + 1 + text.length + 2;
    return {
      value: before + wrapped + after,
      selectionStart: urlStart,
      selectionEnd: urlStart + urlPlaceholder.length,
    };
  }

  const textPlaceholder = 'link text';
  const wrapped = `[${textPlaceholder}](${urlPlaceholder})`;
  const textStart = before.length + 1;
  return {
    value: before + wrapped + after,
    selectionStart: textStart,
    selectionEnd: textStart + textPlaceholder.length,
  };
}
