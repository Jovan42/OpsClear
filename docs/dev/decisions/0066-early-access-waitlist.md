# ADR-0066: Early Access Waitlist

**Status:** Proposed
**Date:** 2026-11-26
**Author:** Jovan

## Context

Pre-launch marketing for the 30-day free trial (MIL-052) is ready: social posts say "Follow for
early access" and "Follow + comment TRIAL", and paid ads need a page to send clicks to. A follow or
a comment gives us no way to reach people on launch day. We need a public page on opsclear.app
where people leave their email, so we can tell them when OpsClear opens.

At the same time, anyone can sign up and start an organisation today, before the trial, lock-out
and payments (MIL-052, MIL-053) are finished. Until launch, sign-up should be closed to strangers.

This is a **launch notification list**, not early access to the product: people leave an email,
nothing else happens until launch, and on launch day the developer emails everyone on the list.

This milestone is PRJ-016 "Go-Live: Email, Operations, Free Trial & Payments" / MIL-054 Early access
waitlist. This ADR is JOB-435. It was not promoted from a Future Consideration.

### Facts from the existing system that shape this design

- **Sign-up path:**
  - the landing page "Sign up" buttons (`LandingPage.tsx:28`, `:66`) and the Features page CTA
    (`FeaturesPage.tsx:300`) call `keycloak.register()`
  - the realm has `registrationAllowed: true` (`keycloak/realm-export.json`)
  - after sign-up, a user without an org is sent to `/onboarding` by `OrgRequiredRoute`, where
    `CreateOrgPage` calls `POST /api/organisations` (`OrganisationController.create`)
  - `/org/new` uses the same page
- **Invites need Keycloak registration:** `AcceptInvitePage` (`SignInToJoin`) calls
  `keycloak.register({ loginHint, redirectUri })` for an invited person without an account. They
  then join through `POST /api/invites/{token}/accept` and never create an org. So turning off
  `registrationAllowed` would also break invites.
- **Super-admin area:** `@RequiresSuperUser` (checks `users.super_user`) guards
  `/api/super-admin/feedback` and the pricing endpoints. The frontend has
  `features/superAdmin/` with `SuperAdminNav`, a Feedback page and a Pricing page.
- **Public endpoints:** `SecurityConfig` permits `/api/health`, `/api/subscriptions/catalog`,
  `/api/webhooks/paddle` and `GET /api/invites/*`. There's no `/api/public/**` yet.
- **Rate limits (ADR-0058):** nginx `api_ip` zone (20 r/s, burst 200) on `/api/`, and a tighter
  `paddle_hook` zone with its own `location`. A new public form needs its own, tighter zone.
- **Errors:** `ErrorResponse.of(error, message)` via `GlobalExceptionHandler`. A 403 comes from
  `ForbiddenException`.
- **Migrations:** latest is `V044__org_invite_last_sent_at.sql`.
- **Privacy Policy:** draft PR #613 (MIL-053) lists the data we collect. Waitlist emails are new
  personal data and have to be listed.
- **Email:** the app sends mail through Brevo (ADR-0059, ADR-0064). This milestone sends none.
- **Verified emails:** Keycloak blocks login until the email is verified (ADR-0059,
  `verifyEmail: true`), so the email on a signed-in user is one they own. Nobody can claim another
  person's sign-up benefit by registering with their address.
- **Trial:** today a new org has no trial and no add-ons. The free trial and its end date come from
  MIL-052 (ADR job JOB-431, not written yet). The `TRIAL` code's benefit needs that trial end date.

## Decision

1. **A public page `/early-access`, "Get notified at launch"**, with one email field and a required
   consent checkbox ("Email me when OpsClear launches"). The UTM source and campaign of the visit
   are stored with it, invisibly.
2. **No email is sent on sign-up.** The page shows a thank-you state. The first email anyone gets is
   the developer's launch mail.
3. **A duplicate email gets the same thank-you.** The page never reveals whether an address was
   already on the list.
4. **Sign-up is closed to new organisations until launch**, controlled by one backend setting,
   `OPSCLEAR_SIGNUP_OPEN` (default `true`, `false` on prod until launch):
   - when closed, `POST /api/organisations` returns 403 for everyone except super users
   - `/onboarding` and `/org/new` show "We're not open yet" with a link to `/early-access`
     instead of the create form
   - the "Sign up" buttons on the landing and Features pages go to `/early-access`
   - Keycloak registration stays on, so **invites keep working**: invited people can still create
     an account and join an existing org
   - launch day: set `OPSCLEAR_SIGNUP_OPEN=true` in `.env.prod` and restart the backend
