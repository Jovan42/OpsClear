# ADR-0019: Dark Mode — Implementation Strategy

**Status:** Accepted
**Date:** 2026-03-10
**Author:** Jovan Manojlovic

## Context

Users can now select a theme preference (Light / Dark / System) via the settings page
introduced in ADR-0018. This ADR decides how that preference is applied to the UI:

- How dark mode is activated on the DOM
- Which Tailwind strategy to use (`media` vs `class`)
- Where the theme-application logic lives
- How the `system` option maps to the OS `prefers-color-scheme` media query
- How to avoid a flash of incorrect theme on page load (FOIT)

---

## Decision

### 1. Tailwind dark mode strategy — `class`

Tailwind supports two dark mode strategies:

| Strategy | How it works |
|----------|--------------|
| `media`  | Applies `dark:` variants when the OS `prefers-color-scheme: dark` matches |
| `class`  | Applies `dark:` variants when a `dark` class is present on a parent element |

**Decision: `class` strategy.**

The `media` strategy cannot be overridden by the user's explicit Light/Dark choice —
it always follows the OS. The `class` strategy lets us programmatically set the theme
to Light, Dark, or System by toggling a class on `<html>`, which is required for a
user-controlled theme toggle.

```js
// tailwind.config.js / CSS config
darkMode: 'class'
```

### 2. DOM application — class on `<html>`

The `dark` class is toggled on the `<html>` element (document root).

- `Light` — remove `dark` class
- `Dark` — add `dark` class
- `System` — add or remove `dark` class based on `window.matchMedia('(prefers-color-scheme: dark)').matches`

For `System`, a `MediaQueryList` event listener updates the class whenever the OS
preference changes while the app is open.

### 3. Where the logic lives — `useTheme` hook

A dedicated `useTheme` hook reads the current theme from `usePreferences` and applies
the DOM class as a side effect. It is mounted once at the app root (`App.tsx`) so the
class is active for the entire component tree.

```ts
// hooks/useTheme.ts
export function useTheme() {
  const { prefs } = usePreferences();

  useEffect(() => {
    const root = document.documentElement;
    const apply = (dark: boolean) =>
      dark ? root.classList.add('dark') : root.classList.remove('dark');

    if (prefs.theme === 'dark') {
      apply(true);
      return;
    }
    if (prefs.theme === 'light') {
      apply(false);
      return;
    }

    // system
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    apply(mq.matches);
    const handler = (e: MediaQueryListEvent) => apply(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, [prefs.theme]);
}
```

`useTheme` has no return value — it is a pure side-effect hook.

### 4. Flash of incorrect theme (FOIT) — accepted for MVP

On a full page load, React renders before the `useEffect` fires, which can briefly
show the wrong theme. Two mitigation approaches exist:

| Approach | How |
|----------|-----|
| Inline `<script>` in `index.html` | Reads localStorage and sets the class before React mounts; zero flash |
| CSS `color-scheme` + system default | Works only when theme is `system`; no help for explicit Light/Dark |

**Decision: defer the inline script to a follow-up.**

The inline script must be hand-written and maintained outside React's lifecycle. For
MVP, with `system` as the default, most users will see the correct theme immediately
via OS preference. Explicit Light/Dark users may see a brief flash on cold load. This
is acceptable for an internal operational tool. The fix is tracked as a known
limitation and can be added without changing this ADR's core decisions.

### 5. Color tokens — Tailwind `dark:` utility classes

Dark mode colors are applied via Tailwind's `dark:` prefix directly in component
markup. No separate CSS variables or theme files are introduced for MVP.

```html
<div class="bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100">
```

This keeps the coloring co-located with the component and avoids an additional
abstraction layer. If a design token system is introduced later (e.g., CSS custom
properties for a design system), the `dark:` classes are the correct starting point
to migrate from.

---

## Alternatives Considered

### Alternative 1: `media` strategy only

Use Tailwind's `media` strategy and remove the Light/Dark explicit toggle, supporting
only the `system` option.

**Pros:** Zero JS needed to apply the theme; no DOM manipulation.

**Cons:** Users cannot override their OS preference from within the app. The settings
page theme toggle becomes meaningless. Rejected because user control is the stated
requirement.

### Alternative 2: CSS custom properties for color tokens

Define all colors as CSS custom properties (`:root { --bg: #fff; }`) and swap them
in a `[data-theme="dark"]` selector. Use Tailwind only for spacing/layout.

**Pros:** Clean separation of theme tokens from layout; easier for a future design
system.

**Cons:** Requires defining and maintaining a full token set upfront. Significant
extra work for an MVP with a small, stable color palette. Tailwind's `dark:` utilities
achieve the same result with no extra infrastructure.

**Why rejected:** Premature abstraction for MVP. Can be adopted later if a design
system is introduced.

### Alternative 3: Inline `<script>` for FOIT prevention now

Add a small synchronous script to `index.html` that reads `localStorage` and applies
the `dark` class before React mounts, eliminating any flash.

**Pros:** Zero flash of incorrect theme on any preference.

**Cons:** Script lives outside the React/TypeScript build pipeline; harder to type and
test; localStorage key must be kept in sync with the React hook manually.

**Why deferred:** Acceptable tradeoff for MVP on an internal tool. Can be added
independently without changing any other decision in this ADR.

---

## Consequences

### Positive

- `class` strategy gives full user control over theme regardless of OS setting
- `useTheme` hook is a single, testable location for all DOM theme logic
- `System` option automatically reacts to OS preference changes without a page reload
- `dark:` utility classes keep dark styles co-located with their components

### Negative

- Brief flash of incorrect theme on cold load for users with an explicit Light or Dark
  preference (known limitation, deferred fix)
- All components that need dark mode styling must be updated with `dark:` classes —
  requires an incremental pass over the component tree

### Neutral

- `usePreferences` hook (ADR-0018) remains the single source of truth for the stored
  preference value; `useTheme` is purely an applier
- Tailwind `darkMode: 'class'` must be set in the Tailwind config

---

## Implementation tickets

After this ADR is merged, create the following ticket in Phase 9:

1. `feat(frontend): dark mode — apply theme class to DOM via useTheme hook + dark: Tailwind variants`

---

## References

- [ADR-0018: User Settings](./0018-user-settings.md)
- [Tailwind CSS — Dark Mode](https://tailwindcss.com/docs/dark-mode)
- [#157: Dark mode theme](https://github.com/Jovan42/OpsClear/issues/157)
- [#158: User settings page (impl)](https://github.com/Jovan42/OpsClear/issues/158)
