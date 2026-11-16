# ADR-0061: Additional User Preferences and Server-Synced Settings

**Status:** Proposed
**Date:** 2026-11-16
**Author:** Jovan

## Context

ADR-0023 added personal preferences, kept in the browser's `localStorage`. There are 14 today. They don't follow a user to another browser or device, and several features shipped since then have choices people can't make yet: a "due soon" warning, only seeing their own jobs, where they land after login, the stats range.

This ADR moves preferences to the server, so they follow the user. It adds seven new preferences and regroups the settings page so it stays easy to scan.

Promoted from JOB-032 (Future Consideration, PRJ-002) into PRJ-014 / MIL-045. This ADR is JOB-335.

### Facts from the existing system that shape this design (research for this ADR)

- **The 14 current preferences** live in `frontend/src/hooks/usePreferences.ts` and are saved to `localStorage` under `opsclear:preferences` by `PreferencesProvider`. That one key is shared by everyone who uses the browser. They are: `theme`, `locale`, `defaultViewMode`, `milestoneAccordionState`, `defaultStatusTab`, `hideCompletedFromAll`, `defaultSortOrder`, `showBlockedSection`, `showOverdueSection`, `showPendingApprovalsSection`, `defaultProjectPage`, `deadlineFormat`, `milestoneProgressFormat`, `notificationSound`.
- **`PreferencesProvider` wraps the whole app**, including public pages (landing and features use the theme and locale before login). Demo slides use `mockPreferences`.
- **`users.preferences` (JSONB, default `{}`) is already used.** Notification mutes per type live under its `notifications` key (`NotificationSettingsService`, `GET/PUT /api/user/notification-settings`, ADR-0051), and the settings page already shows them (`NotificationTypeSettings`). `NotificationSettingsService` keeps other keys intact when it writes. "Expose notification mutes" from the idea is **already done**.
- **There's no "due soon" concept.** Deadlines are normal, or red when overdue (`deadline.overdue` in `JobListPage`, `JobDetailPage`). The backend sends no due-soon notification.
- **The job list has no assignee filter.** `GET /projects/{id}/jobs` takes `q`, `priority`, `milestoneId` and `typeId`. Status filtering and the tab counts are worked out in the browser over the full list.
- **Dates** are formatted ad hoc in about 15 places with `toLocaleDateString(undefined, …)` / `toLocaleString(undefined, …)`, i.e. the **browser's** locale, not the app language. Most show a month name ("15 Nov 2026"); a few use bare `toLocaleDateString()`.
- **After login**, `LandingPage` sends users to a saved deep link if there is one, otherwise to `/projects`. `useRecentProjects` remembers recently opened projects (ADR-0047).
- **Stats** (`StatsPage`, ADR-0050) offers 4, 12 or 26 weeks, with `DEFAULT_RANGE = 12` hard-coded.
- **The cross-project approval queue** is a section of the Owner/Admin Overview page (ADR-0046). Separately, the nav shows a pending-approvals badge to Owners/Admins.
- **The settings page** is one page with sections: Appearance, (job list), Dashboard, Navigation, Milestones, Notifications, API keys. It uses `SettingRow` rows.
- **Dashboard auto-refresh** (from the original idea) isn't needed: SSE notifications and the manual refresh button (ADR-0048, ADR-0051) cover it. Dropped.

## Decision

- **Preferences move to the server**, into a `ui` key of `users.preferences`, through new `GET` and `PATCH /api/user/preferences` endpoints. The backend stores the object without knowing its fields; the frontend owns the schema and defaults.
- **`localStorage` stays as a fast cache.** The page draws from it straight away and the server's values replace it once loaded. Existing local values are uploaded once. The cache is per user.
- **Seven new preferences:**
  - due-soon highlight threshold
  - default "Mine" job filter (with a new Everyone/Mine filter)
  - row density
  - date format (with one shared date formatter)
  - default landing project
  - stats default range
  - hide the cross-project approval queue on Overview