5. **Sign-ups are stored in one reusable table, `email_signups`**, with a `list` column. Early
   access is the first list (`early-access`). A later campaign, newsletter or event adds a list value,
   not a new table or endpoint.
6. **Super admins see the sign-ups** on a new "Sign-ups" tab in the super-admin area, filtered by list, with
   "Download CSV" and a delete button per row (junk and GDPR requests).
7. **A sign-up can carry a code** (`TRIAL` for now), taken from the link (`?code=TRIAL`) or typed
   into an optional "Promo code" field. A static map in the backend says what each code gives.
   `TRIAL` gives a **30-day trial in total**. Later codes can give other benefits (more trial days,
   credits). Unknown codes are rejected.
8. **The benefit is redeemed when the person creates their organisation** with the same
   (verified) email. Nothing to type again, once only. This needs MIL-052's trial, so the redemption
   job waits for it.
9. **The launch email is sent by hand from the CSV.** No bulk-mail code in this milestone.
10. **Spam protection:** a dedicated nginx rate-limit zone for the form endpoint and a honeypot
   field. No captcha.

## Product decisions

| Topic | Decision |
|---|---|
| What it is | A launch notification list. No early access to the product. |
| URL and title | `/early-access`, "Get notified at launch". Linked from the landing hero and header and from the Features CTA, and used as the ad and post link. |
| Form | Email (required) and the consent checkbox (required). Nothing else. Decided over optional name, company and team size. |
| Copy | Matches the posts: "30 days free, no credit card needed. Leave your email and we'll tell you the day OpsClear opens." Links to the Privacy Policy. en and sr. |
| After submit | Thank-you state on the same page: "Thanks! We'll email you when we launch." |
| Confirmation email | None. Decided over a "you're on the list" email and over double opt-in. |
| Duplicates | Same thank-you. The stored row stays as it was, except that a code is added if the row had none. |
| Source tracking | `utm_source`, `utm_medium` and `utm_campaign` from the page URL, stored with the sign-up. Shown in the list and the CSV. |
| Closing sign-up | No new organisations while `OPSCLEAR_SIGNUP_OPEN=false`. Invites still work. Super users can still create orgs (for testing). |
| Landing "Sign up" | While closed, it goes to `/early-access` and reads "Get notified". "Log in" is unchanged. On launch it goes back to Keycloak registration. |
| Signed in without an org | `/onboarding` shows "We're not open yet. We'll email you at launch." with a button that adds their account email to the list (or a link to `/early-access`) and a "Log out" button. |
| Viewing the list | Super-admin "Sign-ups" tab with a list filter (only `early-access` for now): date, email, code, redeemed, language, source, medium, campaign. Newest first. "Download CSV" for the selected list. |
| Reuse | One table for every email sign-up. Each sign-up belongs to a list (`early-access` now). The same email can be on several lists. New lists are added to an allowed set in the backend. |
| Removing someone | Delete button per row in the super-admin tab. Requests come in through support@ (ADR-0064). |
| Codes | Optional "Promo code" field, filled from `?code=` in the link and editable. Case-insensitive, stored upper-case. Unknown code: "This code isn't valid". No code: the sign-up still works, with no benefit. |
| Code map | Static, in the backend. `TRIAL` → trial of 30 days in total. New codes and new benefit types (for example credits) are a code change. |
| Redeeming | At org creation, if the creator's email has an unredeemed sign-up with a code, the new org gets that code's benefit. Used once: the sign-up records when and for which org. Invited people who join an existing org redeem nothing. |
| Launch mail | Out of scope. The developer exports the CSV and sends one campaign by hand. MIL-052 decides what the trial looks like. |
| Privacy | The Privacy Policy lists the waitlist (email, consent time, campaign source, kept until launch mail plus 6 months, or until deleted). Added on PR #613 or, if it merged, by the frontend job. |

## Technical design

### Database

`V045__email_signups.sql`:

```sql
CREATE TABLE email_signups (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list         VARCHAR(50)  NOT NULL,
    email        VARCHAR(254) NOT NULL,
    consent_at   TIMESTAMPTZ  NOT NULL,
    locale       VARCHAR(5),
    utm_source   VARCHAR(100),
    utm_medium   VARCHAR(100),
    utm_campaign VARCHAR(100),
    code         VARCHAR(30),
    redeemed_at  TIMESTAMPTZ,
    redeemed_org_id UUID REFERENCES organisations (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_email_signups_list_email ON email_signups (list, lower(email));
```

