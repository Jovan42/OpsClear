# Onboarding

Last run: 2026-11-19 to 2026-11-20, Jovan
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
3. Scroll to the bottom of the page and click each social icon in the green footer.
4. Open `/features`. Open one card's demo and look at the badges.
5. Click **User guide** in the top bar.
6. Type `/projects` in the address bar.

- [x] The home page shows the headline, **Get started** and **Log in**
- [x] The theme button cycles light, dark and system, and the page follows it
- [x] The language button switches the page between English and Serbian and back
- [ ] The footer shows LinkedIn, X, Facebook and Instagram icons, and each opens the OpsClear account in a new tab
- [x] `/features` lists every feature, with an **Included** badge on base features and the add-on name on gated ones
- [x] A card's demo opens and can be stepped through
- [x] The user guide opens without a login
- [x] `/projects` while logged out sends you to the sign-in page, not an error
- [ ] The home page footer, the bottom of `/features` and the bottom of the user guide show "Questions? Write to support@opsclear.app", and clicking the address opens a new email to it

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

- [x] Each chosen card has a green border, and the **Monthly total** changes by that add-on's price when you click it
- [x] **Continue to payment** opens the payment form on the page, and its requests go to Paddle hosts with `sandbox` in the name
- [x] The payment form shows the same **Total** as **Monthly total**
- [x] **Processing your payment** appears and the page updates by itself, without a refresh (JOB-397 fixed, rechecked in ONB-30)
- [x] **Organisation Settings** now opens, and the Billing box shows **Active**
- [x] **Billing history** (click it open) lists one payment with a date, a status and the amount

## ONB-09 Organisation settings as the owner
**As:** Ana (owner) · **Day:** 1 · **Needs:** ONB-08 done

1. Open **Organisation Settings** (user menu, **Organisation**).
2. Look at every section from the top.

- [x] **Members** has **Manage members** and **Invites** buttons
- [x] **Details** shows **Brightline Renovations**, slug **BRT** and "Created by Ana Petrović", with the name and slug boxes editable
- [x] **ID prefixes** shows the next project, job, milestone and template IDs
- [x] **Org Templates** is there, because Job templates is bought
- [x] **Subscription**, **Billing history** and **Danger zone** are there

## ONB-10 Send the team's invites
**As:** Ana (owner) · **Day:** 1 · **Needs:** ONB-08 done

1. Open **Invites**. Click **Send invite** with the box empty.
2. Type `<mailbox>+nikola@<domain>` and click **Send invite**.
3. Send the same address a second time.
4. Send invites to `<mailbox>+ivana@<domain>`, `<mailbox>+luka@<domain>` and `<mailbox>+sara@<domain>`.
5. Open the mailbox.

- [x] The empty box shows "Email is required"
- [x] Nikola's invite appears in the list with Ana as inviter and "Expires in 7 days"
- [x] The second invite to the same address is refused with "A pending invite already exists for this email", and the list still has one row for it
- [x] The list shows four pending invites, each with **Copy link**, **Resend email** and **Revoke**
- [x] Four invite emails arrive, titled "You're invited to join Brightline Renovations on OpsClear", each saying Ana Petrović invited you, with an **Accept invite** button and the expiry date

## ONB-11 Nikola joins from the invite
**As:** Nikola (new user) · **Day:** 1 · **Needs:** ONB-10 done

1. In the mailbox, open Nikola's invite and click **Accept invite**.
2. On the sign-in page click **Register** and register as **Nikola Jovanović** with `<mailbox>+nikola@<domain>`.
3. Verify the email as in ONB-04.
4. Click **Accept invite** in the invite email again.
5. On **You've been invited**, click **Accept invite**.
6. Click the same invite link a second time.

- [x] The invite link needs a login first, and the sign-in page opens
- [x] Registering shows **Verify your email**, and the app stays closed until the link is clicked
- [x] After verifying, the invite link opens **You've been invited** with an **Accept invite** button
- [x] **You've joined the organisation** appears, then **Projects** opens after about two seconds
- [x] Nikola sees no projects yet
- [x] The second click shows a red message that the invite is no longer valid, and a **Go to projects** button

## ONB-12 The invite only works for the invited address
**As:** Nikola (org member for now) · **Day:** 1 · **Needs:** ONB-11 done, Ivana's invite still pending

