import { describe, expect, it } from 'vitest';
import { buildIndexState, shouldBuildIndex, type SearchIndexState } from './searchLoader';

const search = () => [];

describe('buildIndexState', () => {
  it('reports loading then ready when the index builds', async () => {
    const states: SearchIndexState[] = [];
    await buildIndexState('en', () => Promise.resolve(search), (s) => states.push(s));
    expect(states).toEqual([
      { status: 'loading', lang: 'en' },
      { status: 'ready', lang: 'en', search },
    ]);
  });

  it('reports loading then error, without rejecting, when a page chunk fails to load', async () => {
    const states: SearchIndexState[] = [];
    await expect(
      buildIndexState('sr', () => Promise.reject(new Error('chunk failed')), (s) => states.push(s)),
    ).resolves.toBeUndefined();
    expect(states).toEqual([
      { status: 'loading', lang: 'sr' },
      { status: 'error', lang: 'sr' },
    ]);
  });
});

describe('shouldBuildIndex', () => {
  it('builds on first use and after a language change', () => {
    expect(shouldBuildIndex({ status: 'idle' }, 'en')).toBe(true);
    expect(shouldBuildIndex({ status: 'ready', lang: 'en', search }, 'sr')).toBe(true);
    expect(shouldBuildIndex({ status: 'loading', lang: 'en' }, 'sr')).toBe(true);
    expect(shouldBuildIndex({ status: 'error', lang: 'en' }, 'sr')).toBe(true);
  });

  it("doesn't rebuild while loading, once ready, or after a failure in the same language", () => {
    expect(shouldBuildIndex({ status: 'loading', lang: 'en' }, 'en')).toBe(false);
    expect(shouldBuildIndex({ status: 'ready', lang: 'en', search }, 'en')).toBe(false);
    // A failed chunk import stays failed for the page's life; the retry is a reload.
    expect(shouldBuildIndex({ status: 'error', lang: 'en' }, 'en')).toBe(false);
  });
});