- Hard delete (no `deleted_at`): a removed address must really be gone (GDPR), unlike the app's
  soft-deleted entities.
- No `org_id`: the list belongs to OpsClear, not a customer org.
- `list` is free text in the database. The backend accepts only values from its allowed set
  (`early-access` for now), so a new campaign is a one-line backend change and no migration.
- Unique per list: the same email can be on `early-access` and a later list.
- `locale` (`en` / `sr`, from the page language) tells the launch mail which language to use.
- `code` is free text in the database, upper-case. The backend accepts only codes in its map.
- `redeemed_at` and `redeemed_org_id` mark a used benefit. They're added in this migration so the
  redemption job (after MIL-052) needs no migration.
- Regenerate jOOQ classes.

### API

| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/public/signups` | none | `{ "list": "early-access", "email": "a@b.com", "consent": true, "locale": "en", "utmSource": "linkedin", "utmMedium": "social", "utmCampaign": "launch", "code": "TRIAL", "website": "" }` | `204` always on valid input, new or duplicate. `400` on invalid email, unknown `list`, unknown `code` or `consent` not `true`. |
| `GET` | `/api/public/signup` | none | — | `200 { "open": false }` |
| `GET` | `/api/super-admin/signups?list=early-access` | super user | — | `200 [{ id, list, email, code, redeemedAt, redeemedOrgId, locale, utmSource, utmMedium, utmCampaign, consentAt, createdAt }]`, newest first |
| `GET` | `/api/super-admin/signups/export?list=early-access` | super user | — | `200 text/csv`, `Content-Disposition: attachment; filename="<list>-YYYY-MM-DD.csv"`, same columns |
| `DELETE` | `/api/super-admin/signups/{id}` | super user | — | `204`, `404` if not found |
| `POST` | `/api/organisations` | JWT | unchanged | new: `403` `{ "error": "Forbidden", "message": "Sign-up is closed until launch" }` when closed and the caller isn't a super user |

`website` is the honeypot. When it's not empty, the endpoint returns `204` and stores nothing.

### Backend

- **Property:** `opsclear.signup.open=${OPSCLEAR_SIGNUP_OPEN:true}`. Default open, so dev, tests,
  CI and the seed script behave as today. `docker-compose.prod.yml` passes
  `OPSCLEAR_SIGNUP_OPEN: ${OPSCLEAR_SIGNUP_OPEN:-true}`, and `.env.prod.example` lists it with a
  comment.
- **`SecurityConfig`:** `permitAll` for `POST /api/public/signups` and
  `GET /api/public/signup`.
- **`PublicSignupController`** for the two public endpoints. **`SuperAdminSignupController`** for the list, export and delete, each
  with `@RequiresSuperUser`, like `SuperAdminFeedbackController`.
- **`SignupList`** enum with the allowed lists and their wire values (`EARLY_ACCESS("early-access")`).
- **Code map:** `SignupCode` enum, the single place codes are defined, each with its benefit:

  ```java
  sealed interface SignupBenefit permits TrialDays { }
  record TrialDays(int totalDays) implements SignupBenefit { }

  enum SignupCode {
      TRIAL(new TrialDays(30));
      // later: e.g. a Credits(int amount) benefit and codes that use it
  }
  ```

  `SignupCode.parse(String)` trims, upper-cases and returns empty for an unknown code. A new
  benefit type is a new record plus its case in the redemption switch.
- **`EmailSignupService`:**
  - `signUp(request)`: if the honeypot is filled, return. Otherwise check the list is allowed, normalise the email (trim;
    keep the case as typed, uniqueness on `lower(email)`), require `consent == true`, truncate the
    UTM values to 100 characters, and check `code` against `SignupCode` (400 if unknown). Insert with
    `ON CONFLICT (list, lower(email)) DO UPDATE SET code = COALESCE(email_signups.code, EXCLUDED.code)`:
    no error, nothing else changes, and a code given later is kept.
  - `list(list)`, `exportCsv(list)` and `delete(id)`.
  - CSV: header row; RFC 4180 quoting; cells starting with `=`, `+`, `-` or `@` get a leading `'`
    (CSV injection in spreadsheets).
- **`EmailSignupRepository`** (jOOQ): insert-ignore, list by `list` ordered by `created_at desc`,
  delete by id.