1. Open Ivana's invite email and click its **Accept invite** link while signed in as Nikola.
2. Click **Accept invite** on the page.

- [x] The page shows "This invite was sent to a different email address"
- [x] Ivana's invite is still pending in Ana's list

## ONB-13 Ivana, Luka and Sara join
**As:** Ivana, then Luka, then Sara (each in their own profile) · **Day:** 1 · **Needs:** ONB-12 done

1. For each persona, repeat the steps of ONB-11 with their own invite email: register (**Ivana Marković**, **Luka Ilić**, **Sara Đorđević**), verify, click **Accept invite** again, accept.
2. Sign each one out with **Sign out** in the user menu.

- [x] Ivana, Luka and Sara each get **You've joined the organisation**
- [x] Ana's pending invites list is now empty
- [x] Ana has an **Invite accepted** notification for each of the four people, in the notification centre (bell)
- [x] **Sign out** returns each persona to the public home page

## ONB-14 Promote Nikola to Admin
**As:** Ana (owner) · **Day:** 1 · **Needs:** ONB-13 done

1. Open **Manage members**. Look at the table and the form above it.
2. Change Nikola's role box from **Member** to **Admin**.
3. Look at Ana's own row. Reload the page.

- [x] The table lists Ana (Owner badge), Nikola, Ivana, Luka and Sara, and the form above it is **Search by email…**
- [x] Ana's row has a badge and no role box or **Remove**
- [x] Nikola's role changes to **Admin** and stays after the reload
- [x] Ivana, Luka and Sara stay **Member**

## ONB-15 An admin's powers in the organisation
**As:** Nikola (org admin) · **Day:** 1 · **Needs:** ONB-14 done

1. Open **Organisation Settings**, **Manage members** and **Invites**.
2. Try to change the name on **Organisation Settings**.
3. Look at the members table.

- [x] **Invites** is available, and Nikola can open the list of pending invites
- [x] The name and slug boxes are greyed out, with no **Save changes** button
- [x] **Subscription**, **Billing history** and **Danger zone** aren't shown
- [x] The members table shows role badges, not role boxes, so Nikola can't change anyone's role
- [x] The members table has the add form and **Remove** on non-Owner rows, and no **Remove** on the Owner row

## ONB-16 A member can't invite, add or manage
**As:** Luka (org member) · **Day:** 1 · **Needs:** ONB-13 done

1. Open **Organisation Settings**.
2. Open **Manage members**.
3. Type `/org/invites` in the address bar. Type `<mailbox>+stranger@<domain>` and click **Send invite**.

- [x] **Organisation Settings** has **Manage members** but no **Invites** button
- [x] The name, slug and **ID prefixes** are greyed out
- [x] **Subscription**, **Billing history**, **Credit balance** and **Danger zone** aren't shown
- [x] The members table has no add form, no role boxes and no **Remove**
- [x] Sending the invite fails with "Insufficient permissions: OWNER or ADMIN role required"
- [x] The stranger address doesn't appear in Ana's pending invites

## ONB-17 Create Residential Fit-outs
**As:** Ana (org owner) · **Day:** 1 · **Needs:** ONB-14 done, Ana on **Projects**

1. Click **Create your first project**.
2. Click **Create project** with the name empty.
3. Type **Residential Fit-outs** in **Name**. In **Description**, type "Kitchens, bathrooms and full home refits for private clients."
4. Under **Block reasons**, add **Waiting on client**, **Materials not delivered** and **Waiting on inspection** (type each and click **Add**).
5. Click **Create project**.

- [x] The empty name shows "Name is required"
- [x] The three block reasons show as removable entries before creating
- [x] **Create project** opens the new project and Ana is its Owner
- [x] **Projects** lists **Residential Fit-outs** with an **Owner** badge, under the **Active** tab
- [x] **Project Settings** lists the three block reasons

## ONB-18 Create Office Refurbishments
**As:** Nikola (org admin) · **Day:** 1 · **Needs:** ONB-14 done

1. Open **Projects**. Click **Create your first project**.
2. Type **Office Refurbishments** in **Name** and "Fit-outs and repairs for offices and shops." in **Description**.
3. Add the block reasons **Waiting on landlord** and **Waiting on permits**, then click **Create project**.

