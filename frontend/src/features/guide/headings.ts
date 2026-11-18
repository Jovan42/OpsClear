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
