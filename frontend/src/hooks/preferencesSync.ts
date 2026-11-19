// ADR-0061: server-synced preferences. The React-free parts of PreferencesProvider (cache
// keys, the first-load decision and the debounced save queue), so they can be unit tested.

import type { UiPreferenceValue } from '../types';
import { defaults, sanitize, sanitizePartial, type Preferences } from './usePreferences';

/**
 * The anonymous cache (before login, public pages). Also the legacy key: before ADR-0061
 * every preference lived here, shared by everyone using the browser.
 */
export const ANONYMOUS_CACHE_KEY = 'opsclear:preferences';
/** Which user the legacy key was uploaded for, so it's never uploaded for anyone else. */
export const MIGRATED_BY_KEY = 'opsclear:preferences:migratedBy';
/** Debounce for saving changes to the server. */
export const SAVE_DELAY_MS = 500;

export function userCacheKey(userId: string): string {
  return `${ANONYMOUS_CACHE_KEY}:${userId}`;
}

function parse(raw: string | null): unknown {
  if (raw === null) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/** Preferences from a cache key; defaults when it's missing or unreadable. */
export function readCache(key: string): Preferences {
  try {
    return sanitize(parse(localStorage.getItem(key)));
  } catch {
    return defaults;
  }
}

/**
 * What to draw before the server answers: the user's own cache, or (first load after
 * ADR-0061) the legacy values if they may become this user's, else the defaults.
 */
export function readFirstPaint(userId: string | null): Preferences {
  if (!userId) return readCache(ANONYMOUS_CACHE_KEY);
  try {
    if (localStorage.getItem(userCacheKey(userId)) !== null) return readCache(userCacheKey(userId));
    const migratedBy = localStorage.getItem(MIGRATED_BY_KEY);
    return migratedBy === null || migratedBy === userId ? readCache(ANONYMOUS_CACHE_KEY) : defaults;
  } catch {
    return defaults;
  }
}

export function writeCache(key: string, prefs: Preferences): void {
  try {
    localStorage.setItem(key, JSON.stringify(prefs));
  } catch {
    // Storage full or blocked (private browsing): the server still has the values.
  }
}

/**
 * Changes not saved to the server yet (a reload inside the save delay, or a failed save).
 * Kept so the next page load saves them instead of losing them to the server's values.
 */
export function unsavedKey(userId: string): string {
  return `${userCacheKey(userId)}:unsaved`;
}

export function readUnsaved(userId: string): Partial<Preferences> {
  try {
    return sanitizePartial(parse(localStorage.getItem(unsavedKey(userId))));
  } catch {
    return {};
  }
}

export function writeUnsaved(userId: string, unsaved: Partial<Preferences>): void {
  try {
    if (Object.keys(unsaved).length === 0) localStorage.removeItem(unsavedKey(userId));
    else localStorage.setItem(unsavedKey(userId), JSON.stringify(unsaved));
  } catch {
    // Storage blocked: unsaved changes only live until the page closes.
  }
}

export type InitialPreferences =
  /** The server has values: use them (sanitized over the defaults). */
  | { kind: 'server'; prefs: Preferences }
  /** The server is empty and this browser's legacy values may be uploaded for this user. */
  | { kind: 'upload'; prefs: Preferences; patch: Partial<Preferences> }
  /** Nothing saved anywhere that applies to this user. */
  | { kind: 'defaults'; prefs: Preferences };

interface InitialInput {
  /** `ui` from `GET /api/user/preferences`. */
  serverUi: Record<string, unknown>;
  /** The raw legacy `opsclear:preferences` value, or null. */
  legacyRaw: string | null;
  /** The raw `opsclear:preferences:migratedBy` value, or null. */
  migratedBy: string | null;
  userId: string;
}

/**
 * What to show after login, once the server's preferences are in. Server values win. If the
 * server has none, the legacy key is uploaded once, and only for the user it was first
 * uploaded for (or anyone, if it never was), so a second user in the same browser doesn't
 * inherit the first one's settings.
 */
export function decideInitialPreferences({
  serverUi,
  legacyRaw,
  migratedBy,
  userId,
}: InitialInput): InitialPreferences {
  if (Object.keys(serverUi).length > 0) return { kind: 'server', prefs: sanitize(serverUi) };

  const legacyBelongsToUser = migratedBy === null || migratedBy === userId;
  if (legacyRaw !== null && legacyBelongsToUser) {
    const patch = sanitizePartial(parse(legacyRaw));
    if (Object.keys(patch).length > 0) {
      return { kind: 'upload', prefs: { ...defaults, ...patch }, patch };
    }
  }
  return { kind: 'defaults', prefs: defaults };
}

/**
 * The preferences to show once the first GET answers. The server's values (`initial`) lose to
 * anything the user changed on this page since the request started (`changedKeys`, even
 * if that change was already saved: the GET may have been answered before the save, so it
 * is older) and to changes not saved yet (`unsaved`, including ones left by an earlier load).
 */
export function mergeLoadedPreferences(
  initial: Preferences,
  current: Preferences,
  changedKeys: ReadonlySet<keyof Preferences>,
  unsaved: Partial<Preferences>,
): Preferences {
  const changed: Partial<Preferences> = {};
  for (const key of changedKeys) Object.assign(changed, { [key]: current[key] });
  return { ...initial, ...changed, ...unsaved };
}

export function toUiPatch(patch: Partial<Preferences>): Record<string, UiPreferenceValue> {
  return patch as Record<string, UiPreferenceValue>;
}

interface SaveQueueOptions {
  save: (patch: Partial<Preferences>) => Promise<unknown>;
  /** Called once per run of failures; a successful save starts a new run. */
  onFailure: (error: unknown) => void;
  /** Changes left unsaved by an earlier page load, saved with the next flush. */
  initial?: Partial<Preferences>;
  /** Called with everything not yet saved whenever that changes, to persist it. */
  onUnsavedChange?: (unsaved: Partial<Preferences>) => void;
  delayMs?: number;
}

/**
 * Collects changed keys and saves them together after `delayMs` without changes. One save
 * runs at a time. A failed save keeps its keys queued (newer changes to the same key win),
 * so the next change retries them.
 */
export class PreferenceSaveQueue {
  private pending: Partial<Preferences>;
  private inFlight: Partial<Preferences> = {};
  private running: Promise<boolean> | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private failing = false;
  private readonly save: SaveQueueOptions['save'];
  private readonly onFailure: SaveQueueOptions['onFailure'];
  private readonly onUnsavedChange: SaveQueueOptions['onUnsavedChange'];
  private readonly delayMs: number;

  constructor({ save, onFailure, initial = {}, onUnsavedChange, delayMs = SAVE_DELAY_MS }: SaveQueueOptions) {
    this.save = save;
    this.onFailure = onFailure;
    this.pending = { ...initial };
    this.onUnsavedChange = onUnsavedChange;
    this.delayMs = delayMs;
  }

  /** Changes not saved yet (queued or being saved), newest values. */
  get unsaved(): Partial<Preferences> {
    return { ...this.inFlight, ...this.pending };
  }

  queue(patch: Partial<Preferences>): void {
    this.pending = { ...this.pending, ...patch };
    this.notify();
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.flush(), this.delayMs);
  }

  /** Saves what's queued now. Resolves to whether it all saved. */
  async flush(): Promise<boolean> {
    this.cancel();
    while (this.running) await this.running;
    if (Object.keys(this.pending).length === 0) return true;
    this.running = this.send();
    try {
      return await this.running;
    } finally {
      this.running = null;
    }
  }

  /** Stops the timer without saving. */
  cancel(): void {
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = null;
  }

  private async send(): Promise<boolean> {
    const patch = this.pending;
    this.inFlight = patch;
    this.pending = {};
    try {
      await this.save(patch);
      this.failing = false;
      return true;
    } catch (error) {
      this.pending = { ...patch, ...this.pending };
      if (!this.failing) {
        this.failing = true;
        this.onFailure(error);
      }
      return false;
    } finally {
      this.inFlight = {};
      this.notify();
    }
  }

  private notify(): void {
    this.onUnsavedChange?.(this.unsaved);
  }
}