- [x] Nikola can create a project while only an org admin, and is its Owner
- [x] Nikola's **Projects** list shows only this project
- [x] Ana's **Projects** list doesn't show it, because Ana isn't a member of it

## ONB-19 Create Spring Maintenance 2026
**As:** Ana (org owner) · **Day:** 1 · **Needs:** ONB-17 done

1. Click **+ New Project**.
2. Type **Spring Maintenance 2026** in **Name** and "Seasonal servicing for existing maintenance contracts." in **Description**. Leave block reasons empty.
3. Click **Create project**.

- [x] The project exists and Ana is its Owner
- [x] **Project Settings** says "No block reasons configured"
- [x] The **Active** tab lists two projects for Ana: **Residential Fit-outs** and **Spring Maintenance 2026**

Leave this project active. `03-projects.md` archives it later.

## ONB-20 Add the people to Residential Fit-outs
**As:** Ana (project owner) · **Day:** 1 · **Needs:** ONB-17 and ONB-14 done

1. Open **Residential Fit-outs**, click **Settings**, find **Members**.
2. Type `<mailbox>+nikola` in **Search by email…**, pick Nikola, choose **Admin** and click **Add**.
3. Add Ivana, Luka and Sara with the role **Member**.
4. Type `<mailbox>+nikola` in the search again.

- [x] Nikola is added as **Admin**, and the table shows him with that role
- [x] Ivana, Luka and Sara are added as **Member**
- [x] The second search shows Nikola with "(already a member)"
- [x] The table lists Ana as **Owner** with no role box and no **Remove**
- [x] Nikola, Ivana, Luka and Sara each get a notification that they were added to the project

## ONB-21 Add the people to Office Refurbishments
**As:** Nikola (project owner) · **Day:** 1 · **Needs:** ONB-18 done

1. Open **Office Refurbishments**, click **Settings**, find **Members**.
2. Add Ivana as **Admin**.
3. Add Luka as **Member**.

- [x] Ivana is **Admin** and Luka is **Member** in the table
- [x] Ivana and Luka each get a notification that they were added

## ONB-22 Residential Fit-outs: approval gate on, all sections visible
**As:** Ana (project owner) · **Day:** 1 · **Needs:** ONB-20 done

1. Open **Residential Fit-outs**, **Settings**, scroll to **Job screen**.
2. Look at **Sections shown on jobs**.
3. Switch on **Require approval to complete jobs**.
4. Reload the page.

- [x] **Notes**, **Approvals**, **Relationships**, **Links** and **Job status history** are all switched on, and none says **Not in your plan**
- [x] Switching the gate on shows "Jobs now need approval to complete"
- [x] After the reload the gate is still on

## ONB-23 Office Refurbishments: gate off, some sections hidden
**As:** Nikola (project owner) · **Day:** 1 · **Needs:** ONB-21 done

1. Open **Office Refurbishments**, **Settings**, scroll to **Job screen**.
2. Switch off **Relationships**, **Links** and **Job status history**. Leave **Notes** and **Approvals** on.
3. Look at **Require approval to complete jobs**. Reload the page.

- [x] Each switch-off shows a message such as "Relationships now hidden on jobs"
- [x] **Require approval to complete jobs** is off
- [x] After the reload the three sections are still off and **Notes** and **Approvals** are still on

## ONB-24 What a project member can't change
**As:** Luka (member of Residential Fit-outs and Office Refurbishments) · **Day:** 1 · **Needs:** ONB-22 and ONB-23 done

1. Open **Residential Fit-outs**, **Settings**.
2. Look at **Details**, **Members** and **Job screen**.
3. Open **Office Refurbishments**, **Settings**, and look at the same sections.

- [x] The name and description are greyed out and there's no **Save changes**
- [x] **Members** shows the people without the add form, role boxes or **Remove**
- [x] The section switches and **Require approval to complete jobs** are locked, with the note "Only project Owners and Admins can change this."
- [x] Residential shows the gate on and Office shows it off
- [x] There's no **Danger zone**

## ONB-25 Milestones
**As:** Ana (Residential owner), then Nikola (Office owner), then Luka · **Day:** 1 · **Needs:** ONB-22 and ONB-23 done

