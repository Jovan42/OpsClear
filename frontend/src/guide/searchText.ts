import GithubSlugger from 'github-slugger';

// ADR-0062: the guide search indexes plain text, not MDX. This turns a page's MDX source into
// sections (one per heading, plus the intro before the first heading), with the same anchor
// ids rehype-slug gives the rendered headings, so a result can go straight to its heading.

export interface GuideTextSection {
  /** Heading text, or null for the intro before the first heading. */
  heading: string | null;
  /** rehype-slug's id for the heading, or null for the intro. */
  anchor: string | null;
  text: string;
}

/** Inline Markdown and JSX to plain text: `**Save**` → `Save`, `[the FAQ](/guide/faq)` → `the FAQ`. */
export function inlineToText(line: string): string {
  return line
    .replace(/<\/?[A-Za-z][^>]*>/g, ' ') // JSX / HTML tags (their text content stays)
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1') // images → alt
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1') // links → label
    .replace(/`([^`]*)`/g, '$1')
    .replace(/(\*\*|__)(.*?)\1/g, '$2')
    .replace(/(\*|_)(.*?)\1/g, '$2')
    .replace(/\{[^}]*\}/g, ' ') // JSX expressions
    .replace(/\s+/g, ' ')
    .trim();
}

/** Drops `import`/`export` statements (including a multi-line `export const frontmatter = {…}`). */
function stripEsm(source: string): string {
  const lines = source.split('\n');
  const out: string[] = [];
  let depth = 0;
  for (const line of lines) {
    if (depth > 0 || /^(import|export)\s/.test(line)) {
      for (const ch of line) {
        if (ch === '{' || ch === '[' || ch === '(') depth++;
        else if (ch === '}' || ch === ']' || ch === ')') depth--;
      }
      if (depth < 0) depth = 0;
      continue;
    }
    out.push(line);
  }
  return out.join('\n');
}

export function mdxToSections(source: string): GuideTextSection[] {
  const slugger = new GithubSlugger();
  const sections: GuideTextSection[] = [{ heading: null, anchor: null, text: '' }];
  let inFence = false;

  // Tags first, on the whole source: a JSX tag's attributes can span several lines.
  const body = stripEsm(source).replace(/<\/?[A-Za-z][^>]*>/g, ' ');
  for (const rawLine of body.split('\n')) {
    if (/^\s*(```|~~~)/.test(rawLine)) {
      inFence = !inFence;
      continue;
    }
    const heading = inFence ? null : /^(#{1,6})\s+(.*?)\s*#*\s*$/.exec(rawLine);
    if (heading) {
      const text = inlineToText(heading[2]);
      sections.push({ heading: text, anchor: slugger.slug(text), text: '' });
      continue;
    }
    const text = inlineToText(rawLine.replace(/^\s*(>|[-*+]|\d+\.)\s+/, '').replace(/^\|/, '').replace(/\|/g, ' '));
    if (text && !/^[-:\s]+$/.test(text)) {
      const current = sections[sections.length - 1];
      current.text = current.text ? `${current.text} ${text}` : text;
    }
  }

  return sections.filter((s) => s.heading !== null || s.text !== '');
}
