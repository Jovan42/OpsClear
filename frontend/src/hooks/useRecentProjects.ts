// ADR-0047: quick project switcher recency tracking — localStorage only, same
// pattern as ADR-0018/0023 preferences, no backend involved. Kept as a standalone
// key (not folded into the shared `opsclear:preferences` blob) since this is
// write-heavy (updated on every project page load) and unrelated in shape to the
// rest of that settings object.
const STORAGE_KEY = 'opsclear:lastVisitedProjects';
const MAX_ENTRIES = 10;

// Keyed by project friendlyId — that's what's already in the URL wherever a
// visit is recorded or a switcher target is resolved, no extra project lookup.
type RecentProjectsMap = Record<string, number>;

function load(): RecentProjectsMap {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

export function getLastVisitedProjects(): RecentProjectsMap {
  return load();
}

export function recordProjectVisit(projectFriendlyId: string) {
  const map = load();
  map[projectFriendlyId] = Date.now();

  // Cap at the 10 most recent — pruned by recency, never dropping the entry
  // just written this visit.
  const capped = Object.fromEntries(
    Object.entries(map)
      .sort(([, a], [, b]) => b - a)
      .slice(0, MAX_ENTRIES),
  );

  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(capped));
  } catch {
    // Ignore — e.g. private-browsing storage quota. Recency tracking degrading
    // to alphabetical-only is an acceptable fallback, not worth surfacing.
  }
}
