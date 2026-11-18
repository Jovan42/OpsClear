import type { ComponentType } from 'react';
import type { MDXProps } from 'mdx/types';

// ADR-0062: guide pages are `<lang>/NN-<slug>.mdx`. Both globs are lazy, so every page (and
// its plain text for search) is its own chunk, loaded only when the guide asks for it: the
// guide's content never grows the main bundle.

export type GuideLang = 'en' | 'sr';

export interface GuideFrontmatter {
  title: string;
  description?: string;
}

interface GuideModule {
  default: ComponentType<MDXProps>;
  frontmatter?: GuideFrontmatter;
}

const modules = import.meta.glob<GuideModule>('./{en,sr}/*.mdx');
const rawModules = import.meta.glob<string>('./{en,sr}/*.mdx', { query: '?raw', import: 'default' });

const FILE_PATTERN = /^\.\/(en|sr)\/\d+-(.+)\.mdx$/;

function keyFor(paths: string[], lang: GuideLang, slug: string): string | undefined {
  return paths.find((path) => {
    const match = FILE_PATTERN.exec(path);
    return match?.[1] === lang && match[2] === slug;
  });
}

export interface LoadedGuidePage {
  Content: ComponentType<MDXProps>;
  frontmatter: GuideFrontmatter | null;
  /** The language actually shown: English when the page isn't translated yet. */
  lang: GuideLang;
}

/**
 * Loads a guide page in `lang`, falling back to English. Resolves to null when the page
 * doesn't exist yet in any language (it's still "Coming soon").
 */
export async function loadGuidePage(lang: GuideLang, slug: string): Promise<LoadedGuidePage | null> {
  const paths = Object.keys(modules);
  const langs: GuideLang[] = lang === 'en' ? ['en'] : [lang, 'en'];
  for (const candidate of langs) {
    const key = keyFor(paths, candidate, slug);
    if (key) {
      const mod = await modules[key]();
      return { Content: mod.default, frontmatter: mod.frontmatter ?? null, lang: candidate };
    }
  }
  return null;
}

export interface RawGuidePage {
  slug: string;
  source: string;
}

/** The MDX source of every page written in `lang`, for the search index. */
export async function loadRawGuidePages(lang: GuideLang): Promise<RawGuidePage[]> {
  const entries = Object.entries(rawModules).flatMap(([path, load]) => {
    const match = FILE_PATTERN.exec(path);
    return match?.[1] === lang ? [{ slug: match[2], load }] : [];
  });
  return Promise.all(entries.map(async ({ slug, load }) => ({ slug, source: await load() })));
}
