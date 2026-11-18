import MiniSearch from 'minisearch';
import { loadRawGuidePages, type GuideLang } from './content';
import { mdxToSections } from './searchText';

export interface GuideSearchResult {
  id: string;
  slug: string;
  anchor: string | null;
  heading: string | null;
  pageTitle: string;
}

interface GuideSearchDoc extends GuideSearchResult {
  text: string;
}

export type GuideSearch = (query: string) => GuideSearchResult[];

/**
 * ADR-0062: builds the guide's search index in the browser, one document per heading
 * section, from the plain text of every page shown in `lang` (untranslated pages are
 * indexed in English, as they're shown). Small enough (~25 pages) to build on first use.
 */
export async function buildGuideSearch(lang: GuideLang, titleFor: (slug: string) => string): Promise<GuideSearch> {
  const pages = await loadRawGuidePages(lang);
  if (lang !== 'en') {
    const translated = new Set(pages.map((p) => p.slug));
    const english = await loadRawGuidePages('en');
    pages.push(...english.filter((p) => !translated.has(p.slug)));
  }

  const docs: GuideSearchDoc[] = pages.flatMap(({ slug, source }) =>
    mdxToSections(source).map((section, i) => ({
      id: `${slug}#${i}`,
      slug,
      anchor: section.anchor,
      heading: section.heading,
      pageTitle: titleFor(slug),
      text: section.text,
    })),
  );

  const index = new MiniSearch<GuideSearchDoc>({
    fields: ['pageTitle', 'heading', 'text'],
    storeFields: ['slug', 'anchor', 'heading', 'pageTitle'],
    // Serbian: "cetiri" finds "četiri" (people often type without diacritics).
    processTerm: (term) => term.toLowerCase().replace(/đ/g, 'dj').normalize('NFD').replace(/\p{M}/gu, ''),
    searchOptions: { boost: { pageTitle: 3, heading: 2 }, prefix: true, fuzzy: 0.2 },
  });
  index.addAll(docs);

  return (query) =>
    index
      .search(query)
      .slice(0, 8)
      .map((r) => ({
        id: String(r.id),
        slug: r.slug as string,
        anchor: (r.anchor as string | null) ?? null,
        heading: (r.heading as string | null) ?? null,
        pageTitle: r.pageTitle as string,
      }));
}