- **Closing org creation:** `OrganisationService.create` checks `signupOpen`. When false and the
  caller isn't a super user, it throws `ForbiddenException("Sign-up is closed until launch")`.
  Invite accept doesn't create an org, so it isn't affected.
- **DTOs:** `EmailSignupRequest` (`list` required; `@Email`, `@NotBlank`, `@Size(max = 254)` on email;
  `@AssertTrue` on consent; `@Pattern("en|sr")` on locale, optional; `@Size(max = 100)` on UTM
  fields; `@Size(max = 30)` on code, optional), `EmailSignupResponse`, and
  `SignupStatusResponse(boolean open)`.
- **Logging:** never log the email at INFO. Log "sign-up stored" / "duplicate" /
  "honeypot" without the address.
- **Tests:**
  - Integration (`ApiPaths` for the new paths):
    - sign-up stores a row with its list
    - unknown list returns 400
    - code stored upper-case; unknown code returns 400; no code is fine
    - a duplicate with a code fills an empty code and doesn't overwrite an existing one
    - the same email on two lists gives two rows
    - duplicate in other case returns 204 and leaves one row
    - honeypot returns 204 and stores nothing
    - invalid email and missing consent return 400
    - no auth needed
    - signup status reflects the property
    - super-admin list, export (content type, header, quoting, injection guard) and delete;
      403 for a non-super user
    - `POST /api/organisations` returns 403 when closed and 201 for a super user when closed
      (test profile with the property set to `false`)
    - invite accept still works when closed
  - Unit: `EmailSignupService` (list check, normalising, truncating, honeypot, CSV escaping).

### Frontend

- **API layer:** `api/signups.ts` (`signUp(list, …)`, `getSignupStatus`) and super-admin `list`,
  `exportCsv` and `delete`. Hooks `useSignupStatus` (TanStack Query, long `staleTime`) and
  `useEmailSignUp`. A reusable `EmailSignupForm` component takes the `list` as a prop, so a later
  campaign page reuses it.
- **`/early-access` page** (`features/earlyAccess/EarlyAccessPage.tsx`), public, in `router.tsx`
  next to `/features`, using `EmailSignupForm` with `list="early-access"`:
  - copy from Product decisions, the email field, the consent checkbox with a Privacy Policy link,
    a submit button, and the hidden honeypot `website` input (off-screen, `tabIndex={-1}`,
    `autoComplete="off"`)
  - an optional "Promo code" field, filled from `?code=` and editable; a 400 for the code shows
    "This code isn't valid" on that field
  - reads `utm_source`, `utm_medium` and `utm_campaign` from the URL; sends the current i18n
    language as `locale`
  - thank-you state after `204`, and an inline error on `400` or `429`
  - React Hook Form + Zod like the other forms (FRONTEND.md); works at phone width; en and sr
- **Sign up buttons:** `LandingPage.tsx` (both) and `FeaturesPage.tsx` use `useSignupStatus`.
  When closed they become links to `/early-access` reading "Get notified". When open they call
  `keycloak.register()` as today. While the status is loading, show the "Get notified" variant, so
  the closed state never flashes open.
- **`CreateOrgPage`:** when closed and the user isn't a super user (from the existing user
  profile), show the "We're not open yet" card instead of the form: "Join the list" (submits the
  account email with `consent: true` after the user ticks the checkbox) and "Log out". A 403 from
  `POST /api/organisations` shows the same card.
- **Super-admin "Sign-ups" tab:** `SuperAdminNav` gets the tab. `SuperAdminSignupsPage` has a list
  selector (defaults to `early-access`), a table (date, email, code, redeemed, language, source, medium, campaign), a count, "Download CSV" (fetches
  the export with the auth header and saves it as a blob) and a delete button per row with a
  confirm dialog.
- **E2E (E2E.md):**
  - `/early-access` submits and shows the thank-you (`@smoke`)
  - with signup closed, the landing Sign up goes to `/early-access`
  - a super user sees the sign-up in the list and can delete it

  CI runs with signup open by default. The closed-state specs need the backend started with
  `OPSCLEAR_SIGNUP_OPEN=false` in a dedicated spec setup, or a stubbed `GET /api/public/signup`
  for the UI part plus backend integration tests for the 403. The frontend job picks the cheaper
  way.
- **Privacy Policy:** add the waitlist to the data list in `legal.json` (en, sr) if PR #613 has
  merged. Otherwise ask for it on PR #613.

### nginx (neutral, ships with the backend job)

