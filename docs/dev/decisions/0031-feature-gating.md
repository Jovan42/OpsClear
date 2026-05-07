# ADR-0031: Feature gating full stack

**Status:** Proposed
**Date:** 2026-05-07
**Author:** Jovan Manojlovic

## Context

ADR-0030 introduced the subscription data model and the org settings UI for choosing a tier and add-ons. That milestone deliberately excluded enforcement: the system records what an org has subscribed to, but nothing actually checks it at runtime.

This milestone adds the enforcement layer. There are two distinct categories of access control to implement:

1. **Add-on gating** — certain features are only accessible if the org's active subscription includes the relevant add-on.
2. **No-subscription wall** — orgs with no subscription record at all cannot access any app features until they configure one.

Tier limits (member count, project count) are already enforced at subscription save time (downgrade validation in ADR-0030). They are not re-checked on every request.

## Decision

### Feature classification

| Feature | Gating |
|---|---|
| Projects, jobs, members, job blocking | Core — always available |
| Org management, subscription management | Core — always available |
| Dashboard | Add-on: `DASHBOARD` |
| Approvals | Add-on: `APPROVALS` |
| Notes | Add-on: `NOTES` |
| Job status history | Add-on: `JOB_STATUS_HISTORY` |
| Milestones | Add-on: `MILESTONES` |
| Job relationships | Add-on: `JOB_RELATIONSHIPS` |
| API keys | Add-on: `API_KEYS` |
| Job templates | Always locked (coming soon) |
| Recurring scheduling | Always locked (coming soon) |

### Backend: `@RequiresAddon` + AOP