1. As Ana, open **Residential Fit-outs**, **Milestones**, click **+ New Milestone**. Create **Kitchen phase** (Deadline: Day 4 + 7 days), **Bathroom phase** (Day 4 + 3 days) and **Handover** (Day 4 + 14 days), with a short description each.
2. As Nikola, open **Office Refurbishments**, **Milestones**. Create **Demolition** (Day 3), **Fit-out** (Day 4 + 10 days) and **Handover** (Day 4 + 17 days).
3. As Luka, open **Milestones** in Residential.

- [x] The three Residential milestones are listed, each with its due date
- [x] The three Office milestones are listed
- [x] The same name **Handover** in both projects doesn't clash
- [x] Luka sees the milestones but no **+ New Milestone** button

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

- [x] All eight jobs are created as **New**, each with a friendly ID
- [x] The Day 3 and Day 4 deadlines show the right dates, and "Order bathroom fittings for 14 Elm St" shows no deadline
- [x] Each job shows its milestone, and the **Grouped** view lists it under that milestone
- [x] Each assignee other than the job's creator gets a notification for their job (Nikola gets none for the carpet job he created and assigned to himself)
- [x] Creating a job with an empty title shows "Title is required"

## ONB-27 A member sees only their own jobs, and can create one
**As:** Luka (member) · **Day:** 1 · **Needs:** ONB-26 done

1. Open **Residential Fit-outs**, **Jobs**.
2. Open **Office Refurbishments**, **Jobs**.
3. In Residential click **+ New Job**. Title "Fix leaking tap at 7 Cedar Close", Client "Whitfield family", **Assign to** Luka, Priority Medium, no deadline. Click **Create job**.

- [x] Residential lists only "Tile bathroom at 14 Elm St" and "Install kitchen worktops at 31 Oak Ave"
- [x] Office lists only "Replace ceiling lights in the open-plan office"
- [x] **Create job** works for a member, and the new job appears in Luka's list
- [x] Ana sees "Fix leaking tap at 7 Cedar Close" in Residential, along with every other job

## ONB-28 The spring maintenance job and the first notes
**As:** Ana (owner), then Luka, then Ivana · **Day:** 1 · **Needs:** ONB-26 done

1. As Ana, in **Spring Maintenance 2026** create "Quarterly boiler service at Hartley Court" for client "Hartley Court Residents' Association", deadline Day 4 + 14 days, assigned to Ana.
2. As Ana, open "Tile bathroom at 14 Elm St" in Residential. In **Notes**, type "Client confirmed grey 300x600 tiles. Delivery on Thursday morning." and click **Add Note**, then confirm.
3. As Luka, open the same job and add the note "Bathroom floor is level, ready to tile once tiles arrive."
4. As Ivana, open "Fit partition walls on the 3rd floor" in Office Refurbishments and add "Frames delivered to site, waiting for plasterboard."

- [x] The Spring Maintenance job is created
- [x] Each note first asks "Notes cannot be edited or deleted. Add anyway?"
- [x] The notes show on the job with author and time, in order, with no edit or delete button
- [x] The Office job still shows **Notes**, but not **Relationships**, **Links** or **Job History**, as set in ONB-23

## ONB-29 Forgot password
**As:** Ivana (signed out) · **Day:** 1 · **Needs:** ONB-13 done

1. On the sign-in page click **Forgot Password?**, type `<mailbox>+ivana@<domain>` and click **Submit**.
2. Open **Reset your OpsClear password** in the mailbox and click **Reset password**.
3. Choose a new password and update your password manager.
4. Sign in with the new password.

- [x] A message says an email will arrive shortly
- [x] The email arrives in the OpsClear layout, with a **Reset password** button and a link expiry
- [x] The reset page accepts a new password
- [x] Signing in with the new password opens the app, and the old password is rejected

## ONB-30 Pine & Pixel: sign-up, the slug rule and the one-org rule
**As:** Petra (new user), then Sara · **Day:** 1 · **Needs:** ONB-01 ticked

