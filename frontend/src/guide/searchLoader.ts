import type { GuideSearch } from './searchIndex';

// The guide search index's load state, kept free of React so it can be unit tested.

export type SearchIndexState =
  | { status: 'idle' }
  | { status: 'loading'; lang: string }
  | { status: 'ready'; lang: string; search: GuideSearch }
  | { status: 'error'; lang: string };

/**
 * Whether to (re)build the index for `lang`. A build that failed for `lang` (e.g. a page chunk
 * didn't load) is only retried when `retryError` is set: on the next focus of the search box,
 * not on every keystroke.
 */
export function shouldBuildIndex(state: SearchIndexState, lang: string, retryError: boolean): boolean {
  if (state.status === 'idle' || state.lang !== lang) return true;
  if (state.status === 'error') return retryError;
  return false;
}

/** Builds the index for `lang`, reporting `loading` and then `ready` or `error`. Never rejects. */
export function buildIndexState(
  lang: string,
  build: () => Promise<GuideSearch>,
  onChange: (state: SearchIndexState) => void,
): Promise<void> {
  onChange({ status: 'loading', lang });
  return build().then(
    (search) => {
      onChange({ status: 'ready', lang, search });
    },
    () => {
      onChange({ status: 'error', lang });
    },
  );
}
