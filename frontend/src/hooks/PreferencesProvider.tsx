import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import axios from 'axios';
import { toast } from 'sonner';
import i18n from '../i18n';
import { preferencesApi } from '../api/preferences';
import { useAuth } from '../auth/AuthContext';
import { PreferencesContext, type Preferences } from './usePreferences';
import {
  ANONYMOUS_CACHE_KEY,
  MIGRATED_BY_KEY,
  PreferenceSaveQueue,
  decideInitialPreferences,
  mergeLoadedPreferences,
  mergeUploadPatch,
  readFirstPaint,
  readUnsaved,
  toUiPatch,
  userCacheKey,
  writeCache,
  writeUnsaved,
} from './preferencesSync';

function showSaveError(error: unknown) {
  // A 400 carries the reason in `message`; other failures (network, 5xx) only get the title.
  const message = axios.isAxiosError(error)
    ? (error.response?.data as { message?: string } | undefined)?.message
    : undefined;
  toast.error(i18n.t('approvalsDashboardSettingsLanding:settings.saveError'), {
    id: 'preferences-save-error',
    description: message,
  });
}

/**
 * ADR-0061: preferences follow the user through `/api/user/preferences`, with
 * `localStorage` as a per-user cache for the first paint. Before login (and on public
 * pages) there's no user, so the anonymous cache is used and nothing goes to the server.
 * Demo slides don't use this provider; they get `mockPreferences`.
 */
export default function PreferencesProvider({ children }: { readonly children: ReactNode }) {
  const { authenticated, userId } = useAuth();
  const user = authenticated ? userId : null;
  // A different user (or logging in) starts a fresh store with its own cache and queue.
  return (
    <PreferencesStore key={user ?? 'anonymous'} userId={user}>
      {children}
    </PreferencesStore>
  );
}

interface PreferencesStoreProps {
  readonly userId: string | null;
  readonly children: ReactNode;
}

function PreferencesStore({ userId, children }: PreferencesStoreProps) {
  const cacheKey = userId ? userCacheKey(userId) : ANONYMOUS_CACHE_KEY;
  const [prefs, setPrefs] = useState<Preferences>(() => readFirstPaint(userId));
  const prefsRef = useRef(prefs);
  // Keys the user changed on this page before the first GET settled (see below); not collected after.
  const changedRef = useRef(new Set<keyof Preferences>());
  const loadSettledRef = useRef(userId === null);
  // Anonymous visitors have nothing to load; a user is synced once the GET below settles.
  const [synced, setSynced] = useState(userId === null);
  const [queue] = useState(
    () =>
      new PreferenceSaveQueue({
        save: (patch) => preferencesApi.update(toUiPatch(patch)),
        onFailure: showSaveError,
        initial: userId ? readUnsaved(userId) : {},
        onUnsavedChange: (unsaved) => {
          if (userId) writeUnsaved(userId, unsaved);
        },
      }),
  );

  const apply = useCallback(
    (next: Preferences) => {
      prefsRef.current = next;
      setPrefs(next);
      writeCache(cacheKey, next);
    },
    [cacheKey],
  );

  useEffect(() => {
    void i18n.changeLanguage(prefs.locale);
  }, [prefs.locale]);

  // After login: the server's values replace the cache, or this browser's legacy values
  // are uploaded once. If the request fails, the cached values stay.
  useEffect(() => {
    if (!userId) return;
    let cancelled = false;
    preferencesApi
      .get()
      .then(({ ui }) => {
        if (cancelled) return;
        const initial = decideInitialPreferences({
          serverUi: ui ?? {},
          legacyRaw: localStorage.getItem(ANONYMOUS_CACHE_KEY),
          migratedBy: localStorage.getItem(MIGRATED_BY_KEY),
          userId,
        });
        // Changes made on this page since the request started win over what it returned, even
        // ones already saved (the response can be older than the save). So do changes not
        // saved yet, including ones left from an earlier page load; those are saved now.
        const unsaved = queue.unsaved;
        const changed = changedRef.current;
        apply(mergeLoadedPreferences(initial.prefs, prefsRef.current, changed, unsaved));
        const current = prefsRef.current;
        loadSettledRef.current = true;
        changedRef.current = new Set();
        if (initial.kind === 'upload') {
          queue.queue(mergeUploadPatch(initial.patch, current, changed, unsaved));
          void queue.flush().then((saved) => {
            if (saved) localStorage.setItem(MIGRATED_BY_KEY, userId);
          });
        } else if (Object.keys(unsaved).length > 0) {
          void queue.flush();
        }
        setSynced(true);
      })
      .catch(() => {
        if (!cancelled) {
          loadSettledRef.current = true;
          setSynced(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [userId, queue, apply]);

  // Save what's still queued when the store goes away (e.g. a different user).
  useEffect(() => () => void queue.flush(), [queue]);

  const update = useCallback(
    (patch: Partial<Preferences>) => {
      apply({ ...prefsRef.current, ...patch });
      if (!loadSettledRef.current) {
        for (const key of Object.keys(patch) as (keyof Preferences)[]) changedRef.current.add(key);
      }
      if (userId) queue.queue(patch);
    },
    [apply, userId, queue],
  );

  const value = useMemo(() => ({ prefs, update, synced }), [prefs, update, synced]);

  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>;
}