1. Sign Petra up as in ONB-04 (**Petra Stanković**, `<mailbox>+petra@<domain>`) and verify.
2. On **Create your organisation**, type **Pine & Pixel Studio** with slug `brt` and click **Create organisation**.
3. Change the slug to `ppx` and click **Create organisation**.
4. On **Set up your subscription**, leave every add-on unselected, choose the smallest plan, and pay with the sandbox test card (ONB-08, step 5). Check that the payment requests go to Paddle hosts with `sandbox` in the name.
5. As Sara, type `/org/new` in the address bar, type "Pine Studio Two" with slug `psx` and click **Create organisation**.
6. As Petra, open the invite for Brightline that Ana sends to `<mailbox>+petra@<domain>` (Ana: **Invites**, send it first) and click **Accept invite**.

- [x] The slug `brt` is refused with "An organisation with this slug already exists"
- [x] `ppx` creates **Pine & Pixel Studio** and opens **Set up your subscription**
- [x] The payment goes through Paddle sandbox hosts, and **Organisation Settings** then shows the Billing box **Active**
- [x] Sara's attempt fails with "You already belong to an organisation", and she stays in Brightline only
- [x] Petra can't accept the Brightline invite while she owns Pine & Pixel, and stays in one org only (known bug: `OrgInviteService.acceptInvite` only checks membership of the same org, so this is expected to fail, job to be linked)

Afterwards, revoke the Brightline invite for Petra in Ana's **Invites** list so it doesn't stay pending.

## ONB-31 Pine & Pixel shows gated features as locked
**As:** Petra (owner of Pine & Pixel) · **Day:** 1 · **Needs:** ONB-30 done

1. On **Projects**, click **Create your first project**. Name it **Brand refresh for Juniper Cafe**, no block reasons, and create it.
2. Click **+ New Job**, add "Design menu cards for Juniper Cafe" (Client "Juniper Cafe", deadline in 2 weeks) and create it.
3. Look at the project's top bar. Click one of the locked items.
4. Open **Settings**, scroll to **Job screen**.
5. Open **Organisation Settings**.

- [x] The project and the job are created
- [x] The top bar shows a lock next to add-on features such as Dashboard, Stats, Milestones and Schedules, and clicking one says it is not included in your plan, with **Upgrade in org settings**
- [x] All five sections under **Sections shown on jobs** are off and marked **Not in your plan**
- [x] **Require approval to complete jobs** is locked with "Requires an active Approvals add-on."
- [x] **Org Templates** isn't shown on **Organisation Settings**

## ONB-32 No data leaks between the orgs
**As:** Petra, then Ana · **Day:** 1 · **Needs:** ONB-31 and ONB-26 done

1. As Ana, copy the address of "Tile bathroom at 14 Elm St" from the browser bar.
2. As Petra, paste it into the browser bar.
3. As Petra, open **Projects**. As Ana, open **Projects**.
4. As Petra, open **Manage members**. As Ana, open **Manage members**.
5. As Ana, open Residential **Settings**, **Members**, search `<mailbox>+petra` and try to add Petra as **Member**.
6. As Ana, open "Order bathroom fittings for 14 Elm St", click **Edit** and try to assign it to Petra.

- [x] Petra can't see "Tile bathroom at 14 Elm St" or any other Brightline data
- [x] Petra's **Projects** lists only **Brand refresh for Juniper Cafe**
- [x] Ana's **Projects** lists only Brightline projects
- [x] Petra's members list is Petra alone, and Ana's has the five Brightline people
- [x] Petra can't be added to a Brightline project (known bug: `ProjectMemberService.addMember` only checks that the user exists, so this is expected to fail, job to be linked)
- [x] A Brightline job can't be assigned to Petra (known bug: `JobService` only checks that the assignee exists, so this is expected to fail, job to be linked)

## ONB-33 Luka switches to Serbian
**As:** Luka (member) · **Day:** 1 · **Needs:** ONB-13 done

1. Open the user menu and click **Account settings**.
2. Under **Appearance**, change **Language** to Serbian.
3. Open **Projects** and a project.
4. Sign out and sign back in.

- [x] The language changes at once, without a reload
- [x] The menus, buttons and headings are in Serbian
- [x] The language is still Serbian after signing back in

From now on Luka plays every scenario with the Serbian UI. The Serbian screens themselves are
judged in Luka's scenarios in the other area files.

## ONB-34 Contact support from the user menu
**As:** Ana (owner), then Luka (member, Serbian UI) · **Day:** 1 · **Needs:** ONB-08 and ONB-33 done

