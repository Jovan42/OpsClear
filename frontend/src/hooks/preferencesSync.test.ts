import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { defaults, type Preferences } from './usePreferences';
import {
  ANONYMOUS_CACHE_KEY,
  MIGRATED_BY_KEY,
  PreferenceSaveQueue,
  decideInitialPreferences,
  mergeLoadedPreferences,
  mergeUploadPatch,
  readFirstPaint,
  readUnsaved,
  unsavedKey,
  userCacheKey,
  writeUnsaved,
} from './preferencesSync';

const ALICE = 'alice-id';
const BOB = 'bob-id';

// Vitest runs in node: a minimal in-memory localStorage.
function stubLocalStorage(initial: Record<string, string> = {}): Map<string, string> {
  const store = new Map(Object.entries(initial));
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, value),
    removeItem: (key: string) => void store.delete(key),
  });
  return store;
}

describe('readFirstPaint', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('uses the anonymous cache before login', () => {
    stubLocalStorage({ [ANONYMOUS_CACHE_KEY]: JSON.stringify({ theme: 'dark' }) });
    expect(readFirstPaint(null).theme).toBe('dark');
  });

  it("uses the user's own cache when there is one", () => {
    stubLocalStorage({
      [ANONYMOUS_CACHE_KEY]: JSON.stringify({ theme: 'dark' }),
      [userCacheKey(ALICE)]: JSON.stringify({ theme: 'light' }),
    });
    expect(readFirstPaint(ALICE).theme).toBe('light');
  });

  it('uses the legacy values when they may still become this user', () => {
    stubLocalStorage({ [ANONYMOUS_CACHE_KEY]: JSON.stringify({ theme: 'dark' }) });
    expect(readFirstPaint(ALICE).theme).toBe('dark');
  });

  it("never shows another user's legacy values", () => {
    stubLocalStorage({ [ANONYMOUS_CACHE_KEY]: JSON.stringify({ theme: 'dark' }), [MIGRATED_BY_KEY]: ALICE });
    expect(readFirstPaint(BOB)).toEqual(defaults);
  });

  it('gives the defaults for an unreadable cache', () => {
    stubLocalStorage({ [userCacheKey(ALICE)]: '{not json' });
    expect(readFirstPaint(ALICE)).toEqual(defaults);
  });
});

describe('unsaved changes', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('are stored per user, sanitized when read, and removed once empty', () => {
    const store = stubLocalStorage();
    writeUnsaved(ALICE, { theme: 'dark' });
    expect(store.get(unsavedKey(ALICE))).toBe('{"theme":"dark"}');
    expect(readUnsaved(BOB)).toEqual({});

    store.set(unsavedKey(ALICE), JSON.stringify({ theme: 'dark', locale: 'de' }));
    expect(readUnsaved(ALICE)).toEqual({ theme: 'dark' });

    writeUnsaved(ALICE, {});
    expect(store.has(unsavedKey(ALICE))).toBe(false);
  });
});

describe('cache keys', () => {
  it('uses a per-user key next to the anonymous (legacy) one', () => {
    expect(ANONYMOUS_CACHE_KEY).toBe('opsclear:preferences');
    expect(userCacheKey(ALICE)).toBe('opsclear:preferences:alice-id');
    expect(MIGRATED_BY_KEY).toBe('opsclear:preferences:migratedBy');
  });
});