- New zone `limit_req_zone $binary_remote_addr zone=signups:1m rate=5r/m;`
- `location = /api/public/signups` with `limit_req zone=signups burst=5 nodelay;`, the
  same proxy headers as `/api/`, and the 429 handling of `@rate_limited`.
- `GET /api/public/signup` falls under the normal `/api/` zone.

### Constraints & edge cases

- **Keycloak registration stays on:** someone can still reach Keycloak's register page directly
  (or through the "Register" link on the Keycloak login page). They get an account but can't create
  an org: `/onboarding` shows the "not open yet" card. That's acceptable. Hiding the Keycloak link
  would need the theme to know the backend setting.
- **Existing users without an org** (signed up earlier, never created one) see the same card until
  launch.
- **Second org for an existing user** (`/org/new`) is also blocked while closed. Nobody does that
  before launch.
- **Super users:** exempt from the org check, so the developer can create test orgs on prod.
- **Codes are public:** anyone who sees a post can use `TRIAL`. That's intended; it's a campaign
  code, not a secret. A code-specific limit (for example a maximum number of redemptions) would be a
  new field in the map later.
- **Code before MIL-052:** until the redemption job ships, codes are only stored and shown. Orgs
  created in that time (super users only, while sign-up is closed) don't redeem anything.
- **Different email at org creation:** if someone signs up with one address and registers with
  another, the code isn't found. Support can fix it by hand (support@, ADR-0064).
- **Deleted sign-up:** deleting the row removes the code too. An already redeemed benefit stays on
  the org.
- **Launch day:** `OPSCLEAR_SIGNUP_OPEN=true` in `.env.prod`, then
  `docker compose … up -d backend`. The frontend picks it up on the next status fetch, with no
  rebuild. Recorded in DEPLOY.md.
- **Email enumeration:** the same 204 for new, duplicate and honeypot sign-ups. A 400 only says
  the input is invalid.
- **Rate limit:** 5 per minute per IP with a burst of 5. Shared IPs (offices) are fine at that
  rate for a one-field form.
- **Abuse:** a bot that skips the honeypot and rotates IPs can still fill the table with junk. The
  table is cheap, the list is reviewed before launch, and junk rows can be deleted. A captcha can be
  added later.