1. As Ana, open the user menu and look at the items. Click **Contact support**.
2. As Luka, open the user menu on a phone-width window with the dark theme on.

- [ ] **Contact support** comes right after **Feedback**
- [ ] Clicking it closes the menu and opens a new email to support@opsclear.app
- [ ] Luka's menu says **Kontaktirajte podršku** on one line, readable in the dark theme

## ONB-35 The legal pages open logged out and are linked
**As:** Visitor (not signed in, a private window) · **Day:** 1 · **Needs:** nothing. JOB-420 draft pages: the "Draft, not yet reviewed" banner and `[[…]]` placeholders are expected until the texts are final.

1. Open the landing page and scroll to the footer. Click **Terms of Service**, go back, then **Privacy Policy**, go back, then **Refund policy**.
2. Open **Features** and, at the bottom, click each of the three links. Do the same on **Guide**.
3. On a phone, or a 375 px wide window, open `/terms`. Switch to dark mode and to Serbian with the **EN** button.
4. Start **Register** from the landing page and read the text above the **Register** button. Click both links.
5. As Ana, open **Account settings** and scroll to the bottom.

- [ ] Each page opens without signing in, with a title, a "Last updated" date, the draft banner, the note that the English version is binding, and the company details section
- [ ] The landing footer, **Features** and **Guide** each show the three links, and they open the right pages
- [ ] On a phone the text fits the screen with no sideways scrolling, in light and dark mode
- [ ] Serbian shows the full page in Serbian, still saying the English version is binding
- [ ] The register page says "By creating an account you agree to the Terms of Service and the Privacy Policy", and both links open the pages (in a new tab where the theme keeps it)
- [ ] **Account settings** shows the three links at the bottom
## ONB-36 Early access page and the super-admin Sign-ups tab
**As:** Anonymous visitor (private window), then the developer (super user) · **Day:** 1 · **Needs:** a super user account on the environment

1. Open `/early-access?code=TRIAL&utm_source=linkedin&utm_medium=social&utm_campaign=launch` on a desktop-width window.
2. Click **Get notified** without filling anything in.
3. Type `<mailbox>+waitlist@<domain>`, tick **Email me when OpsClear launches** and click **Get notified**.
4. Open the page again with the same link, sign up the same address again.
5. Open `/early-access?code=NOPE`, type `<mailbox>+waitlist2@<domain>`, tick the box and click **Get notified**.
6. Repeat steps 1 and 3 with `<mailbox>+waitlist3@<domain>` on a phone-width window with the **SR** language on.
7. As the developer, open `/admin` and click **Sign-ups**.
8. Click **Download CSV** and open the file in a spreadsheet.
9. Click the bin icon on the `+waitlist3` row and confirm **Delete**.