A custom annotation marks service methods that require an active add-on:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAddon {
    String value(); // e.g. "DASHBOARD"
}
```

A Spring AOP aspect intercepts every call to a method annotated with `@RequiresAddon`. It:

1. Resolves the `orgId` from the method arguments.
2. Queries `org_subscriptions` + `org_subscription_addons` to check if the add-on key is active for that org.
3. If the org has no subscription record, throws `ForbiddenException` (403).
4. If the org has a subscription but the add-on is not active, throws `ForbiddenException` (403).
5. If the check passes (or `is_internal = true`), the method proceeds normally.

`is_internal` orgs bypass all add-on checks, consistent with how downgrade validation is bypassed in ADR-0030.

### No-subscription wall

If an org has no subscription record at all, **all** app features are inaccessible. This is enforced at the API level: any endpoint that the AOP aspect intercepts will throw 403 when there is no subscription.

The frontend handles this with a full-page subscription configurator wall — the same component used in the org settings Subscription section — rendered before any other app content when the subscription GET returns 404.

### Frontend: lock UI

Three distinct lock states are handled in the frontend:

**No subscription (org has never configured one)**
Full-page wall replaces all app content. The subscription configurator (from the org settings page) is rendered inline so the owner can set up without navigating away. Non-owners see a message directing them to the org owner.

**Locked full page** (e.g. Dashboard, API Keys page)
A centered upgrade card fills the page with the feature name and an "Upgrade in org settings" button that navigates to `/org/settings`. Nav items for locked pages remain visible but carry a lock indicator; clicking navigates to the upgrade card rather than the real feature.

**Locked section within a page** (e.g. Notes, Approvals, Job History, Relationships on the job detail page)
A single compact locked row replaces the section content:
```
🔒  Notes · Approvals · Job History · Relationships  —  Upgrade in org settings
```
Multiple locked sections on the same page collapse into one row to avoid visual clutter.

### Determining lock state in the frontend

The subscription is fetched once at the org context level (alongside the org itself) and made available via context. Each feature component reads from context to determine whether to render normally or show the lock UI. There is no per-feature API call for gate checking.

The subscription response already contains the list of active add-on keys. Components compare against a client-side constant map of `feature → addonKey` to determine their state.

## Alternatives Considered

### Alternative 1: Grayed-out content for locked features

Show the real feature UI but dim it and overlay a lock icon/upgrade prompt.

**Pros:**
- Users can see what they are missing, which may increase upgrade conversions.

**Cons:**
- Requires fetching real data (jobs, notes, etc.) just to dim it — wasted API calls and increased backend load.
- Poor on mobile: small screens make grayed overlays hard to interact with.
- Edge cases with interactive elements (inputs, buttons) that must also be disabled.

**Why rejected:** The cost of fetching real data for locked content outweighs the conversion argument, especially on mobile. The upgrade card is clear enough about what is locked.

### Alternative 2: Explicit service-level checks instead of AOP

Add `if (!subscriptionService.hasAddon(orgId, "DASHBOARD"))` at the top of every gated service method.

**Pros:**
- Explicit — each method self-documents its gate.
- No aspect magic; easier to trace in a debugger.

**Cons:**
- Repetitive — every new gated endpoint must remember to add the check.
- Inconsistent enforcement risk — one missed check means a feature is silently ungated.
- Pollutes service constructors with `SubscriptionService` dependency on every service.

**Why rejected:** AOP gives a single enforcement point. A missing `@RequiresAddon` on a new endpoint is still a bug, but it is easier to catch in code review than a missing inline check. The aspect can also be tested in isolation.

### Alternative 3: Gateway/filter-level enforcement using URL patterns

Map add-on keys to URL prefixes (e.g. `/api/projects/{id}/dashboard → DASHBOARD`) and enforce in a servlet filter.

**Pros:**
- No annotation required on individual methods.
- Enforcement is independent of the service layer.

**Cons:**
- URL-to-feature mapping is fragile: renaming or restructuring routes breaks the gate silently.
- Some features (e.g. notes on a job) share URL namespaces with ungated features (`/api/projects/{id}/jobs/{id}/notes` vs `/api/projects/{id}/jobs/{id}`).
- Resolving `orgId` from a URL path in a generic filter requires URL-template matching logic.

**Why rejected:** The annotation approach is more explicit and less brittle than URL-pattern matching.

## Consequences

### Positive

- Add-on enforcement is uniform: any service method annotated with `@RequiresAddon` is automatically gated.
- The subscription context is fetched once and shared; no per-request DB queries beyond the AOP check.
- The lock UI is consistent across page-level and section-level locked features.

### Negative

- Every new gated endpoint must carry `@RequiresAddon` — this is easy to forget and must be verified in code review.
- The AOP aspect adds a DB read on every call to a gated method. This should be mitigated with a short-lived cache (e.g. per-request or per-second) if performance becomes a concern.
- The subscription configurator component must be extractable for reuse as the no-subscription wall; the org settings and the wall must stay in sync.

### Neutral

- Coming-soon add-ons (`JOB_TEMPLATES`, `RECURRING_SCHEDULING`) are always locked at the frontend level regardless of subscription state; the `available = false` field on the add-on catalog entry drives this, not the AOP gate.

## Implementation Notes

1. Create `@RequiresAddon` annotation and `RequiresAddonAspect` in a new `aop` package.
2. Add a `hasAddon(orgId, addonKey)` method to `OrgSubscriptionRepository` (or a dedicated query in `SubscriptionService`).
3. Annotate all gated service methods: `DashboardService`, `ApprovalService`, `NoteService`, `JobHistoryService`, `MilestoneService`, `JobRelationshipService`, `ApiKeyService`.
4. On the frontend, fetch the subscription in `OrgContext` alongside the org; expose `addonKeys: Set<string>` and `hasAddon(key): boolean` from context.
5. Implement the no-subscription wall as a separate component; render it in `OrgRequiredRoute` when subscription is `null`.
6. Add lock UI components: `UpgradeCard` (full-page) and `LockedSectionRow` (inline).
7. Wire lock state into all affected pages: Dashboard, API Keys (full-page); Notes, Approvals, Job History, Relationships (section-level on job detail).
8. Unit tests: aspect correctly gates methods, passes for `is_internal`, throws 403 when add-on absent.
9. Integration tests: end-to-end per gated endpoint — 200 with active add-on, 403 without.

## References

- ADR-0025: API key authentication (one of the features being gated)
- ADR-0026: Organisation layer (org context that subscription is attached to)
- ADR-0030: Subscription data model and management (the data this milestone enforces)