describe('decideInitialPreferences', () => {
  const legacy = JSON.stringify({ theme: 'dark', locale: 'sr' });

  it('uses the server values, sanitized over the defaults, when the server has any', () => {
    const result = decideInitialPreferences({
      serverUi: { theme: 'light', dueSoonDays: 99, unknown: 'x' },
      legacyRaw: legacy,
      migratedBy: null,
      userId: ALICE,
    });
    expect(result).toEqual({ kind: 'server', prefs: { ...defaults, theme: 'light' } });
  });

  it('uploads the legacy key when the server is empty and it was never uploaded', () => {
    const result = decideInitialPreferences({ serverUi: {}, legacyRaw: legacy, migratedBy: null, userId: ALICE });
    expect(result).toEqual({
      kind: 'upload',
      prefs: { ...defaults, theme: 'dark', locale: 'sr' },
      patch: { theme: 'dark', locale: 'sr' },
    });
  });

  it('uploads again for the user the marker names', () => {
    const result = decideInitialPreferences({ serverUi: {}, legacyRaw: legacy, migratedBy: ALICE, userId: ALICE });
    expect(result.kind).toBe('upload');
  });

  it('does not upload for a different user than the marker names', () => {
    const result = decideInitialPreferences({ serverUi: {}, legacyRaw: legacy, migratedBy: ALICE, userId: BOB });
    expect(result).toEqual({ kind: 'defaults', prefs: defaults });
  });

  it('uses the defaults when the server is empty and there is no legacy key', () => {
    const result = decideInitialPreferences({ serverUi: {}, legacyRaw: null, migratedBy: null, userId: ALICE });
    expect(result).toEqual({ kind: 'defaults', prefs: defaults });
  });

  it.each(['{not json', '"dark"', '[]', JSON.stringify({ theme: 'purple', other: 1 })])(
    'uses the defaults when the legacy key %s has nothing valid',
    (legacyRaw) => {
      const result = decideInitialPreferences({ serverUi: {}, legacyRaw, migratedBy: null, userId: ALICE });
      expect(result).toEqual({ kind: 'defaults', prefs: defaults });
    },
  );

  it('uploads only the valid legacy values', () => {
    const result = decideInitialPreferences({
      serverUi: {},
      legacyRaw: JSON.stringify({ theme: 'dark', locale: 'de', stray: true }),
      migratedBy: null,
      userId: ALICE,
    });
    expect(result).toMatchObject({ kind: 'upload', patch: { theme: 'dark' } });
  });
});

