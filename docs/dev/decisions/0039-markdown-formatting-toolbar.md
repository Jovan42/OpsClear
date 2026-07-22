# ADR-0039: Markdown Formatting Toolbar

**Status:** Proposed
**Date:** 2026-07-22
**Author:** Jovan Manojlovic

## Context

Users writing job descriptions and notes must know Markdown syntax to format content — non-technical users have no way to apply bold, lists, or code formatting without memorizing it. The rendering side already works fine (`Markdown.tsx`, backed by `react-markdown` + `remark-gfm` + `rehype-sanitize`); what's missing is a way to *write* formatted text without knowing the syntax.

Investigating the current state changed the shape of this ADR from the original Future Consideration: markdown input across the app already goes through one shared component, `frontend/src/components/MarkdownEditor.tsx`, which already has a Write/Preview tab toggle (`useState<'write' | 'preview'>`, switching between a `<textarea>` and a `<Markdown>` render). It's used by `NoteThread.tsx`, the job description field in `NewJobModal.tsx`, and also `NewProjectModal.tsx`, `ProjectSettingsPage.tsx`, and `TemplateFormModal.tsx` — five consumers, not just the two (job description, notes) the original Future Consideration named.

This means two things the original FC treated as open questions are effectively settled by what already exists:

- **Preview toggle:** already built, already shipped, on every markdown field in the app. Nothing to add.
- **Scope:** since the toolbar is added to the shared `MarkdownEditor` component itself rather than to each field individually, it lands on all five consumers automatically — a deliberate expansion beyond the FC's original two named fields, adopted here rather than gated off, because it's the same amount of work and produces a more consistent app (every markdown field behaves identically).

## Decision

Add a formatting toolbar directly to `MarkdownEditor.tsx` — a row of icon buttons (Bold, Italic, Inline code, Blockquote, Horizontal rule, Ordered list, Unordered list) shown above the textarea in Write mode only. Built as new, custom code in the existing component; no third-party markdown-editor library.

## Product decisions

- Clicking a button with text selected wraps the selection (e.g. `**text**`); clicking with no selection inserts a placeholder at the cursor (e.g. `**bold text**`), matching the original FC spec exactly.
- Toolbar only renders in Write mode — nothing to act on while Preview is showing.
- Applies uniformly to every `MarkdownEditor` consumer (Notes, job description, project description, project settings, templates) — no per-field opt-out prop.

## Technical design

### Library vs. custom

Rejected pulling in a package like `@uiw/react-md-editor` or `react-textarea-markdown-editor`. Doing so would mean either replacing the existing, already-working, five-places-shared `MarkdownEditor`/`Markdown` pair entirely, or running two different markdown-input patterns side by side in the app. Both are a lot of blast radius and new-dependency risk for a feature the original FC itself scoped as "a quality-of-life improvement... not a blocker for any core workflow." A custom toolbar is a small, additive change to the existing component and leaves the working preview path (`Markdown.tsx`) completely untouched.

### Selection/cursor manipulation

No existing utility for this anywhere in the codebase (no `selectionStart`/`selectionEnd`/`setRangeText` usage found). New code either way — added as a small helper alongside `MarkdownEditor.tsx`, operating on the textarea ref: read `selectionStart`/`selectionEnd`, wrap or insert the appropriate markdown syntax, restore focus and cursor position after the DOM update.

### Icons

No icon library exists in the codebase today (no `lucide-react`, Heroicons, or `react-icons` — the only icon-shaped code is `LinkIcon.tsx`'s one-off inline SVGs for link favicons/brand marks). ADR-0035 already named Lucide as the fallback icon for links (chain-link icon), though it isn't actually installed yet. This ADR adopts `lucide-react` now for the seven toolbar icons (`Bold`, `Italic`, `Code`, `Quote`, `Minus`, `ListOrdered`, `List`) rather than hand-rolling seven inline SVGs — small, tree-shakeable, and becomes the project's actual icon library going forward instead of leaving it as a named-but-unused ADR-0035 reference.

### Component changes

- `MarkdownEditor.tsx`: new toolbar row rendered above the `<textarea>`, visible only when `tab === 'write'`. Each button calls the new selection-wrapping helper and updates the editor's value via the existing `onChange` prop — no new props needed on the public `MarkdownEditor` API, so no changes required in any of its five consumers.
- No changes to `Markdown.tsx` (preview rendering) or `NoteThread.tsx` / `NewJobModal.tsx` / `NewProjectModal.tsx` / `ProjectSettingsPage.tsx` / `TemplateFormModal.tsx` beyond what they already get automatically by using `MarkdownEditor`.

### Constraints & edge cases

- Toolbar buttons must not steal focus from the textarea in a way that breaks the wrap/insert behavior — focus and selection need to be restored after each action.
- Must not regress the existing `Markdown.tsx` render path — untouched by this ADR.

## Alternatives considered

### Third-party markdown editor library

Covered above — rejected due to blast radius (replacing or duplicating the existing shared `MarkdownEditor`) disproportionate to a feature explicitly scoped as low-priority polish.

### Per-field opt-in/opt-out toolbar (only job description + notes, as the original FC named)

Considered, to stay strictly within the original FC's named scope. Rejected — since the toolbar lives inside the shared `MarkdownEditor` component, gating it per-consumer would require a new prop and conditional rendering for no real benefit; every markdown field in the app behaving identically is simpler and more consistent than an arbitrary subset having formatting help and others not.

### Hand-rolled inline SVG icons (no new dependency)

Considered, consistent with "no icon library exists yet." Rejected in favor of adopting `lucide-react` now — ADR-0035 already implicitly committed to Lucide for link icons, and seven hand-rolled SVGs is more code to maintain than one small dependency that also resolves ADR-0035's not-yet-installed reference.

## Consequences

### Positive

- Ships to all five `MarkdownEditor` consumers at once, not just the two originally named — same effort, more consistency
- Leaves the already-working Write/Preview toggle and `Markdown.tsx` render path completely untouched
- `lucide-react` becomes the project's actual icon library, resolving ADR-0035's previously-named-but-never-installed dependency

### Negative

- Introduces the codebase's first icon-button toolbar row — a new UI pattern, though a small and expected one
- `lucide-react` is a new frontend dependency

### Neutral

- No new props on `MarkdownEditor`'s public API — all five consumers get the toolbar with zero changes to their own code
- Selection/cursor-manipulation logic is new code with no prior art in the codebase to follow; implemented as a small, self-contained helper

## Implementation order

1. Add `lucide-react` dependency
2. Selection-wrapping helper (wrap selected text / insert placeholder at cursor, restore focus)
3. Toolbar row in `MarkdownEditor.tsx`, rendered only in Write mode, wired to the helper
4. Manual verification across all five consumers (Notes, job description, project description, project settings, templates)

## References

- ADR-0035: Job and Project Links (`docs/dev/decisions/0035-job-and-project-links.md`) — originally named Lucide as the fallback link icon, not yet installed; this ADR is what actually introduces the dependency
- JOB-129 (PRJ-007 / MIL-021): Markdown toolbar for description/notes fields
- JOB-049 (Future Consideration, promoted to PRJ-007/MIL-021): original scoping notes this ADR implements and expands on
