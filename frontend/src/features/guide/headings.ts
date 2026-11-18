export interface GuideHeading {
  id: string;
  text: string;
  level: 2 | 3;
}

/** The page's h2/h3 headings (rehype-slug gives each an id), in document order. */
export function readHeadings(root: HTMLElement): GuideHeading[] {
  return Array.from(root.querySelectorAll<HTMLHeadingElement>('h2[id], h3[id]')).map((el) => ({
    id: el.id,
    text: el.textContent?.trim() ?? '',
    level: el.tagName === 'H2' ? 2 : 3,
  }));
}

export interface ActiveHeadingInput {
  /** Heading ids in document order. */
  ids: string[];
  /** Ids currently in the top 30% of the viewport. */
  inView: ReadonlySet<string>;
  /** Ids whose top has scrolled above the viewport. */
  passed: ReadonlySet<string>;
  /** The page scrolls and is scrolled to the bottom. */
  atBottom: boolean;
}

/**
 * The heading "On this page" highlights, or null to keep the current one. At the bottom of a
 * page the last heading wins: on a short page it can't reach the top 30%, so clicking it
 * would otherwise leave the previous heading highlighted. Otherwise the first heading in the
 * top 30%, else the last one scrolled past.
 */
export function pickActiveHeading({ ids, inView, passed, atBottom }: ActiveHeadingInput): string | null {
  if (ids.length === 0) return null;
  if (atBottom) return ids[ids.length - 1];
  const first = ids.find((id) => inView.has(id));
  if (first) return first;
  const scrolledPast = ids.filter((id) => passed.has(id));
  return scrolledPast.length > 0 ? scrolledPast[scrolledPast.length - 1] : null;
}