- **GDPR:** consent is explicit (unticked checkbox, `consent_at` stored), the Privacy Policy
  describes it, deletion is a hard delete, and the launch mail must carry an unsubscribe option
  (the mailing tool's job).

## Alternatives considered

### A third-party form: Tally, Google Forms, Brevo form (decided against)

No code, but the page would live off our domain or in an iframe, styling and i18n wouldn't match,
another sub-processor would hold the list, and UTM capture would be clumsy. Built in, it's one small
table and one form.

### Code typed at org creation (decided against)

A "Promo code" field on `CreateOrgPage` works for anyone with the code, but people would have to
remember it weeks later, and the waitlist sign-up wouldn't matter. Redeeming by verified email
needs nothing from the user.

### Only store the code now, decide redemption in MIL-052 (decided against)

Keeps this ADR smaller, but splits one feature across two ADRs. The redemption is a separate job
here, blocked by MIL-052's trial job, so the order is still right.

### A dedicated `early_access_signups` table (decided against)

Simplest for one waitlist, but every later campaign or newsletter would need another table,
endpoint and admin view. A `list` column costs one field and one allowed-value check.

### Turn off Keycloak registration (decided against)

The simplest switch, but invites need it (`AcceptInvitePage`). Keeping invites working would mean
creating Keycloak accounts from the backend with a "set your password" email: much more code, and it
changes the invite flow just before launch.

### Double opt-in or a confirmation email (decided against)

A confirmed list and stronger proof of consent, but 20–30 % never confirm, and every sign-up would
make us email an address someone typed in. The developer wanted just a list of addresses to email
at launch.

### Optional name, company and team size (decided against)

More insight into who's interested, but a longer form gets fewer sign-ups. The UTM source already
tells us which post or ad worked.

### Super-admin toggle or frontend build flag for the switch (decided against)

A database toggle needs a settings table, an endpoint and a UI for one flip on launch day. A build
flag only hides buttons, leaves `POST /api/organisations` open and needs a rebuild. An env setting
plus a public status endpoint covers both the backend and the frontend.

### Syncing sign-ups to a Brevo contact list (decided against for now)

It would make the launch campaign easy, but needs a Brevo API key and integration code for a
one-time send. A CSV import into the mailing tool does the same once.

### Cloudflare Turnstile captcha (decided against)

Strong against bots, but it's another script and sub-processor that sees every visitor of the page.
The nginx limit and the honeypot are enough for a one-field form. It can be added if junk shows up.

## Consequences

### Positive

- Posts and ads have a landing page that captures people, with the source of each sign-up.
- Strangers can't start using an unfinished product before trial and payments are ready, while
  invites keep working for testers.
- Launch day is one setting and one restart.
- The list is ours, on our domain, and can be exported at any time.

### Negative

- Keycloak's own register page is still reachable. Those users hit a "not open yet" card.
- The launch mail is a manual step, outside the app.
- One more public, unauthenticated write endpoint to protect.
- Personal data (emails) that must be listed in the Privacy Policy and deleted on request.

### Neutral

- Dev, tests and CI default to sign-up open, so nothing changes for them.
- The super-admin area grows by one tab.

## Implementation order

Each job is one layer (JOBS.md). nginx, compose, `.env.prod.example` and DEPLOY.md are neutral and
go with the backend job.

1. **DB: Email sign-ups table.** `V045__email_signups.sql` (table and unique `(list, lower(email))`
   index), jOOQ regeneration.
2. **Backend: Email sign-ups, signup switch and super-admin list.** `SignupList`, the two public endpoints,
   the super-admin list, CSV export and delete, the `opsclear.signup.open` check in
   `OrganisationService.create`, `SecurityConfig`, the nginx zone and location, compose,
   `.env.prod.example`, and DEPLOY.md "Launch day: reopen sign-up", with tests. Blocked by 1.
   After merge (developer): `OPSCLEAR_SIGNUP_OPEN=false` in `.env.prod`, `git pull`,
   `up -d backend frontend` (nginx config is in the frontend image, so the frontend is rebuilt),
   and check that `POST /api/organisations` returns 403 for a non-super user.
3. **Frontend: Early access page, closed sign-up and super-admin tab.** `/early-access`, the
   Sign up buttons driven by `useSignupStatus`, the "not open yet" card in `CreateOrgPage`, the
   reusable `EmailSignupForm`, the super-admin Sign-ups tab with CSV and delete, en and sr, E2E, and the Privacy Policy entry if
   PR #613 has merged. Blocked by 2.
4. **Backend 2: Redeem sign-up codes at organisation creation.** In `OrganisationService.create`,
   after the org is created: find the oldest unredeemed sign-up with a code for the creator's email
   (case-insensitive), apply its benefit (`TrialDays` sets the org's trial end to creation +
   `totalDays`, through MIL-052's trial model), and set `redeemed_at` and `redeemed_org_id`, all in
   the same transaction. Log the redemption (no email). Integration tests: the code is redeemed
   once; a second org by the same user gets the normal trial; no sign-up or no code gives the normal
   trial; super-user org creation follows the same rule. Blocked by 2 and by MIL-052's trial backend
   job (JOB-431 breakdown). The frontend shows the trial length MIL-052 already displays, so no
   frontend job is needed.

**For JOB-431 (free trial ADR):** the trial model must allow a per-org trial length (a trial end
date set at creation that can differ from the default), so job 4 can apply `TrialDays`.

Order on prod: the backend can ship first with the setting still `true` (nothing changes), then
switch to `false` once the frontend is live, so the Sign up buttons never point to a 403.

## References

- JOB-435 (this ADR), PRJ-016 / MIL-054
- MIL-052 Free trial and lock-out (JOB-431), MIL-053 Payments and legal go-live (JOB-432, PR #613)
- ADR-0058 API Rate Limiting (nginx zones), ADR-0059 Email Verification and Transactional Email,
  ADR-0064 Domain Email and Support Contact (support@ for deletion requests)
- `frontend/src/features/landing/LandingPage.tsx`, `frontend/src/features/landing/FeaturesPage.tsx`,
  `frontend/src/features/org/AcceptInvitePage.tsx`, `frontend/src/features/org/CreateOrgPage.tsx`,
  `frontend/src/components/OrgRequiredRoute.tsx`, `frontend/src/features/superAdmin/`,
  `backend/src/main/java/com/opsclear/controller/OrganisationController.java`,
  `backend/src/main/java/com/opsclear/controller/SuperAdminFeedbackController.java`,
  `backend/src/main/java/com/opsclear/aop/RequiresSuperUserAspect.java`, `frontend/nginx.conf`,
  `keycloak/realm-export.json`
