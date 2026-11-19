# Onboarding

Last run: 2026-11-19, Jovan
Guide: /guide/sign-up, /guide/create-organisation, /guide/invite-team, /guide/first-project

This file builds the test world that every other area file relies on: both orgs, all personas
through real sign-up, email verification and invites, Brightline's three projects with their
settings, and realistic clients, milestones, jobs and notes. Run it first, on day 1, in order. The
world is described in the [README](README.md#test-world).

**Two things the app does that shape this file:**

- **One organisation per person.** A user can create or belong to one organisation only
  (`/organisations/mine` returns one, and creating a second is refused). The README gives Sara a
  seat in both orgs, which the app doesn't allow, so **Petra**, a sixth persona on
  `<mailbox>+petra@<domain>`, owns Pine & Pixel Studio. Sara stays a Brightline member and is used
  to check that she can't create a second org (ONB-30). Org switching can't be tested for the same
  reason. The README and ADR-0063 carry the same corrected persona table.
- **Subscription before anything else.** Until the Owner pays, every org page except the
  subscription setup shows a wall, so the invite and project steps come after ONB-08.

**Setup.** One browser profile per persona. Emails are `<mailbox>+ana@<domain>`,
`<mailbox>+nikola@<domain>`, `<mailbox>+ivana@<domain>`, `<mailbox>+luka@<domain>`,
`<mailbox>+sara@<domain>` and `<mailbox>+petra@<domain>`, all landing in one real inbox. Use one
password for every persona and keep it in a password manager. Never write the real address in this
repo.

**Out of scope here.** Invite expiry after 7 days, **Copy link**, **Resend email** and the
5-minute resend cooldown are covered by hand in `02-org-admin.md`.

**Dates.** Day 1 is today (UTC). "Day 3" is today + 2 days and "Day 4" is today + 3 days. Near
midnight UTC, work the dates out from the UTC clock, not your local one.

## ONB-01 Paddle is in sandbox (pre-flight)
**As:** Developer (no login) · **Day:** 1 · **Needs:** access to the prod `.env.prod` and the Paddle dashboard. Do this before ONB-08 and ONB-30, the first scenarios that buy anything.

1. On the VPS, open `.env.prod` and look at `PADDLE_CLIENT_TOKEN` and `PADDLE_API_KEY`. Don't paste them anywhere.
2. Open the Paddle dashboard and check which account you are on.

- [x] `PADDLE_CLIENT_TOKEN` starts with `test_`
- [x] `PADDLE_API_KEY` starts with `pdl_sdbx_`
- [x] The Paddle dashboard in use is the Sandbox one

**If any box can't be ticked, stop the whole run.** Nothing in this pass may charge a real card.
ONB-08 and ONB-30 repeat the check in the browser.

## ONB-02 The public pages work before login
**As:** Anonymous visitor (private window) · **Day:** 1 · **Needs:** nothing

1. Open the OpsClear home page.
2. Click the theme button (sun, moon or screen icon) in the top bar, then the **EN** language button.
3. Scroll the page.
4. Open `/features`. Open one card's demo and look at the badges.
5. Click **User guide** in the top bar.
6. Type `/projects` in the address bar.

- [x] The home page shows the headline, **Get started** and **Log in**
- [x] The theme button cycles light, dark and system, and the page follows it
- [x] The language button switches the page between English and Serbian and back
- [x] `/features` lists every feature, with an **Included** badge on base features and the add-on name on gated ones
- [x] A card's demo opens and can be stepped through
- [x] The user guide opens without a login
- [x] `/projects` while logged out sends you to the sign-in page, not an error

## ONB-03 Registration form checks its input
**As:** Ana (not registered yet) · **Day:** 1 · **Needs:** nothing

1. On the home page click **Get started**. The **Register** form opens.
2. Click **Register** with every field empty.
3. Fill in First name **Ana**, Last name **Petrović**, Email `<mailbox>+ana@<domain>`, a password, and a different text in **Confirm password**. Click **Register**.

- [x] The empty form is rejected with a message on the missing fields, and no account is created
- [x] A mismatching **Confirm password** is rejected with a message, and the other fields keep what you typed
- [x] The page has a **Back to Login** link and uses the OpsClear look

## ONB-04 Sign up and verify the email (Ana)
**As:** Ana (new user) · **Day:** 1 · **Needs:** ONB-03 done, still on the form. Every other persona repeats this flow.

1. Fix **Confirm password** so it matches and click **Register**.
2. On **Verify your email**, read the page, then in a second tab open `/projects`.
3. In the mailbox, open **Verify your email for OpsClear** sent to `<mailbox>+ana@<domain>`.
4. Click **Resend email** on the page, and check the mailbox again.
5. Click **Verify email address** in the newest email.

- [x] After **Register** you land on **Verify your email**, which names `<mailbox>+ana@<domain>`
- [x] The second tab also shows **Verify your email** and never lets you into the app
- [x] The first email arrives within a minute, from OpsClear, with a green **Verify email address** button and a note that the link expires in 5 minutes
- [x] **Resend email** sends a second email
- [x] Clicking the link confirms the address and signs you in
- [x] You land on **Create your organisation**, because Ana has no organisation yet

## ONB-05 The same email can't register twice
**As:** Anonymous visitor (private window) · **Day:** 1 · **Needs:** ONB-04 done

1. Click **Get started** and register again with `<mailbox>+ana@<domain>`.
2. Click **Back to Login**.

- [x] The form rejects the address with a message that it's already in use, and no second account exists
- [x] **Back to Login** returns to the sign-in page

## ONB-06 Create Brightline Renovations
**As:** Ana (org owner to be) · **Day:** 1 · **Needs:** ONB-04 done, on **Create your organisation**; JOB-395 deployed for the top bar checks

1. Look at the bar at the top of the page. Click the theme button until it cycles back, then the **EN** language button, then switch back to English.
2. Click **Sign out**. Then click **Log in** and sign in as Ana again.
3. Click **Create organisation** with both fields empty.
4. Type **Brightline Renovations** in **Organisation name**. In **Slug**, type `b1` and click **Create organisation**.
5. Clear **Slug**, type `brtx`, and look at the box.
6. Clear **Slug**, type `brt`, and click **Create organisation**.

- [x] The page has a top bar with the OpsClear logo, the theme button, the language button and **Sign out**, and no links that need an organisation (projects, organisation settings)
- [x] The theme button cycles light, dark and system, and the language button switches the page to Serbian and back
- [x] **Sign out** ends the session and lands on the home page with **Log in**; signing in again as Ana returns to **Create your organisation**
- [x] The empty form shows "Name is required"
- [x] A one-letter slug shows "Slug must be 2–3 letters"
- [x] The slug box only takes letters, shows them in capitals and stops at three characters (`brtx` becomes `BRT`)
- [x] **Create organisation** succeeds and opens **Set up your subscription**, not the projects list

## ONB-07 Nothing works before a subscription
**As:** Ana (owner) · **Day:** 1 · **Needs:** ONB-06 done, subscription not bought yet

1. Type `/projects`, then `/org/members`, then `/org/invites` in the address bar.

- [x] Each address shows **Set up your subscription** again, not the real page

## ONB-08 Buy every add-on in the Paddle sandbox
**As:** Ana (owner) · **Day:** 1 · **Needs:** ONB-01 ticked, ONB-07 done

1. Under **Base plan**, drag **Team members** and **Active projects** to the smallest values that allow at least 6 people and at least 3 active projects.
2. Under **Add-ons**, click each of the 11 cards: Dashboard, Approvals, Notes, Job status history, Milestones, Job relationships, API keys, Job templates, Recurring scheduling, Job links and Job types. Leave **Annual billing** off.
3. Check the **Monthly total** against **Base price** plus the cards, then click **Continue to payment**.
4. Open the browser's Network tab, then look at the payment form that opens on the page.
5. Pay with the Paddle sandbox test card `4242 4242 4242 4242`, any future expiry date, CVC `100`, and any name and billing country.
6. Wait on the page.

- [ ] Each chosen card has a green border, and the **Monthly total** changes by that add-on's price when you click it
- [ ] **Continue to payment** opens the payment form on the page, and its requests go to Paddle hosts with `sandbox` in the name — ❌ JOB-396
- [ ] The payment form shows the same **Total** as **Monthly total**
- [ ] **Processing your payment** appears and the page updates by itself, without a refresh
- [ ] **Organisation Settings** now opens, and the Billing box shows **Active**
- [ ] **Billing history** (click it open) lists one payment with a date, a status and the amount

## ONB-09 Organisation settings as the owner
**As:** Ana (owner) · **Day:** 1 · **Needs:** ONB-08 done

1. Open **Organisation Settings** (user menu, **Organisation**).
2. Look at every section from the top.

- [ ] **Members** has **Manage members** and **Invites** buttons
- [ ] **Details** shows **Brightline Renovations**, slug **BRT** and "Created by Ana Petrović", with the name and slug boxes editable
- [ ] **ID prefixes** shows the next project, job, milestone and template IDs
- [ ] **Org Templates** is there, because Job templates is bought
- [ ] **Subscription**, **Billing history** and **Danger zone** are there

## ONB-10 Send the team's invites
**As:** Ana (owner) · **Day:** 1 · **Needs:** ONB-08 done

1. Open **Invites**. Click **Send invite** with the box empty.
2. Type `<mailbox>+nikola@<domain>` and click **Send invite**.
3. Send the same address a second time.
4. Send invites to `<mailbox>+ivana@<domain>`, `<mailbox>+luka@<domain>` and `<mailbox>+sara@<domain>`.
5. Open the mailbox.

- [ ] The empty box shows "Email is required"
- [ ] Nikola's invite appears in the list with Ana as inviter and "Expires in 7 days"
- [ ] The second invite to the same address is refused with "A pending invite already exists for this email", and the list still has one row for it
- [ ] The list shows four pending invites, each with **Copy link**, **Resend email** and **Revoke**
- [ ] Four invite emails arrive, titled "You're invited to join Brightline Renovations on OpsClear", each saying Ana Petrović invited you, with an **Accept invite** button and the expiry date

## ONB-11 Nikola joins from the invite
**As:** Nikola (new user) · **Day:** 1 · **Needs:** ONB-10 done

1. In the mailbox, open Nikola's invite and click **Accept invite**.
2. On the sign-in page click **Register** and register as **Nikola Jovanović** with `<mailbox>+nikola@<domain>`.
3. Verify the email as in ONB-04.
4. Click **Accept invite** in the invite email again.
5. On **You've been invited**, click **Accept invite**.
6. Click the same invite link a second time.

- [ ] The invite link needs a login first, and the sign-in page opens
- [ ] Registering shows **Verify your email**, and the app stays closed until the link is clicked
- [ ] After verifying, the invite link opens **You've been invited** with an **Accept invite** button
- [ ] **You've joined the organisation** appears, then **Projects** opens after about two seconds
- [ ] Nikola sees no projects yet
- [ ] The second click shows a red message that the invite is no longer valid, and a **Go to projects** button

## ONB-12 The invite only works for the invited address
**As:** Nikola (org member for now) · **Day:** 1 · **Needs:** ONB-11 done, Ivana's invite still pending

1. Open Ivana's invite email and click its **Accept invite** link while signed in as Nikola.
2. Click **Accept invite** on the page.

- [ ] The page shows "This invite was sent to a different email address"
- [ ] Ivana's invite is still pending in Ana's list

## ONB-13 Ivana, Luka and Sara join
**As:** Ivana, then Luka, then Sara (each in their own profile) · **Day:** 1 · **Needs:** ONB-12 done

1. For each persona, repeat the steps of ONB-11 with their own invite email: register (**Ivana Marković**, **Luka Ilić**, **Sara Đorđević**), verify, click **Accept invite** again, accept.
2. Sign each one out with **Sign out** in the user menu.

- [ ] Ivana, Luka and Sara each get **You've joined the organisation**
- [ ] Ana's pending invites list is now empty
- [ ] Ana has an **Invite accepted** notification for each of the four people, in the notification centre (bell)
- [ ] **Sign out** returns each persona to the public home page

## ONB-14 Promote Nikola to Admin
**As:** Ana (owner) · **Day:** 1 · **Needs:** ONB-13 done

1. Open **Manage members**. Look at the table and the form above it.
2. Change Nikola's role box from **Member** to **Admin**.
3. Look at Ana's own row. Reload the page.

- [ ] The table lists Ana (Owner badge), Nikola, Ivana, Luka and Sara, and the form above it is **Search by email…**
- [ ] Ana's row has a badge and no role box or **Remove**
- [ ] Nikola's role changes to **Admin** and stays after the reload
- [ ] Ivana, Luka and Sara stay **Member**

## ONB-15 An admin's powers in the organisation
**As:** Nikola (org admin) · **Day:** 1 · **Needs:** ONB-14 done

1. Open **Organisation Settings**, **Manage members** and **Invites**.
2. Try to change the name on **Organisation Settings**.
3. Look at the members table.

- [ ] **Invites** is available, and Nikola can open the list of pending invites
- [ ] The name and slug boxes are greyed out, with no **Save changes** button
- [ ] **Subscription**, **Billing history** and **Danger zone** aren't shown
- [ ] The members table shows role badges, not role boxes, so Nikola can't change anyone's role
- [ ] The members table has the add form and **Remove** on non-Owner rows, and no **Remove** on the Owner row

## ONB-16 A member can't invite, add or manage
**As:** Luka (org member) · **Day:** 1 · **Needs:** ONB-13 done

1. Open **Organisation Settings**.
2. Open **Manage members**.
3. Type `/org/invites` in the address bar. Type `<mailbox>+stranger@<domain>` and click **Send invite**.

- [ ] **Organisation Settings** has **Manage members** but no **Invites** button
- [ ] The name, slug and **ID prefixes** are greyed out
- [ ] **Subscription**, **Billing history**, **Credit balance** and **Danger zone** aren't shown
- [ ] The members table has no add form, no role boxes and no **Remove**
- [ ] Sending the invite fails with "Insufficient permissions: OWNER or ADMIN role required"
- [ ] The stranger address doesn't appear in Ana's pending invites

## ONB-17 Create Residential Fit-outs
**As:** Ana (org owner) · **Day:** 1 · **Needs:** ONB-14 done, Ana on **Projects**

1. Click **Create your first project**.
2. Click **Create project** with the name empty.
3. Type **Residential Fit-outs** in **Name**. In **Description**, type "Kitchens, bathrooms and full home refits for private clients."
4. Under **Block reasons**, add **Waiting on client**, **Materials not delivered** and **Waiting on inspection** (type each and click **Add**).
5. Click **Create project**.

- [ ] The empty name shows "Name is required"
- [ ] The three block reasons show as removable entries before creating
- [ ] **Create project** opens the new project and Ana is its Owner
- [ ] **Projects** lists **Residential Fit-outs** with an **Owner** badge, under the **Active** tab
- [ ] **Project Settings** lists the three block reasons

## ONB-18 Create Office Refurbishments
**As:** Nikola (org admin) · **Day:** 1 · **Needs:** ONB-14 done

1. Open **Projects**. Click **Create your first project**.
2. Type **Office Refurbishments** in **Name** and "Fit-outs and repairs for offices and shops." in **Description**.
3. Add the block reasons **Waiting on landlord** and **Waiting on permits**, then click **Create project**.

- [ ] Nikola can create a project while only an org admin, and is its Owner
- [ ] Nikola's **Projects** list shows only this project
- [ ] Ana's **Projects** list doesn't show it, because Ana isn't a member of it

## ONB-19 Create Spring Maintenance 2026
**As:** Ana (org owner) · **Day:** 1 · **Needs:** ONB-17 done

1. Click **+ New Project**.
2. Type **Spring Maintenance 2026** in **Name** and "Seasonal servicing for existing maintenance contracts." in **Description**. Leave block reasons empty.
3. Click **Create project**.

- [ ] The project exists and Ana is its Owner
- [ ] **Project Settings** says "No block reasons configured"
- [ ] The **Active** tab lists two projects for Ana: **Residential Fit-outs** and **Spring Maintenance 2026**

Leave this project active. `03-projects.md` archives it later.

## ONB-20 Add the people to Residential Fit-outs
**As:** Ana (project owner) · **Day:** 1 · **Needs:** ONB-17 and ONB-14 done

1. Open **Residential Fit-outs**, click **Settings**, find **Members**.
2. Type `<mailbox>+nikola` in **Search by email…**, pick Nikola, choose **Admin** and click **Add**.
3. Add Ivana, Luka and Sara with the role **Member**.
4. Type `<mailbox>+nikola` in the search again.

- [ ] Nikola is added as **Admin**, and the table shows him with that role
- [ ] Ivana, Luka and Sara are added as **Member**
- [ ] The second search shows Nikola with "(already a member)"
- [ ] The table lists Ana as **Owner** with no role box and no **Remove**
- [ ] Nikola, Ivana, Luka and Sara each get a notification that they were added to the project

## ONB-21 Add the people to Office Refurbishments
**As:** Nikola (project owner) · **Day:** 1 · **Needs:** ONB-18 done

1. Open **Office Refurbishments**, click **Settings**, find **Members**.
2. Add Ivana as **Admin**.
3. Add Luka as **Member**.

- [ ] Ivana is **Admin** and Luka is **Member** in the table
- [ ] Ivana and Luka each get a notification that they were added

## ONB-22 Residential Fit-outs: approval gate on, all sections visible
**As:** Ana (project owner) · **Day:** 1 · **Needs:** ONB-20 done

1. Open **Residential Fit-outs**, **Settings**, scroll to **Job screen**.
2. Look at **Sections shown on jobs**.
3. Switch on **Require approval to complete jobs**.
4. Reload the page.

- [ ] **Notes**, **Approvals**, **Relationships**, **Links** and **Job status history** are all switched on, and none says **Not in your plan**
- [ ] Switching the gate on shows "Jobs now need approval to complete"
- [ ] After the reload the gate is still on

## ONB-23 Office Refurbishments: gate off, some sections hidden
**As:** Nikola (project owner) · **Day:** 1 · **Needs:** ONB-21 done

1. Open **Office Refurbishments**, **Settings**, scroll to **Job screen**.
2. Switch off **Relationships**, **Links** and **Job status history**. Leave **Notes** and **Approvals** on.
3. Look at **Require approval to complete jobs**. Reload the page.

- [ ] Each switch-off shows a message such as "Relationships now hidden on jobs"
- [ ] **Require approval to complete jobs** is off
- [ ] After the reload the three sections are still off and **Notes** and **Approvals** are still on

## ONB-24 What a project member can't change
**As:** Luka (member of Residential Fit-outs and Office Refurbishments) · **Day:** 1 · **Needs:** ONB-22 and ONB-23 done

1. Open **Residential Fit-outs**, **Settings**.
2. Look at **Details**, **Members** and **Job screen**.
3. Open **Office Refurbishments**, **Settings**, and look at the same sections.

- [ ] The name and description are greyed out and there's no **Save changes**
- [ ] **Members** shows the people without the add form, role boxes or **Remove**
- [ ] The section switches and **Require approval to complete jobs** are locked, with the note "Only project Owners and Admins can change this."
- [ ] Residential shows the gate on and Office shows it off
- [ ] There's no **Danger zone**

## ONB-25 Milestones
**As:** Ana (Residential owner), then Nikola (Office owner), then Luka · **Day:** 1 · **Needs:** ONB-22 and ONB-23 done

1. As Ana, open **Residential Fit-outs**, **Milestones**, click **+ New Milestone**. Create **Kitchen phase** (Deadline: Day 4 + 7 days), **Bathroom phase** (Day 4 + 3 days) and **Handover** (Day 4 + 14 days), with a short description each.
2. As Nikola, open **Office Refurbishments**, **Milestones**. Create **Demolition** (Day 3), **Fit-out** (Day 4 + 10 days) and **Handover** (Day 4 + 17 days).
3. As Luka, open **Milestones** in Residential.

- [ ] The three Residential milestones are listed, each with its due date
- [ ] The three Office milestones are listed
- [ ] The same name **Handover** in both projects doesn't clash
- [ ] Luka sees the milestones but no **+ New Milestone** button

## ONB-26 Create the day-1 jobs
**As:** Ana (Residential owner), then Nikola (Office owner) · **Day:** 1 · **Needs:** ONB-25 done

Use **+ New Job** on **Jobs**. Fill **Title**, **Client**, **Deadline**, **Priority**, **Milestone** and **Assign to** from the table, with a one-line description such as "Client wants the work finished before the weekend".

| Project | Title | Client | Deadline | Priority | Milestone | Assign to |
|---------|-------|--------|----------|----------|-----------|-----------|
| Residential | Tile bathroom at 14 Elm St | Okafor family | **Day 3** | High | Bathroom phase | Luka |
| Residential | Paint hallway and stairs at 9 Birch Rd | Hartwell family | **Day 3** | Medium | Handover | Ivana |
| Residential | Install kitchen worktops at 31 Oak Ave | Delaney family | **Day 4** | High | Kitchen phase | Luka |
| Residential | Order bathroom fittings for 14 Elm St | Okafor family | **none** | Low | Bathroom phase | Sara |
| Residential | Final snag list at 22 Mill Lane | Brennan family | Day 4 + 12 days | Medium | Handover | Nikola |
| Office | Fit partition walls on the 3rd floor | Marlow Accounting | **Day 3** | High | Fit-out | Ivana |
| Office | Replace ceiling lights in the open-plan office | Marlow Accounting | **Day 4** | Medium | Fit-out | Luka |
| Office | Strip old carpet in reception | Dunmore Dental | Day 4 + 7 days | Low | Demolition | Nikola |

Later areas rely on this data (notifications in `09`, schedules in `08`, jobs in `04`):

- **Due Day 3:** "Tile bathroom at 14 Elm St" (complete it on day 3), "Paint hallway and stairs at 9 Birch Rd" and "Fit partition walls on the 3rd floor" (leave both open, they go overdue on day 4).
- **Due Day 4:** "Install kitchen worktops at 31 Oak Ave" and "Replace ceiling lights in the open-plan office".
- **No deadline:** "Order bathroom fittings for 14 Elm St".

Don't change their deadlines, assignees or status before the day plan reaches them.

- [ ] All eight jobs are created as **New**, each with a friendly ID
- [ ] The Day 3 and Day 4 deadlines show the right dates, and "Order bathroom fittings for 14 Elm St" shows no deadline
- [ ] Each job shows its milestone, and the **Grouped** view lists it under that milestone
- [ ] Each assignee other than the job's creator gets a notification for their job (Nikola gets none for the carpet job he created and assigned to himself)
- [ ] Creating a job with an empty title shows "Title is required"

## ONB-27 A member sees only their own jobs, and can create one
**As:** Luka (member) · **Day:** 1 · **Needs:** ONB-26 done

1. Open **Residential Fit-outs**, **Jobs**.
2. Open **Office Refurbishments**, **Jobs**.
3. In Residential click **+ New Job**. Title "Fix leaking tap at 7 Cedar Close", Client "Whitfield family", **Assign to** Luka, Priority Medium, no deadline. Click **Create job**.

- [ ] Residential lists only "Tile bathroom at 14 Elm St" and "Install kitchen worktops at 31 Oak Ave"
- [ ] Office lists only "Replace ceiling lights in the open-plan office"
- [ ] **Create job** works for a member, and the new job appears in Luka's list
- [ ] Ana sees "Fix leaking tap at 7 Cedar Close" in Residential, along with every other job

## ONB-28 The spring maintenance job and the first notes
**As:** Ana (owner), then Luka, then Ivana · **Day:** 1 · **Needs:** ONB-26 done

1. As Ana, in **Spring Maintenance 2026** create "Quarterly boiler service at Hartley Court" for client "Hartley Court Residents' Association", deadline Day 4 + 14 days, assigned to Ana.
2. As Ana, open "Tile bathroom at 14 Elm St" in Residential. In **Notes**, type "Client confirmed grey 300x600 tiles. Delivery on Thursday morning." and click **Add Note**, then confirm.
3. As Luka, open the same job and add the note "Bathroom floor is level, ready to tile once tiles arrive."
4. As Ivana, open "Fit partition walls on the 3rd floor" in Office Refurbishments and add "Frames delivered to site, waiting for plasterboard."

- [ ] The Spring Maintenance job is created
- [ ] Each note first asks "Notes cannot be edited or deleted. Add anyway?"
- [ ] The notes show on the job with author and time, in order, with no edit or delete button
- [ ] The Office job still shows **Notes**, but not **Relationships**, **Links** or **Job History**, as set in ONB-23

## ONB-29 Forgot password
**As:** Ivana (signed out) · **Day:** 1 · **Needs:** ONB-13 done

1. On the sign-in page click **Forgot Password?**, type `<mailbox>+ivana@<domain>` and click **Submit**.
2. Open **Reset your OpsClear password** in the mailbox and click **Reset password**.
3. Choose a new password and update your password manager.
4. Sign in with the new password.

- [ ] A message says an email will arrive shortly
- [ ] The email arrives in the OpsClear layout, with a **Reset password** button and a link expiry
- [ ] The reset page accepts a new password
- [ ] Signing in with the new password opens the app, and the old password is rejected

## ONB-30 Pine & Pixel: sign-up, the slug rule and the one-org rule
**As:** Petra (new user), then Sara · **Day:** 1 · **Needs:** ONB-01 ticked

1. Sign Petra up as in ONB-04 (**Petra Stanković**, `<mailbox>+petra@<domain>`) and verify.
2. On **Create your organisation**, type **Pine & Pixel Studio** with slug `brt` and click **Create organisation**.
3. Change the slug to `ppx` and click **Create organisation**.
4. On **Set up your subscription**, leave every add-on unselected, choose the smallest plan, and pay with the sandbox test card (ONB-08, step 5). Check that the payment requests go to Paddle hosts with `sandbox` in the name.
5. As Sara, type `/org/new` in the address bar, type "Pine Studio Two" with slug `psx` and click **Create organisation**.
6. As Petra, open the invite for Brightline that Ana sends to `<mailbox>+petra@<domain>` (Ana: **Invites**, send it first) and click **Accept invite**.

- [ ] The slug `brt` is refused with "An organisation with this slug already exists"
- [ ] `ppx` creates **Pine & Pixel Studio** and opens **Set up your subscription**
- [ ] The payment goes through Paddle sandbox hosts, and **Organisation Settings** then shows the Billing box **Active**
- [ ] Sara's attempt fails with "You already belong to an organisation", and she stays in Brightline only
- [ ] Petra can't accept the Brightline invite while she owns Pine & Pixel, and stays in one org only (known bug: `OrgInviteService.acceptInvite` only checks membership of the same org, so this is expected to fail, job to be linked)

Afterwards, revoke the Brightline invite for Petra in Ana's **Invites** list so it doesn't stay pending.

## ONB-31 Pine & Pixel shows gated features as locked
**As:** Petra (owner of Pine & Pixel) · **Day:** 1 · **Needs:** ONB-30 done

1. On **Projects**, click **Create your first project**. Name it **Brand refresh for Juniper Cafe**, no block reasons, and create it.
2. Click **+ New Job**, add "Design menu cards for Juniper Cafe" (Client "Juniper Cafe", deadline in 2 weeks) and create it.
3. Look at the project's top bar. Click one of the locked items.
4. Open **Settings**, scroll to **Job screen**.
5. Open **Organisation Settings**.

- [ ] The project and the job are created
- [ ] The top bar shows a lock next to add-on features such as Dashboard, Stats, Milestones and Schedules, and clicking one says it is not included in your plan, with **Upgrade in org settings**
- [ ] All five sections under **Sections shown on jobs** are off and marked **Not in your plan**
- [ ] **Require approval to complete jobs** is locked with "Requires an active Approvals add-on."
- [ ] **Org Templates** isn't shown on **Organisation Settings**

## ONB-32 No data leaks between the orgs
**As:** Petra, then Ana · **Day:** 1 · **Needs:** ONB-31 and ONB-26 done

1. As Ana, copy the address of "Tile bathroom at 14 Elm St" from the browser bar.
2. As Petra, paste it into the browser bar.
3. As Petra, open **Projects**. As Ana, open **Projects**.
4. As Petra, open **Manage members**. As Ana, open **Manage members**.
5. As Ana, open Residential **Settings**, **Members**, search `<mailbox>+petra` and try to add Petra as **Member**.
6. As Ana, open "Order bathroom fittings for 14 Elm St", click **Edit** and try to assign it to Petra.

- [ ] Petra can't see "Tile bathroom at 14 Elm St" or any other Brightline data
- [ ] Petra's **Projects** lists only **Brand refresh for Juniper Cafe**
- [ ] Ana's **Projects** lists only Brightline projects
- [ ] Petra's members list is Petra alone, and Ana's has the five Brightline people
- [ ] Petra can't be added to a Brightline project (known bug: `ProjectMemberService.addMember` only checks that the user exists, so this is expected to fail, job to be linked)
- [ ] A Brightline job can't be assigned to Petra (known bug: `JobService` only checks that the assignee exists, so this is expected to fail, job to be linked)

## ONB-33 Luka switches to Serbian
**As:** Luka (member) · **Day:** 1 · **Needs:** ONB-13 done

1. Open the user menu and click **Account settings**.
2. Under **Appearance**, change **Language** to Serbian.
3. Open **Projects** and a project.
4. Sign out and sign back in.

- [ ] The language changes at once, without a reload
- [ ] The menus, buttons and headings are in Serbian
- [ ] The language is still Serbian after signing back in

From now on Luka plays every scenario with the Serbian UI. The Serbian screens themselves are
judged in Luka's scenarios in the other area files.

## Not manual
- Invite emails never failing the invite when sending fails: covered by `InviteEmailListenerTest`.
- The Keycloak `verifyEmail` realm setting on a fresh install: covered by the `@smoke` E2E `auth-flows/registration.cy.ts`, which clicks the real verification link.
- Two simultaneous org creations with the same slug: covered by `OrganisationServiceTest` and the unique index from migration V039.

## Findings
- ONB-05: JOB-395 (UX) **Create your organisation** has no **Log out**, language or theme controls, so a signed-in user with no org is stuck on the page. Fix in PR #582; ONB-06 now checks the new top bar.
- ONB-08: JOB-396 (Bug) **Continue to payment** returns 500 from `POST /api/organisations/{orgId}/subscription/paddle`, so the payment form never opens and the org cannot subscribe. Blocks ONB-08 and everything after it that needs the subscription.