- **The settings page** keeps its grouped sections and gets a section index: sticky on desktop, anchor chips on mobile.

## Product decisions (settled in JOB-335)

| Question | Decision |
|----------|----------|
| Storage | Server (`users.preferences.ui`), so preferences follow the user across devices. Everything syncs; nothing stays per-device. |
| Existing preferences | Uploaded once from `localStorage` on first load after release, so nobody has to set them again. |
| Due-soon highlight | Off / 1 / 3 / 5 / 7 days, **default 3**. An amber deadline and a "Due soon" hint wherever deadlines show (job list table and cards, job detail, dashboard lists). Overdue stays red and takes precedence. No new dashboard section. |
| "Mine" filter | New **Everyone / Mine** filter on the job list (Mine = I'm the responsible person). The preference sets which one the list opens on (default Everyone). The status tab counts follow the filter. |
| Row density | Comfortable (default) / Compact, for the desktop job table only. Mobile cards don't change. |
| Date format | **Auto** (default: follows the app language, en `15 Nov 2026`, sr `15. nov 2026.`), `DD.MM.YYYY`, `DD/MM/YYYY`, `MM/DD/YYYY`. Applies to every date in the app through one shared formatter; times keep the language's style. |
| Default landing project | **Project list** (default, today's behaviour) / **Last opened project** / **a chosen project**. It opens on the existing "default project page" (Dashboard / Jobs / Approvals). A saved deep link still wins. If the project no longer exists or isn't accessible, the user lands on the list. |
| Stats default range | 4 / 12 / 26 weeks, default 12. |
| Hide cross-project approval queue | Hides the approvals section on Overview only. The nav's pending-approvals badge stays, so nothing waiting gets missed. Default: shown. |
| Settings layout | Grouped sections plus a section index (sticky list on desktop, anchor chips on mobile). |
| Dropped | Dashboard auto-refresh (covered by SSE and manual refresh); exposing notification mutes (already done). |

### Settings page groups

| Section | Preferences (new in bold) |
|---------|---------------------------|
| Appearance | Theme, Language, **Date format**, **Row density** |
| Jobs | Default view, Default status tab, Hide completed from All, Default sort, **Default assignee filter**, Deadline format, **Due-soon threshold** |
| Dashboard | Show Blocked / Overdue / Pending approvals sections, **Show approvals on Overview** (Owner/Admin only) |
| Navigation | **Landing project**, Default project page |
| Milestones | Accordion state, Progress format |
| Stats | **Default range** |
| Notifications | Sound, mutes per type (existing) |
| API keys | (existing) |

## Technical design

### Database

None. `users.preferences` exists; the new data goes under a `ui` key next to `notifications`.

### API

**`GET /api/user/preferences`** (any authenticated user, own preferences only):

```json
{ "ui": { "theme": "dark", "dueSoonDays": 3, "dateFormat": "AUTO" } }
```

`ui` is `{}` when nothing has been saved yet.

**`PATCH /api/user/preferences`**, body `{ "ui": { "dueSoonDays": 5, "rowDensity": null } }`:

- Merges top-level keys into the stored `ui`. A `null` value removes the key (back to the frontend default). Returns the full stored `ui`.
- **400** when:
  - `ui` is missing or isn't an object
  - a key isn't 1–64 characters `[A-Za-z0-9_]`
  - a value isn't a string (≤ 256 characters), number, boolean or null; nested objects and arrays are rejected
  - there are more than 100 keys after the merge
  - the stored `ui` would exceed 16 KB
- The `notifications` key and any other top-level keys are never touched.

Postman: add both requests.

### Backend

- **`UserPreferencesController`** (`/api/user/preferences`, `ApiPaths` constant) with GET and PATCH.
- **`UserPreferencesService`:** reads `users.preferences` and treats it as `{}` if it isn't an object (same rule as `NotificationSettingsService`). It validates the patch, merges it into the `ui` key and writes it back with `UserRepository.updatePreferences`, keeping every other key. The read-merge-write runs in one transaction with `SELECT … FOR UPDATE` on the user row, so this and a concurrent notification-settings write can't overwrite each other.
- **DTOs:** `UserPreferencesResponse { Map<String, Object> ui }` and `UpdateUserPreferencesRequest { Map<String, Object> ui }`, validated in the service.
- **Tests:**
  - Unit tests: merge, remove on null, each validation rule, a non-object `preferences` treated as `{}`, `notifications` kept.
  - Integration tests:
    - GET returns empty for a new user
    - PATCH then GET round-trip
    - `null` removes a key
    - a nested value, an oversized string, too many keys and over 16 KB each give 400
    - a PATCH after a notification-settings PUT keeps the mutes, and the reverse keeps `ui`
    - unauthenticated gives 401

### Frontend: preference sync

- **`usePreferences.ts`:** the `Preferences` type and `defaults` gain:

  ```ts
  dueSoonDays: 0 | 1 | 3 | 5 | 7;                        // 3 (0 = off)
  defaultAssigneeFilter: 'EVERYONE' | 'MINE';            // 'EVERYONE'
  rowDensity: 'COMFORTABLE' | 'COMPACT';                 // 'COMFORTABLE'
  dateFormat: 'AUTO' | 'DMY_DOT' | 'DMY_SLASH' | 'MDY_SLASH'; // 'AUTO'
  landingProject: 'LIST' | 'LAST' | 'SPECIFIC';          // 'LIST'
  landingProjectId: string | null;                       // null (project UUID when SPECIFIC)
  statsDefaultRange: 4 | 12 | 26;                        // 12
  showOverviewApprovals: boolean;                        // true
  ```

- **Validating loaded values:** whatever is read from the server or the cache passes through a `sanitize()` step. Unknown keys are dropped, and invalid values fall back to their defaults, so a bad stored value can't break the UI.
- **`PreferencesProvider`** flow:
  1. **First paint:** draw from the cache, the per-user key `opsclear:preferences:<userId>`. Before login, and on public pages, use the anonymous key `opsclear:preferences` (theme and locale for the landing page).
  2. **After login:** `GET /api/user/preferences`.
     - **The server has values:** server values win (merged over defaults), and the cache is overwritten.
     - **The server is empty and this browser has the legacy `opsclear:preferences` key:** upload it once with `PATCH` and copy it to the per-user key. The legacy key is then kept only as the anonymous cache, and is **not** uploaded again for a different user who logs in later in the same browser. A `opsclear:preferences:migratedBy` marker records the user it was uploaded for.
     - **The server is empty and there's nothing local:** defaults.
  3. **`update(patch)`:** update state and the cache straight away, and queue a debounced (500 ms) `PATCH` of the changed keys. On failure, keep the local value and show one "Couldn't save your preferences" toast; the next change retries.
  4. Other devices see changes on their next app load. There's no live push.
- **Demo mode** keeps `mockPreferences`, with no server calls.

### Frontend: the new preferences

- **Date formatting:** a new `utils/formatDate.ts` with `formatDate(iso, prefs)` and `formatDateTime(iso, prefs)`.
  - `AUTO` uses `Intl.DateTimeFormat` with the **app language** (`en` / `sr-Latn`), `{ day: 'numeric', month: 'short', year: 'numeric' }`.
  - The fixed formats pad day and month to 2 digits.
  - The time part (`HH:mm`, or the language's style) is unchanged.
  - Every existing `toLocaleDateString` / `toLocaleString` call that shows a date moves to it. `deadlineFormat: ABSOLUTE` uses it too.
  - This deliberately changes "Auto" from the *browser's* locale to the *app's* language.
- **Due soon:** a `deadlineState(deadline, status, dueSoonDays)` helper returns `'overdue' | 'dueSoon' | 'normal'`.
  - Due soon means: not completed, not overdue, and the deadline is within N days from today (calendar days in the viewer's zone).
  - Used by the job list table and cards, job detail and dashboard job rows, with amber text plus a small "Due soon" hint.
  - `dueSoonDays = 0` turns it off.
- **Mine filter:** an Everyone / Mine toggle on `JobListPage` next to the status tabs. It starts on `defaultAssigneeFilter`, filters the loaded jobs on `assignedTo === me` (the job's responsible person) in the browser, and the tab counts are computed after it. Switching it doesn't change the preference.
- **Row density:** `COMPACT` reduces the desktop table's row padding (`py-3` → `py-1.5`) and text size. Mobile cards unchanged.
- **Landing project:** `LandingPage`'s fallback (no saved deep link) becomes:
  - `LIST` → `/projects`
  - `LAST` → the first entry from `useRecentProjects`
  - `SPECIFIC` → `landingProjectId`

  Each project option opens `/projects/{friendlyId}/{defaultProjectPage}`. If the project can't be loaded (deleted, no access), the user lands on `/projects`. The setting's picker lists the user's projects; "Last opened" needs no picker.
- **Stats range:** `StatsPage` starts on `statsDefaultRange` instead of the hard-coded 12. Changing the range on the page doesn't change the preference.
- **Overview approvals:** `OverviewPage` hides the cross-project approvals section when `showOverviewApprovals` is false. The setting is shown only to org Owners/Admins (the only people who see the Overview approvals). The nav badge doesn't change.
- **Settings page:**
  - Regroup into the sections in the table above, each with an `id`.
  - Desktop: a sticky section index on the left.
  - Mobile: a horizontal row of anchor chips under the page title.
  - New rows use the existing `SettingRow`.
- **i18n:** en and sr keys for every new label, description, option, hint and the save-error toast.
- **E2E:**
  - A preference changed in one session is still there after clearing `localStorage` and logging in again (it came from the server).
  - The one-time upload: seed the legacy key, log in, then the server has those values.
  - Due-soon highlight on a job due tomorrow, and off when set to Off.
  - The Mine filter and its default.
  - The date format changes a visible deadline.
  - The landing project after login.
  - The stats default range.
  - Hiding Overview approvals.
  - Specs reset preferences afterwards (`PATCH` with nulls) so shared seed users aren't left changed.

### Constraints & edge cases

- **Shared browser:** the per-user cache key and the `migratedBy` marker keep user B from getting, or uploading, user A's preferences.
- **Two devices changing preferences at once:** each `PATCH` only sends changed keys, so different keys don't overwrite each other. For the same key, the last write wins.
- **First paint vs server:** the first paint uses the cache. If the server's values differ (another device changed them), the UI updates once after the GET. That's acceptable; theme and locale may switch once.
- **Opaque storage:** the backend doesn't know the fields. `sanitize()` on the client is the safety net, and the backend's size and type limits stop abuse.
- **Landing project gone:** a deleted project, or one the user has left, falls back to the list. The stale `landingProjectId` stays until they change it; harmless.
- **"Last opened" with no recent projects** falls back to the list.
- **Due soon and time zones:** worked out in calendar days in the viewer's zone, like the overdue check.
- **The Mine filter** uses the responsible person only, not watchers or approvers.
- **The date format** affects display only. Date inputs (pickers) keep the browser's native format.
- **Overview approvals for Members:** the setting isn't shown. The stored value has no effect because they don't see the section.

## Alternatives considered

### Keep preferences in `localStorage` (decided against)

No backend work, but preferences stay tied to one browser, and the new ones would make that gap more noticeable.

### Server only for the new preferences (decided against)

Two storage places to explain and maintain, and the 14 existing ones still wouldn't follow the user.

### Typed backend DTO for every preference (decided against)

Strict validation, but every new UI preference would need a backend change and deploy. The data is UI-only and per-user, so client-side sanitising plus backend size and type limits is enough.

### Server only, no upload of local values (decided against)

Simpler, but everyone would have to set their preferences again after release.

### Theme and density per device (decided against)

Some people want dark on the laptop and light on the phone. Syncing everything is simpler, and "System" theme already adapts per device.

### Server-side `responsibleId` filter (decided against)

The list already loads every job and filters status in the browser. A server parameter only pays off if lists are paginated later.

### A "Due soon" dashboard section (decided against)

More dashboard surface for a highlight that already shows in every job row. Can be added later with its own show/hide preference.

### Hide the nav approvals badge too (decided against)

Cleaner for people who never approve, but pending approvals would be easy to miss. The badge only shows to Owners/Admins with something waiting.

### Settings as tabs (decided against)

Tidier, but more clicks and harder to scan, and it doesn't fit "3 clicks max".

### Dashboard auto-refresh interval (dropped)

SSE notifications and the manual refresh button (ADR-0048, ADR-0051) already keep the dashboard current.

## Consequences

### Positive

- Preferences follow the user to every browser and device.
- Seven new choices covering deadlines, filtering, dates, density, navigation and stats.
- One shared date formatter replaces about 15 ad-hoc calls, and dates follow the app language instead of the browser's.
- New UI preferences need no backend change.

### Negative

- Every preference change is now a network call (debounced). If saving fails, the change is kept locally only until the next successful save.
- Opaque storage means the backend can't report or migrate individual preferences; the frontend's `sanitize()` must handle old and unknown values.
- "Auto" dates change for users whose browser locale differs from the app language. That's intended, but visible.
- The settings page grows to about 21 settings.

### Neutral

- ADR-0023's "preferences in `localStorage`" is superseded for storage. Its preference model and the `usePreferences` API stay.
- A "Due soon" notification (backend) could reuse `dueSoonDays` later. Out of scope here.

## Implementation order

1. **Backend: user UI preferences API.**
   - `GET`/`PATCH /api/user/preferences` with `ui` merge, validation and row lock
   - unit + integration tests, Postman

   No blockers.
2. **Frontend 1: server-synced preferences and settings layout.**
   - `PreferencesProvider` sync (per-user cache, one-time upload, debounced PATCH, `sanitize()`), the new `Preferences` fields and defaults
   - settings page regrouping with the section index
   - E2E for sync and upload

   *Blocked by Backend.*
3. **Frontend 2: date format and row density.**
   - `formatDate` / `formatDateTime`, moving every date display to it
   - date format and density settings, compact table
   - i18n, E2E

   *Blocked by Frontend 1.*
4. **Frontend 3: due-soon highlight and Mine filter.**
   - `deadlineState` and the amber hint in list, detail and dashboard
   - Everyone/Mine toggle with counts, both settings
   - i18n, E2E

   *Blocked by Frontend 1.*
5. **Frontend 4: landing project, stats range and Overview approvals.**
   - `LandingPage` fallback with the project picker setting
   - `StatsPage` default range
   - `OverviewPage` section toggle (Owner/Admin setting)
   - i18n, E2E

   *Blocked by Frontend 1.*

Frontend 2–4 are independent of each other, but all add rows to the settings page. Whichever merges later rebases on the earlier one.

No DB job.

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`): GET and PATCH `/api/user/preferences`
- [ ] Add example requests (set values, remove with null, nested value → 400)
- [ ] Update environment variables if needed (none)
- [ ] Test the flow manually before marking complete

## References

- JOB-032: source Future Consideration
- ADR-0023: User preferences (model and `localStorage`, storage now superseded)
- ADR-0018: User settings page
- ADR-0037: Localization (en/sr, date language)
- ADR-0046: Cross-project approval queue (Overview)
- ADR-0047: Quick project switcher (recent projects)
- ADR-0048: Manual refresh button (why auto-refresh is dropped)
- ADR-0050: Job completion velocity stats (range)
- ADR-0051: In-app notification centre (`users.preferences.notifications`)