- [ ] The page shows **Get notified at launch**, "30 days free, no credit card needed…", and **Promo code** prefilled with `TRIAL`
- [ ] Step 2 shows "Enter your email" and "Tick the box so we can email you at launch", and sends nothing
- [ ] **Privacy Policy** opens the Privacy Policy in a new tab (the `/privacy` page comes with JOB-420, PR #613), and it lists the launch list
- [ ] After submitting, the form is replaced by "Thanks! We'll email you when we launch."
- [ ] The same address again shows the same thank-you, with no error
- [ ] `NOPE` shows "This code isn't valid" under **Promo code**, and no thank-you
- [ ] At phone width nothing scrolls sideways, and the Serbian page reads **Obavestite me o pokretanju**
- [ ] **Sign-ups** lists the list **Early access** with a count, newest first: `+waitlist3` with language `sr`, and `+waitlist` once, with code `TRIAL`, source `linkedin`, medium `social`, campaign `launch`
- [ ] The CSV file is named `early-access-<today>.csv` and opens with one column per field (date, email, code, redeemed, language, source, medium, campaign)
- [ ] After **Delete**, the `+waitlist3` row is gone and the count drops by one

Afterwards, delete the `+waitlist` row too.

## ONB-37 Sign-up closed until launch
**As:** Anonymous visitor, a new user, Ana (owner), then the developer (super user) · **Day:** 1 · **Needs:** the backend started with `OPSCLEAR_SIGNUP_OPEN=false`; run on a test environment, never during the Brightline world build

1. Open the home page and `/features` in a private window. Look at the Sign up buttons and click one.
2. Register a new user `<mailbox>+closed@<domain>` through Keycloak's own **Register** link on the login page, and verify the email.
3. On the page that opens, click **Join the list**, then tick **Email me when OpsClear launches** and click **Join the list** again.
4. Click **Log out**.
5. As Ana, invite `<mailbox>+closedinvite@<domain>` to Brightline. Open the invite link in a private window, create the account and accept the invite.
6. As the developer (super user, no organisation yet on this environment), open `/org/new`.

- [ ] The hero, the green footer and the `/features` buttons read **Get notified** and open `/early-access`
- [ ] The new user sees **We're not open yet** and "We'll email you at launch." with their account email, not the create form
- [ ] **Join the list** first asks for the tick, then shows "Thanks! We'll email you when we launch."
- [ ] **Log out** signs the user out and returns to the home page
- [ ] The invited person creates an account and joins Brightline without seeing the card
- [ ] The developer gets the **Create your organisation** form and can create a test org

## Not manual
- Invite emails never failing the invite when sending fails: covered by `InviteEmailListenerTest`.
- The Keycloak `verifyEmail` realm setting on a fresh install: covered by the `@smoke` E2E `auth-flows/registration.cy.ts`, which clicks the real verification link.
- Two simultaneous org creations with the same slug: covered by `OrganisationServiceTest` and the unique index from migration V039.
- Early access sign-up API (ADR-0066: validation, codes, duplicates in any case, honeypot): covered by `EmailSignupIntegrationTest`. The page itself is ONB-36.
- The honeypot field on `/early-access` (off-screen, only bots fill it): covered by `emailSignup.test.ts` (sent as filled) and `EmailSignupIntegrationTest` (nothing stored).
- Sign-up switch closed (`OPSCLEAR_SIGNUP_OPEN=false`): org creation returns 403 for a normal user and 201 for a super user, and invite accept still works: covered by `SignupClosedIntegrationTest`. The screens are ONB-37, and the 403 showing the "not open yet" card is covered by the E2E `organisation-management/signup-closed.cy.ts`.
- The 429 message on `/early-access` (nginx limit of 5 a minute): covered by the E2E `landing-page/early-access.cy.ts` with a stubbed 429.
- A new organisation starts a 30-day trial with every add-on, and `access` (state, trial end, limits, usage, `lockoutEnabled`) in the organisation response (ADR-0067): covered by `OrgAccessIntegrationTest` and `OrgAccessServiceTest`. The 402 lock-out of a locked org and its allowlist, with `OPSCLEAR_LOCKOUT_ENABLED=true`: covered by `OrgLockoutIntegrationTest` and `OrgLockInterceptorTest`. The trial banner and paywall screens come with JOB-448. Pine & Pixel pays for a plan in ONB-30, so it's a paid org without add-ons and NHR-22/NHR-23 still hold.

## Findings
- ONB-05: JOB-395 (UX) **Create your organisation** has no **Log out**, language or theme controls, so a signed-in user with no org is stuck on the page. Fix in PR #582; ONB-06 now checks the new top bar.
- ONB-08: JOB-396 (Bug) **Continue to payment** returns 500 from `POST /api/organisations/{orgId}/subscription/paddle`, so the payment form never opens and the org cannot subscribe. Cause was an expired Paddle sandbox API key; the code fix (clear 502 instead of a bare 500) is in PR #585 and the key was replaced.
- ONB-08: JOB-397 (Bug) right after a successful Paddle payment the page shows a 500 instead of **Processing your payment**; the subscription is there after a manual refresh.
- ONB-10/ONB-11: JOB-399 (UX), with JOB-398 (Backend) first: a new invitee lands on the login page with no invite context, re-types the email, verifies it and clicks Accept a second time. Proposed: a public invite page with sign-up and automatic joining.
- ONB-20: JOB-400 (Bug) the project member search (**Search by email…**) lists registered users from every organisation, not just Brightline members, and only matches the email, not first or last name.
- ONB-29: JOB-404 (UX) there is no way to change your password from inside the app; a signed-in user has to sign out and use **Forgot password?**.
