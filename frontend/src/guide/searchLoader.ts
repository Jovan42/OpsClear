import type { GuideSearch } from './searchIndex';

// The guide search index's load state, kept free of React so it can be unit tested.

export type SearchIndexState =
  | { status: 'idle' }
  | { status: 'loading'; lang: string }
  | { status: 'ready'; lang: string; search: GuideSearch }
  | { status: 'error'; lang: string };

/**
 * Whether to (re)build the index for `lang`: on first use and after a language change. A build
 * that failed for `lang` isn't retried here: the browser caches a failed chunk import for the
 * rest of the page's life, so importing it again fails the same way. The way to retry is a
 * page reload (the search error's "Try again" button).
 */
export function shouldBuildIndex(state: SearchIndexState, lang: string): boolean {
  return state.status === 'idle' || state.lang !== lang;
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