describe('PreferenceSaveQueue', () => {
  let saves: Partial<Preferences>[];
  let failNext: boolean;
  let onFailure: Mock<(error: unknown) => void>;
  let queue: PreferenceSaveQueue;

  beforeEach(() => {
    vi.useFakeTimers();
    saves = [];
    failNext = false;
    onFailure = vi.fn<(error: unknown) => void>();
    queue = new PreferenceSaveQueue({
      save: async (patch) => {
        saves.push(patch);
        if (failNext) {
          failNext = false;
          throw new Error('offline');
        }
      },
      onFailure,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('saves the changed keys together 500 ms after the last change', async () => {
    queue.queue({ theme: 'dark' });
    await vi.advanceTimersByTimeAsync(400);
    queue.queue({ locale: 'sr' });
    await vi.advanceTimersByTimeAsync(400);
    expect(saves).toEqual([]);

    await vi.advanceTimersByTimeAsync(100);
    expect(saves).toEqual([{ theme: 'dark', locale: 'sr' }]);
  });

  it('keeps a failed change and retries it with the next one, reporting the failure once', async () => {
    failNext = true;
    queue.queue({ theme: 'dark' });
    await vi.advanceTimersByTimeAsync(500);
    expect(onFailure).toHaveBeenCalledTimes(1);
    expect(queue.unsaved).toEqual({ theme: 'dark' });

    queue.queue({ locale: 'sr' });
    await vi.advanceTimersByTimeAsync(500);
    expect(saves[1]).toEqual({ theme: 'dark', locale: 'sr' });
    expect(queue.unsaved).toEqual({});
  });

  it('reports one failure per run of failures', async () => {
    queue = new PreferenceSaveQueue({ save: () => Promise.reject(new Error('down')), onFailure });
    queue.queue({ theme: 'dark' });
    await vi.advanceTimersByTimeAsync(500);
    queue.queue({ theme: 'light' });
    await vi.advanceTimersByTimeAsync(500);
    expect(onFailure).toHaveBeenCalledTimes(1);
    expect(queue.unsaved).toEqual({ theme: 'light' });
  });

  it('reports again after a success followed by a new failure', async () => {
    failNext = true;
    queue.queue({ theme: 'dark' });
    await vi.advanceTimersByTimeAsync(500);
    queue.queue({ locale: 'sr' });
    await vi.advanceTimersByTimeAsync(500);
    failNext = true;
    queue.queue({ rowDensity: 'COMPACT' });
    await vi.advanceTimersByTimeAsync(500);
    expect(onFailure).toHaveBeenCalledTimes(2);
  });

  it('flush saves right away and says whether it worked', async () => {
    queue.queue({ theme: 'dark' });
    await expect(queue.flush()).resolves.toBe(true);
    expect(saves).toEqual([{ theme: 'dark' }]);

    failNext = true;
    queue.queue({ theme: 'light' });
    await expect(queue.flush()).resolves.toBe(false);
  });

  it('flush with nothing queued saves nothing', async () => {
    await expect(queue.flush()).resolves.toBe(true);
    expect(saves).toEqual([]);
  });

  it('reports everything not saved yet, including a save in flight, and saves leftovers from an earlier load', async () => {
    const reported: Partial<Preferences>[] = [];
    let resolve: () => void = () => {};
    queue = new PreferenceSaveQueue({
      save: () => new Promise<void>((r) => { resolve = r; }),
      onFailure,
      initial: { locale: 'sr' },
      onUnsavedChange: (unsaved) => reported.push(unsaved),
    });
    expect(queue.unsaved).toEqual({ locale: 'sr' });

    queue.queue({ theme: 'dark' });
    expect(reported.at(-1)).toEqual({ locale: 'sr', theme: 'dark' });

    const flushing = queue.flush();
    expect(queue.unsaved).toEqual({ locale: 'sr', theme: 'dark' });
    resolve();
    await flushing;
    expect(reported.at(-1)).toEqual({});
  });

  it('runs one save at a time', async () => {
    const resolvers: (() => void)[] = [];
    const sent: Partial<Preferences>[] = [];
    queue = new PreferenceSaveQueue({
      save: (patch) => {
        sent.push(patch);
        return new Promise<void>((r) => resolvers.push(r));
      },
      onFailure,
    });
    queue.queue({ theme: 'dark' });
    const first = queue.flush();
    queue.queue({ locale: 'sr' });
    const second = queue.flush();
    await Promise.resolve();
    expect(sent).toEqual([{ theme: 'dark' }]);

    resolvers[0]();
    await first;
    await vi.waitFor(() => expect(sent).toHaveLength(2));
    expect(sent[1]).toEqual({ locale: 'sr' });
    resolvers[1]();
    await expect(second).resolves.toBe(true);
  });

  it('a change made while a save is in flight wins over the failed one', async () => {
    let reject: (e: Error) => void = () => {};
    queue = new PreferenceSaveQueue({
      save: () => new Promise((_, r) => { reject = r; }),
      onFailure,
    });
    queue.queue({ theme: 'dark' });
    const flushing = queue.flush();
    queue.queue({ theme: 'light' });
    reject(new Error('down'));
    await flushing;
    expect(queue.unsaved).toEqual({ theme: 'light' });
    queue.cancel();
  });
});

describe('mergeLoadedPreferences', () => {
  const server: Preferences = { ...defaults, theme: 'light', hideCompletedFromAll: false };

  it('keeps a change that was already saved while the GET was in flight (the response is older)', () => {
    // The user chose Dark, the save finished (nothing unsaved), then the older GET answered.
    const current: Preferences = { ...server, theme: 'dark' };
    const merged = mergeLoadedPreferences(server, current, new Set(['theme']), {});
    expect(merged.theme).toBe('dark');
  });

  it('keeps unsaved changes, and takes the server value for everything the user did not touch', () => {
    const current: Preferences = { ...server, hideCompletedFromAll: true };
    const merged = mergeLoadedPreferences(server, current, new Set(), { hideCompletedFromAll: true });
    expect(merged).toEqual({ ...server, hideCompletedFromAll: true });
  });

  it('uses the server values when the user changed nothing', () => {
    expect(mergeLoadedPreferences(server, defaults, new Set(), {})).toEqual(server);
  });
});

describe('merge precedence', () => {
  const server: Preferences = { ...defaults, theme: 'light' };

  it('an unsaved value beats a changed one for the same key (it is the newer change)', () => {
    // Saved Dark, then chose System, which is not saved yet.
    const current: Preferences = { ...server, theme: 'dark' };
    const merged = mergeLoadedPreferences(server, current, new Set(['theme']), { theme: 'system' });
    expect(merged.theme).toBe('system');
  });

  it('the first-login upload does not overwrite a change saved while the load was in flight', () => {
    // Legacy key says Light + compact rows. The user chose Dark meanwhile and it was saved.
    const current: Preferences = { ...defaults, theme: 'dark', rowDensity: 'COMPACT' };
    const patch = mergeUploadPatch({ theme: 'light', rowDensity: 'COMPACT' }, current, new Set(['theme']), {});
    expect(patch).toEqual({ theme: 'dark', rowDensity: 'COMPACT' });
  });

  it('the upload also keeps unsaved values over changed and legacy ones', () => {
    const current: Preferences = { ...defaults, theme: 'dark' };
    const patch = mergeUploadPatch({ theme: 'light' }, current, new Set(['theme']), { theme: 'system' });
    expect(patch).toEqual({ theme: 'system' });
  });
});
