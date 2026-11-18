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
    expect(shouldBuildIndex({ status: 'idle' }, 'en', false)).toBe(true);
    expect(shouldBuildIndex({ status: 'ready', lang: 'en', search }, 'sr', false)).toBe(true);
    expect(shouldBuildIndex({ status: 'loading', lang: 'en' }, 'sr', false)).toBe(true);
  });

  it("doesn't rebuild while loading or once ready", () => {
    expect(shouldBuildIndex({ status: 'loading', lang: 'en' }, 'en', true)).toBe(false);
    expect(shouldBuildIndex({ status: 'ready', lang: 'en', search }, 'en', true)).toBe(false);
  });

  it('retries a failed build only when asked (the next focus)', () => {
    expect(shouldBuildIndex({ status: 'error', lang: 'en' }, 'en', false)).toBe(false);
    expect(shouldBuildIndex({ status: 'error', lang: 'en' }, 'en', true)).toBe(true);
  });
});
