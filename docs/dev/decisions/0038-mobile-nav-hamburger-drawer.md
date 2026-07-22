# ADR-0038: Mobile Nav Hamburger Drawer

**Status:** Proposed
**Date:** 2026-07-22
**Author:** Jovan Manojlovic

## Context

On mobile, `ProjectNav` (defined inside `AppLayout.tsx`) currently renders twice: once desktop-only in the header's right-side cluster (`hidden md:block`), and once in a second, mobile-only row directly below the header (`md:hidden overflow-x-auto -mx-4 px-4 pb-2`, `AppLayout.tsx` lines 157–161) that horizontally scrolls. That strip already holds seven items — Dashboard, Jobs, Milestones, Templates, Schedules, Approvals, Settings — and is about to gain an eighth (Links, JOB-124, currently in progress). Horizontal scroll on a phone-width screen is already awkward and only gets worse as more project-level features ship.

This ADR replaces that strip with a hamburger icon that opens a side drawer listing the same items vertically.

## Decision

Add a hamburger trigger at the **far left** of the header, before the `OpsClear` logo, mobile-only. Opening it slides in a left-side overlay drawer listing the exact same nav items as the desktop `ProjectNav` — same items, same order, same locked/unlocked treatment — just stacked vertically instead of horizontally. The current mobile-only horizontal-scroll row is removed entirely; desktop behavior (`hidden md:block` `ProjectNav` in the header's right-side cluster) is untouched.

## Product decisions

- **Trigger placement:** far left, before the logo — matches the near-universal mobile convention of nav-on-the-left, account-on-the-right, rather than optimizing for least code disruption. Mobile header becomes `[Hamburger] [Logo/Breadcrumb] ... [UserMenu]`.
- **Drawer content:** identical to today's desktop `ProjectNav` — same item list, same order, same `lockedNavLink()` teaser treatment for addon-gated items (Dashboard, Milestones, Templates, Schedules, Links, etc.). No divergence between what desktop and mobile show.
- **Kept separate from `UserMenu`:** account/logout/theme stays in its own menu on the right — different concern, not merged into the nav drawer.
- **Approvals badge:** the pending-approvals count currently shown inline in the nav row surfaces on the hamburger icon itself (small dot/count), so it's visible without opening the drawer.
- **No item-count threshold:** the drawer unconditionally replaces the mobile row on all mobile viewports, regardless of how many nav items exist. A conditional (e.g. "scroll strip until 5 items, hamburger past that") would mean maintaining two mobile nav UIs in parallel for marginal benefit — the strip is already awkward at 7 items today and only grows.
- **Desktop unaffected:** no changes to the `hidden md:block` `ProjectNav` row or its layout.

## Technical design

### Header changes (`AppLayout.tsx`)

- New hamburger `<button>`, `md:hidden`, inserted before the existing logo `<Link to="/projects">OpsClear</Link>` (currently line ~137).
- Remove the second mobile-only row (lines 157–161) entirely.
- Small badge (dot or count) on the hamburger reflecting the same pending-approvals count already computed for the existing nav row.

### Nav item source

Today, the item list and the `lockedNavLink()` / `hasAddon()` gating logic live inline inside `ProjectNav` (`AppLayout.tsx` lines 30–79). Both the desktop horizontal nav and the new drawer need to render the same items with the same gating, so the item list (icon, label, route, addon-gate) is extracted into a shared array/hook that both `ProjectNav` (desktop) and the new `NavDrawer` component consume — avoiding two copies of the gating logic drifting apart as addons change. `lockedNavLink()`'s rendering (label + small padlock icon) is reused as-is for the vertical layout, not reimplemented.

### Drawer mechanics

- **Overlay, not push:** reuses `Modal.tsx`'s existing backdrop pattern exactly — `fixed inset-0 z-50` wrapper, a separate `absolute inset-0 bg-black/40` dimming layer, click-on-backdrop closes via `onClick`, the drawer panel itself calls `e.stopPropagation()` to avoid closing when clicking inside. No push-content model — nothing else in this codebase shifts the main layout open/closed, and doing so here would add reflow complexity for no real benefit.
- **Slide side:** left, matching the trigger's position.
- **Animation:** `transition-transform duration-200 ease-out` translate-in from the left. This is a deliberate, small precedent: neither `UserMenu`'s dropdown nor `Modal`/`ConfirmModal` currently have any transition at all (both just mount/unmount instantly), and there's no animation library in `frontend/package.json` (no Radix, Headless UI, or Framer Motion — everything nav/modal-related is hand-rolled Tailwind + React state). A full-height side panel appearing instantly reads as broken in a way an instant dropdown doesn't; drawers are a spatially-oriented pattern where motion communicates "coming from off-screen." Plain Tailwind utility classes, no new dependency.
- **Close behavior:** backdrop click (via the `Modal.tsx`-style `stopPropagation` pattern) and selecting a nav item both close the drawer.

### Constraints & edge cases

- Must not change desktop (`md:` and up) behavior at all.
- Drawer must reuse `lockedNavLink()` exactly as it renders today for gated items — no new locked-state treatment.
- Drawer stays a distinct component from `UserMenu` — no shared state, no merged menu.

## Alternatives considered

### Hamburger on the right, next to `UserMenu`

Initially proposed because `AppLayout.tsx` already has a right-side `flex items-center gap-6` container that could be extended with minimal restructuring. Rejected: optimizing for implementation convenience over the near-universal "nav on the left" mobile convention is the wrong tradeoff for a UI element every mobile user interacts with directly, especially for a tool aimed at non-technical SME owners where familiar patterns reduce friction more than saved lines of code help.

### Push-content drawer instead of overlay

Shift the main content area open when the drawer expands, rather than floating an overlay on top. Rejected — no existing UI in this codebase does content-push, it adds real reflow/layout complexity, and the overlay pattern is already established via `Modal.tsx`.

### Conditional threshold (scroll strip until N items, hamburger past that)

Keep today's horizontal-scroll strip for a small number of items and only switch to the drawer once the nav grows past some count. Rejected — maintaining two mobile nav UIs in parallel is more implementation and testing surface than it's worth; the strip is already unwieldy at the current 7 items and only grows.

### No animation (match `Modal`/`UserMenu`'s instant show/hide)

Considered, to avoid introducing the first transition-based UI in the codebase. Rejected — a full-height slide-in panel is a spatially-oriented pattern where users expect motion; appearing instantly reads as a rendering glitch rather than a deliberate UI. The cost of introducing this precedent is low (plain Tailwind transition utilities, no new dependency).

## Consequences

### Positive

- Mobile nav no longer relies on horizontal scroll, which was already strained at 7 items and about to hit 8 (Links)
- Desktop and mobile nav render from one shared item source — no risk of the two drifting apart as addons are added or gated differently in the future
- Reuses two existing, proven interaction patterns (`Modal.tsx`'s backdrop, `lockedNavLink()`'s teaser rendering) rather than inventing new ones for most of the feature

### Negative

- Introduces the first CSS transition/animation in the frontend codebase — a small new precedent to be consistent with going forward (future overlays/dropdowns may now be compared against this standard)
- Slightly more header restructuring than a right-side placement would have needed, to match mobile UX convention over implementation convenience

### Neutral

- The second mobile-only `ProjectNav` row is removed outright, not deprecated gradually — no feature flag, single cutover
- Desktop nav (`hidden md:block` row) is completely unchanged

## Implementation order

1. Extract the nav item list + `lockedNavLink()`/`hasAddon()` gating from `ProjectNav` into a shared array/hook
2. `NavDrawer` component: overlay + backdrop (per `Modal.tsx` pattern), slide-in-from-left panel, renders the shared item list vertically
3. Hamburger trigger button in `AppLayout.tsx`, `md:hidden`, placed before the logo, with pending-approvals badge
4. Remove the existing mobile-only horizontal-scroll `ProjectNav` row
5. Manual verification: desktop unaffected; mobile drawer opens/closes correctly (backdrop click, item selection); locked items render identically to desktop; badge reflects pending-approvals count

## References

- JOB-128 (PRJ-007 / MIL-020): Mobile nav hamburger side drawer
- JOB-125 (Future Consideration, promoted to PRJ-007/MIL-020): original scoping notes this ADR implements
