import { describe, expect, it } from 'vitest';
import { loadGuidePage } from './content';
import { GUIDE_PAGES } from './pages';

// ADR-0062: a page with no content in any language shows the "Coming soon" placeholder
// (`loadGuidePage` resolves to null). Every page is written now, so none may be left without
// content, and the placeholder stays reachable for a page added to `pages.ts` before its text.
describe('loadGuidePage', () => {
  it('has content in both languages for every page in the guide', async () => {
    for (const { slug } of GUIDE_PAGES) {
      for (const lang of ['en', 'sr'] as const) {
        const loaded = await loadGuidePage(lang, slug);
        expect(loaded, `${lang}/${slug}`).not.toBeNull();
        expect(loaded?.lang, `${lang}/${slug}`).toBe(lang);
      }
    }
  });

  it('resolves to null for a page that has no content, which is what shows "Coming soon"', async () => {
    expect(await loadGuidePage('en', 'no-such-page')).toBeNull();
    expect(await loadGuidePage('sr', 'no-such-page')).toBeNull();
  });
});
