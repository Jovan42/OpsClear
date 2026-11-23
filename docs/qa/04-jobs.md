# Jobs

Last run: 2026-11-23, Jovan (in progress)
Guide: /guide/create-job, /guide/job-blocked, /guide/finding-jobs, /guide/roles-permissions, /guide/job-done, /guide/notes-links-history

Jobs are the core of the app: the list, the job screen, creating and editing, assigning, the four
statuses, block reasons, deleting, filters, search and links. The world is built by
`01-onboarding.md` ([README](README.md#test-world)). Run it on any day after ONB-33, in order.

**What other files already did, and how this one stays out of their way.**

- The eight day-1 jobs from ONB-26 and the two from ONB-27 and ONB-28 stay as they are. This file
  only reads them (list, search, filters, due-soon highlight) and never changes their status,
  deadline or assignee. The due-soon and overdue checks use jobs this file creates itself, because
  the ONB-26 deadlines move into the past as the run goes on.
- `09-notifications.md` creates its own working jobs in Residential Fit-outs and relies on their
  status, assignee and deadline. This file doesn't touch them either, and creates its own jobs
  (below). Lists in Residential can therefore hold more rows than ONB-26 made, so list checks say
  "includes" or "every row shown", never an exact count.
- `08-schedules.md` makes jobs such as `Site safety check for Residential Fit-outs - <date>` and
  `Toolbox talk week <n>` in Residential from its day 2 on. They may be in the lists when this file
  runs, so every list check says "includes" or "every row shown", never an exact count.
- Spring Maintenance 2026 is archived late by `03-projects.md`, so no job scenario here uses it.

**Jobs this file creates** (all in the test world, fictional clients):

| Job | Project | Created in | Used by |
|-----|---------|-----------|---------|
| **A** Install extractor fan at 5 Linden Walk | Residential | JOB-03 | edit, member and admin status changes, links, filters |
| **B** Strip kitchen floor at 31 Oak Ave | Residential | JOB-03 | assigning, owner status changes, delete |
| **C** Fix roof flashing at 12 Rowan Close | Residential | JOB-03 | block reasons, links, Mine filter |
| **F** Collect keys for 8 Pear Tree Rd | Residential | JOB-10 | overdue highlight, delete by an admin |
| **G**, **H**, **K** Check scaffolding at 17 Cherry Hill, Order skirting boards for 17 Cherry Hill, Book skip for 17 Cherry Hill | Residential | JOB-10 | due-soon highlight, relative deadlines; deleted at the end of JOB-11 |
| **D** Install fire door at Marlow Accounting | Office Refurbishments | JOB-17 | a member in a project without the approval gate; deleted at the end of JOB-23 |
| **P** Draft logo options for Juniper Cafe | Pine & Pixel | JOB-22 | an org without add-ons |

**Deadline alerts stay clean.** Run JOB-10 and JOB-11 in one sitting without crossing 06:00 UTC: F has a past deadline and is open until JOB-11 step 4, and an overdue alert for it would reach Ana and Nikola. The 06:00 UTC alerts only look at open jobs with a deadline. F is
completed and then deleted, G, H, K and D are deleted, and A, B, C and P end with no deadline, so
nothing this file leaves behind raises a DEADLINE_SOON or OVERDUE entry for `09-notifications.md`.
The jobs with deadlines are assigned to Ana or Luka, and all of it happens in one sitting.

**Notifications this file sends** (by title they are easy to tell apart in the centres):
- Ivana: "Assigned you to Install extractor fan…" (JOB-03), two "Deadline or priority changed" for A
  (JOB-04), and Status changed entries for A when Nikola completes it and Ana reopens it (JOB-15).
- Nikola: one "Assigned you to Strip kitchen floor…" for B (JOB-04).
- Luka: "Assigned you to Install fire door…" (JOB-17), a Status changed entry when Ivana reopens D,
  and "Deleted Install fire door…" when Nikola deletes D (JOB-23).
- Ana: "Deleted Collect keys…" when Nikola deletes F (JOB-18).
- Ana and Nikola (as owner and admin of Residential): Status changed and Job blocked entries for A, B,
  C and F as the others change them (JOB-11 to JOB-15). Nikola (owner of Office) and Ivana (admin of
  Office) get the same kind of entries for D only (JOB-17).

**Settings.** Every user's settings start at their defaults (Row density Comfortable, Deadline format
Absolute, Due-soon highlight 3 days, Default assignee filter Everyone, Default view mode Grouped).
Scenarios that change a setting change it back at the end. The settings page itself is covered in
`11-account.md`; here only its effect on jobs is checked.

**Dates.** "Today" is the current date (UTC). Near midnight UTC, work the dates out from the UTC clock. The due-soon check counts calendar days in your own time zone.

**Luka** uses the Serbian UI. His scenarios judge that the screens read in Serbian and never check
exact English text.

## JOB-01 The New Job form checks its input
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** ONB-26 done

1. Open **Residential Fit-outs**, **Jobs**, click **+ New Job**.
2. Click **Create job** with **Title** empty.
3. Paste a text of 256 characters into **Title** (type a letter, select it, copy it and paste it until the box is long) and click **Create job**.
4. Replace it with the title **Test** and click **Cancel**. Click **+ New Job** again.
5. Open the **Milestone** box.

- [x] The form is titled **New Job**, and **Priority** starts on **Medium**
- [x] The empty title shows "Title is required" and no job is created
- [x] The 256 characters show "Max 255 characters" and no job is created
- [x] **Cancel** closes the form without creating a job
- [x] Reopening the form shows every box empty again, not the "Test" title
- [x] **Milestone** offers **No milestone** and includes **Kitchen phase**, **Bathroom phase** and **Handover**

## JOB-02 The assignee picker offers project members, found by name or email
**As:** Ana (owner, Residential Fit-outs), then Nikola (owner, Office Refurbishments) · **Day:** any, after ONB-33 · **Needs:** JOB-01 done. The picker lists the members of the project, who are all in the organisation, so nobody from Pine & Pixel can appear. The org-wide search for adding project members is in `03-projects.md`.

1. As Ana, click **+ New Job** in Residential, then click the **Assign to** box.
2. Type `ana`.
3. Replace it with `<mailbox>+luka`.
4. Replace it with `petra`, then with `<mailbox>+petra`.
5. Replace it with `+luka`, pick Luka. Look at the box, then click the **×** inside it. Close the form with **Cancel**.
6. As Nikola, open **Office Refurbishments**, **Jobs**, **+ New Job**, click **Assign to**.

- [x] The empty box lists Ana, Nikola, Ivana, Luka and Sara, each with their email under the name
- [x] `ana` lists Ana Petrović and Ivana Marković, matching part of the name
- [x] The email fragment `<mailbox>+luka` lists Luka Ilić only
- [x] `petra` and `<mailbox>+petra` list nobody, because Petra isn't in Brightline
- [x] Picking Luka puts his name in the box with a **×**, and the **×** empties it again
- [x] Nikola's picker in Office lists only Nikola, Ivana and Luka, not Ana or Sara

## JOB-03 Create jobs A, B and C and read the job screen
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-02 done

Use **+ New Job** in Residential and fill the form from the table. For **Assign to**, type a part of the name and pick the person.

| Job | Title | Client | Deadline | Priority | Milestone | Assign to | Description |
|-----|-------|--------|----------|----------|-----------|-----------|-------------|
| A | Install extractor fan at 5 Linden Walk | Sutherland family | today + 10 days | High | Bathroom phase | Ivana | Needs a `**scaffold tower**` for the roof vent. Ask the client about parking. |
| B | Strip kitchen floor at 31 Oak Ave | Delaney family | none | High | Kitchen phase | nobody | none |
| C | Fix roof flashing at 12 Rowan Close | Nolan family | none | Medium | No milestone | Ana | none |

Then open job A from the list and click **Jobs** in the breadcrumb at the top.

- [x] All three are created and show **New** in the list, and the form closes after each
- [x] A sits under **Bathroom phase** with its client, "Ivana Marković", its deadline date, a **High** badge and a **New** badge
- [x] B sits under **Kitchen phase** with "—" as assignee and "—" as deadline
- [x] C sits under **Ungrouped** with "Ana Petrović" as assignee
- [x] A's screen shows the breadcrumb **Jobs / Install extractor fan at 5 Linden Walk**, the title with **High** and **New** badges, and the fields Client, Assigned to, Deadline and Created (today)
- [x] A's description shows "scaffold tower" in bold, not the asterisks
- [x] Clicking **Jobs** in the breadcrumb returns to the list

## JOB-04 Edit a job and change who it is assigned to
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-03 done

1. Open job A and click **Edit**.
2. Clear **Title** and click **Save changes**.
3. Type the title **Install extractor fan at 5 Linden Walk** back. Change **Client** to "Sutherland household", **Deadline** to today + 14 days, **Priority** to **Critical** and **Milestone** to **Kitchen phase**. Click **Save changes**. Reload the page.
4. Click **Edit**, clear **Deadline** and **Client**, and click **Save changes**.
5. Click **Edit** again, type "Sutherland household" back in **Client** (leave **Deadline** empty), and click **Save changes**.
6. Open job B, click **Edit**, pick Nikola in **Assign to** and click **Save changes**.
7. Click **Edit** on B, click the **×** in **Assign to**, and click **Save changes**.

- [x] **Edit** opens a form titled **Edit Job** with A's title, client, deadline date, **High**, **Bathroom phase** and "Ivana Marković" already filled in
- [x] The empty title shows "Title is required" and nothing is saved
- [x] After saving, A's screen shows "Sutherland household", the new deadline date and a **Critical** badge, and they are still there after the reload
- [x] In the list A now sits under **Kitchen phase**
- [x] With **Deadline** and **Client** cleared, A's screen shows "—" for both
- [x] A's assignee is still "Ivana Marković" after all the edits, and A is left with no deadline so it raises no deadline alert later
- [x] B's screen shows "Nikola Jovanović" as the assignee after step 6
- [x] B's screen shows "—" as the assignee after step 7

## JOB-05 The job list: groups, flat view, tabs
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-04 done, Ana's settings at their defaults

1. Open **Residential Fit-outs**, **Jobs**. Look at the groups and the tab row.
2. Click the **Kitchen phase** header, then click it again.
3. Click the **Grouped** button.
4. Click the row of "Tile bathroom at 14 Elm St".

- [x] The list opens grouped, with the groups **Kitchen phase**, **Bathroom phase**, **Handover** and **Ungrouped**, each with a count
- [x] Each milestone header shows the milestone's deadline and a progress bar with a count such as 0/3
- [x] **Bathroom phase** includes "Tile bathroom at 14 Elm St" and "Order bathroom fittings for 14 Elm St"; **Kitchen phase** includes "Install kitchen worktops at 31 Oak Ave" and A; **Handover** includes "Paint hallway and stairs at 9 Birch Rd" and "Final snag list at 22 Mill Lane"; **Ungrouped** includes "Fix leaking tap at 7 Cedar Close" and C
- [x] Clicking a group header collapses it and clicking again expands it
- [x] The button reads **Grouped** while grouped, and after one click the list is one flat table and the button reads **Flat**
- [x] The flat table includes the columns Title, Client, Assigned to, Deadline, Priority and Status in that order (plus Type once the project has job types), and each title carries its milestone as a small chip
- [x] Clicking a row opens that job's screen
- [x] The tab row shows **All**, **New**, **In Progress**, **Blocked** and **Completed**, each with a count, and the **All** count equals the four others added up
- [x] A tab with a count of 0 is dimmed

## JOB-06 Sorting the flat list
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-05 done, list in the flat view, tab **All**

1. Look at the table headers and the first rows.
2. Click **Deadline** once, then again.
3. Click **Priority**.
4. Click **Title**, then click it again.

- [x] The list starts sorted by **Deadline** with the ↑ arrow, earliest first
- [x] Jobs without a deadline sit at the bottom of the list
- [x] Clicking **Deadline** again shows ↓ with the latest deadline first, and jobs without a deadline are still at the bottom
- [x] Clicking **Priority** puts the **Critical** job (A) first and **Low** jobs last
- [x] Clicking **Title** sorts A to Z ignoring capitals, and a second click sorts Z to A

## JOB-07 Search
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-06 done. Results appear about a third of a second after you stop typing.

1. Type `elm st` (lower case) in the search box.
2. Replace it with `Okafor`.
3. Replace it with `Ivana`.
4. Replace it with `scaffold`, a word that is only in A's description.
5. Replace it with `zzzqq`.
6. Replace it with `Elm` and click the **New** tab. Clear the search box and click **All**.

- [x] `elm st` finds "Tile bathroom at 14 Elm St" and "Order bathroom fittings for 14 Elm St" although the case differs
- [x] `Okafor` finds the Okafor family jobs (by client) and every other job disappears
- [x] `Ivana` finds the jobs assigned to Ivana, including A and "Paint hallway and stairs at 9 Birch Rd", and not "Tile bathroom at 14 Elm St"
- [x] `scaffold` finds A, because the search also reads the description
- [x] The search box placeholder names everything the search reads (known gap: it says "Search by title, client, or assignee…" and leaves out the description, so this is expected to fail, UX job to be linked)
- [x] `zzzqq` shows `No results for "zzzqq".`
- [x] With `Elm` typed, the tab counts shrink to the matching jobs and the **New** tab lists only matching New jobs
- [x] Clearing the box brings back the full list

## JOB-08 Priority and milestone filters
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-07 done, search box empty, tab **All**, no other Critical job in Residential

1. Open the priority box next to the search box and look at its options.
2. Choose **Critical**, then **Low**, then **All priorities**.
3. In the grouped view, look for a milestone box. Click **Grouped** to go flat and look again.
4. Choose **Kitchen phase** in the milestone box.
5. Choose **Critical** in the priority box, then **Handover** in the milestone box.
6. Choose **All milestones** and **All priorities**.

- [x] The priority box offers **All priorities**, **Critical**, **High**, **Medium** and **Low**
- [x] **Critical** shows A, and every row shown has a **Critical** badge
- [x] **Low** shows only rows with a **Low** badge
- [x] The grouped view has no milestone box, and the flat view has one with **All milestones**, **Kitchen phase**, **Bathroom phase** and **Handover**
- [x] **Kitchen phase** shows only rows with the **Kitchen phase** chip, including A, and the **Grouped**/**Flat** button disappears while the filter is on
- [x] **Critical** with **Handover** shows "No jobs match this filter."
- [x] After choosing **All milestones**, the **Grouped**/**Flat** button is back

## JOB-09 Everyone and Mine
**As:** Ana (owner, Residential Fit-outs), then Ivana (member) · **Day:** any, after ONB-33 · **Needs:** JOB-08 done, filters reset

1. As Ana, open the list and look at the **Everyone** / **Mine** switch next to the tabs.
2. Click **Mine**, then click through the tabs.
3. Reload the page.
4. Open **Account settings**, **Jobs**, set **Default assignee filter** to **Mine**, and open **Residential Fit-outs**, **Jobs**.
5. Click **Everyone** on the list, then go back to **Account settings**.
6. Set **Default assignee filter** back to **Everyone**.
7. As Ivana, open Residential **Jobs**, click **Mine**, then **Everyone**.

- [x] The switch has the buttons **Everyone** and **Mine**, and **Everyone** is the selected one on a fresh load
- [x] **Mine** shows only jobs with Ana as the assigned person, including C, and the tab counts follow that shorter list
- [x] After the reload the switch is back on **Everyone**, because the switch isn't remembered
- [x] With the setting on **Mine**, the list opens on **Mine** without a click
- [x] Clicking **Everyone** on the list leaves the setting on **Mine**
- [x] Ivana sees the same jobs under **Mine** and **Everyone**, because a member only sees her own

## JOB-10 Due-soon and overdue deadlines
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-09 done, Due-soon highlight at 3 days. The jobs are created here with dates counted from today, so the check works on any day.

1. Click **+ New Job** in Residential and create four jobs, each with **Assign to** Ana, no milestone and a client "Whitlock family". Use the **Title**, **Deadline** and **Priority** below, then **Create job**.

   | Job | Title | Deadline | Priority |
   |-----|-------|----------|----------|
   | F | Collect keys for 8 Pear Tree Rd | yesterday | Low |
   | G | Check scaffolding at 17 Cherry Hill | today + 2 days | Medium |
   | H | Order skirting boards for 17 Cherry Hill | today + 3 days | Medium |
   | K | Book skip for 17 Cherry Hill | today + 8 days | Low |

2. In the flat list, look at the Deadline column for F, G, H and K.
3. Open **Account settings**, **Jobs**, **Due-soon highlight**, choose **1 day**, and look at the list again.
4. Choose **Off** and look at the list.
5. Choose **3 days**, open G and look at its **Deadline**.

- [ ] G and H show their deadline in amber with a small **Due soon** label
- [ ] K shows its deadline in the normal colour and no label
- [ ] F shows its deadline in red and no **Due soon** label
- [ ] With **1 day**, G and H lose the amber colour and the label, and F stays red
- [ ] With **Off**, no row has a **Due soon** label, and F stays red
- [ ] G's job screen shows the amber deadline with **Due soon** beside it

## JOB-11 Relative deadlines, row density and completing clears the highlight
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-10 done, F still New and overdue, G, H and K still open

1. Open **Account settings**, **Jobs**, and set **Deadline format** to **Relative**. Open the Residential list.
2. Set **Row density** (under **Appearance**) to **Compact** and open the list again.
3. Set **Row density** back to **Comfortable**.
4. Open F, click **Start**, click **Complete**, confirm **Mark Complete**. Go back to the list and look at F.
5. Set **Deadline format** back to **Absolute**.
6. Open G, H and K in turn, and on each click **⋮**, **Delete Job**, **Delete**. Look at the list.

- [ ] G reads "in 2 days", H reads "in 3 days" and F reads "1 day overdue"
- [ ] With **Compact**, the table rows are visibly tighter and the text smaller
- [ ] With **Comfortable**, the rows are back to the roomier spacing
- [ ] Once F is Completed, its deadline is no longer red and shows a date, not "days overdue"
- [ ] With **Absolute**, deadlines show dates again
- [ ] G, H and K are gone from the list, so they raise no deadline alert

## JOB-12 An owner moves a job through the statuses
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-04 done, B unassigned and New. Ana is not the assignee: owners and admins may change any job.

1. Open job B. Look at the action bar under the details.
2. Click **Start**.
3. Click **Complete**, then **Cancel** in the box.
4. Click **Complete** again and confirm **Mark Complete**.
5. Look at the action bar, then click **Reopen**.
6. Open **Status history** if it is closed.

- [ ] On a New job the bar offers **Start** and no **Complete** or **Block**
- [ ] **Start** makes B **In Progress** at once, with no question first
- [ ] On an In Progress job the bar offers **Complete** and **Block**
- [ ] **Complete** asks "Mark this job as complete? This cannot be undone by the assigned member." and **Cancel** leaves B In Progress
- [ ] **Mark Complete** makes B **Completed** at once, although Residential's approval gate is on, because an owner completes directly
- [ ] On a Completed job the bar offers **Reopen** and no **Start**, **Complete** or **Block**
- [ ] **Reopen** makes B **In Progress** at once, with no question first
- [ ] **Status history** shows a count of 4: created, started, completed, reopened

## JOB-13 Block a job with a reason, and unblock it
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-12 done (B In Progress), C New. Note the **Blocked** tab count before you start.

1. Open job C, click **Start**, then click **Block**. Look at the box without typing.
2. Type three spaces.
3. Clear the box, click it, and look at the drop-down. Type `mat`.
4. Pick **Waiting on client** and click **Block Job**.
5. Look at C's screen, then open the list. Click the **Status** header in the flat list.
6. Open C, click **Unblock**, and confirm.
7. Click **Block**, type **Waiting on scaffold hire**, and click **Block Job**.
8. Open job B, click **Block**, and look at the drop-down. Click **Cancel**.

- [ ] The **Block Job** button is greyed out while the box is empty and while it holds only spaces
- [ ] The drop-down lists **Waiting on client**, **Materials not delivered** and **Waiting on inspection**, and `mat` narrows it to **Materials not delivered**
- [ ] C becomes **Blocked** and shows a red banner "Blocked since" with today's date and "Waiting on client" in quotes
- [ ] On a Blocked job the bar offers **Unblock** and no **Complete** or **Start**
- [ ] In the list C's row has a red edge and the **Blocked** tab count is one higher than before
- [ ] The **Status** header puts Blocked jobs first
- [ ] **Status history** on C shows the block with "Reason: Waiting on client"
- [ ] **Unblock** asks "Unblock this job and resume work?", and after confirming C is **In Progress** with no banner
- [ ] The typed reason is accepted: C is Blocked again and the banner shows "Waiting on scaffold hire" in quotes
- [ ] B's Block box now also offers **Waiting on scaffold hire**, because typed reasons are remembered

## JOB-14 Manage block reasons in the project settings
**As:** Ana (owner), then Nikola (admin), then Ivana (member), all in Residential Fit-outs · **Day:** any, after ONB-33 · **Needs:** JOB-13 done, C Blocked with "Waiting on scaffold hire"

1. As Ana, open **Residential Fit-outs**, **Settings**, and find **Block Reasons**.
2. Click **Remove** next to **Waiting on scaffold hire**.
3. Open job C. Open job B, click **Block**, and look at the drop-down. Click **Cancel**.
4. On C, click **Unblock**, confirm, click **Block**, type **Waiting on scaffold hire**, and click **Block Job**. Open the settings again.
5. On C, click **Unblock** and confirm.
6. As Nikola, open Residential **Settings**.
7. As Ivana, open Residential **Settings**.

- [ ] **Block Reasons** lists **Waiting on client**, **Materials not delivered**, **Waiting on inspection** and **Waiting on scaffold hire**, each with **Remove**
- [ ] **Remove** takes the reason off the list at once, without asking first
- [ ] C, still Blocked, keeps its banner "Waiting on scaffold hire"
- [ ] B's Block box no longer offers **Waiting on scaffold hire**
- [ ] Blocking C with the same text again puts **Waiting on scaffold hire** back on the settings list, once
- [ ] Nikola, an admin, sees **Block Reasons** with **Remove**
- [ ] Ivana, a member, sees no **Block Reasons** section

## JOB-15 A member changes the status of her own job, and an admin of someone else's
**As:** Ivana (member, Residential), then Nikola (admin, Residential), then Ana · **Day:** any, after ONB-33 · **Needs:** JOB-04 done, A New and assigned to Ivana, Residential's approval gate on

1. As Ivana, open job A. Look at the action bar.
2. Click **Start**.
3. Click **Block**, type **Materials not delivered**, click **Block Job**, then **Unblock** and confirm.
4. Look at the action bar. Click the button that replaces **Complete**, then **Cancel**.
5. As Nikola, open job A and click **Complete**, then confirm **Mark Complete**.
6. As Ivana, reload job A and look at the action bar.
7. As Ana, open A and click **Reopen**. As Ivana, reload.

- [ ] Ivana's bar on New job A offers **Start** and no **Complete** or **Block**
- [ ] **Start** makes A **In Progress**
- [ ] **Block** with a typed reason, and **Unblock**, both work for the assigned member
- [ ] On the In Progress job Ivana's bar says **Request completion** instead of **Complete**, and **Cancel** on its box leaves A In Progress (the approval flow is in `06-approvals.md`)
- [ ] Nikola, an admin who isn't the assignee, completes A directly: it shows **Completed** with no awaiting-approval badge
- [ ] After the reload Ivana sees A **Completed** and an action bar with no buttons
- [ ] Ana's **Reopen** makes A **In Progress** again, and Ivana sees **Block** again after her reload

## JOB-16 What a member can't see, edit or delete
**As:** Ivana (member, Residential; admin, Office), then Sara (member, Residential) · **Day:** any, after ONB-33 · **Needs:** JOB-15 done

1. As Ivana, open Residential **Jobs**, then job A.
2. As Ana, copy the address of job A.
3. As Sara, paste it into the address bar, then open Residential **Jobs**.
4. As Ivana, open Office Refurbishments **Jobs** and open "Replace ceiling lights in the open-plan office".

- [ ] Ivana's Residential list shows her own jobs, including A and "Paint hallway and stairs at 9 Birch Rd", and not "Tile bathroom at 14 Elm St" or "Install kitchen worktops at 31 Oak Ave"
- [ ] A's screen for Ivana has no **Edit** button and no **⋮** menu, so no **Delete Job**
- [ ] Sara pasting A's address sees "Failed to load job." and a red message "Access denied: you are not assigned to this job"
- [ ] Sara's Residential list doesn't include A
- [ ] In Office Ivana is an admin: she sees every Office job, including Luka's, and the job has **Edit** and **⋮**

## JOB-17 Office Refurbishments: no approval gate, and Luka in Serbian
**As:** Nikola (owner, Office), then Luka (member, Serbian UI), then Ivana (admin, Office) · **Day:** any, after ONB-33 · **Needs:** ONB-33 and ONB-23 done (Office's gate is off). Don't run it while PRJ-11 in `03-projects.md` has Office's gate on: Luka would then get **Request completion** instead of completing

1. As Nikola, open Office **Jobs**, **+ New Job**. Title **Install fire door at Marlow Accounting**, Client "Marlow Accounting", Deadline today + 3 days, Priority **High**, Milestone **Fit-out**, **Assign to** Luka. Click **Create job**. This is job D.
2. As Luka, open Office **Jobs**, then open job D.
3. Click the first button on the action bar, which starts the job.
4. Click the first button on the bar again, which completes the job, and confirm.
5. As Ivana, open job D and click **Reopen**.
6. As Luka, reload job D.

- [ ] D is created as **New** with "Luka Ilić" as the assignee
- [ ] Luka's Office list includes D and no job assigned to anyone else
- [ ] Luka's list, tabs, column headings and status badges are in Serbian
- [ ] Luka's job screen, buttons and boxes are in Serbian
- [ ] Starting D makes it **In Progress** (the status badge changes)
- [ ] Completing D asks for a confirmation and then completes it directly, with no awaiting-approval badge, because Office's gate is off
- [ ] Luka's action bar on the Completed D has no buttons
- [ ] Ivana's **Reopen** makes D **In Progress**, and Luka sees the action buttons again after his reload

## JOB-18 Delete a job
**As:** Ana (owner), then Nikola (admin), both in Residential Fit-outs · **Day:** any, after ONB-33 · **Needs:** JOB-14 and JOB-11 done: B In Progress, F Completed

1. As Ana, open job B, click **⋮**, then **Delete Job**. Look at the box and click **Cancel**.
2. Click **⋮**, **Delete Job**, and **Delete**.
3. In the list, type `Strip kitchen` in the search box. Clear it.
4. Click the browser's **Back** button until B's old address is on screen.
5. As Nikola, open job F, click **⋮**, **Delete Job**, **Delete**.
6. As Ana, reload the list.

- [ ] **⋮** offers **Delete Job**
- [ ] The box reads `Delete "Strip kitchen floor at 31 Oak Ave"? This cannot be undone.` and **Cancel** leaves B in place
- [ ] **Delete** returns to the list, where B is gone
- [ ] Searching for `Strip kitchen` finds nothing
- [ ] B's old address shows "Failed to load job." and no red message
- [ ] Nikola, an admin, can delete the Completed job F
- [ ] F no longer shows in Ana's list

## JOB-19 Addresses that don't match
**As:** Nikola, then Ana · **Day:** any, after ONB-33 · **Needs:** JOB-18 done. Ana isn't a member of Office Refurbishments.

1. As Nikola, copy the address of the Office **Jobs** list.
2. As Ana, paste it into the address bar.
3. As Nikola, open a Residential job, copy its address, and replace the project part of it with the project part of the Office address.
4. As Ana, open any Residential job, and in its address change the job number to one far beyond the last one, such as 999.

- [ ] Ana opening Office's list sees "Failed to load jobs." and a red message "You are not a member of this project"
- [ ] A Residential job under Office's address shows "Failed to load job." and no red message
- [ ] A job number that doesn't exist shows "Failed to load job." and no red message

## JOB-20 Add, edit and remove job links as an owner
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** JOB-14 done (job C exists), the Job links add-on bought (ONB-08)

1. Open job C and click **Links** (closed because the job has none). Click **+ Add link**.
2. Look at **Save** with the URL empty. Type `not a url` and click **Save**.
3. Replace it with `javascript:alert(1)` and click **Save**.
4. Replace it with `https://github.com/brightline/specs/pull/12`. Look at the label box, then click **Save**.
5. Click **+ Add link**. URL `https://www.example.com/tile-quote.pdf`, label "Tile supplier quote". **Save**.
6. Click **+ Add link**. URL `https://example.com/site-photos`, no label. **Save**.
7. Click the copy icon (two squares) on the first link, then paste into the address bar without pressing Enter.
8. Click the pencil icon on the "Tile supplier quote" link, change the label to "Tile quote (final)", click **Save**.
9. Click the **×** on the "site-photos" link, click **Cancel**, click **×** again, then **Delete**.
10. Click the first link's label. Reload the page.

- [ ] **Save** is greyed out while the URL box is empty
- [ ] `not a url` is refused with a red message "URL is invalid or uses a disallowed scheme" and no link is added
- [ ] `javascript:alert(1)` is refused with the same message
- [ ] The GitHub address fills the label box with "GitHub", and the saved link shows "GitHub" with "Added by Ana Petrović"
- [ ] The second link shows the label "Tile supplier quote", and the third, with no label, shows its URL as text
- [ ] The copy icon changes to a tick for a moment, and the pasted text is the link's URL
- [ ] After **Save** the second link reads "Tile quote (final)"
- [ ] The delete box reads `Remove "https://example.com/site-photos"?`, **Cancel** keeps the link and **Delete** removes it
- [ ] Clicking a link's label opens its URL in a new tab
- [ ] After the reload the two remaining links are still there

## JOB-21 A member can add a link but not edit or remove one
**As:** Ana (owner), then Ivana (member), then Ana · **Day:** any, after ONB-33 · **Needs:** JOB-15 done (A is In Progress, assigned to Ivana)

1. As Ana, open job A, open **Links**, click **+ Add link**, type `https://www.figma.com/file/ext-fan-layout` and click **Save**.
2. As Ivana, open job A and look at **Links**.
3. Click **+ Add link** under the list. URL `https://example.com/fan-datasheet`, label "Fan datasheet". **Save**.
4. As Ana, reload job A.

- [ ] Ana's link is saved with the label "Figma"
- [ ] Ivana sees Ana's link with the copy icon, and with no pencil icon and no **×**
- [ ] Ivana's new link is saved and shows "Added by Ivana Marković"
- [ ] Ivana's own link has no pencil icon and no **×** either
- [ ] Ana sees both links, each with the copy icon, a pencil icon and a **×**

## JOB-22 A job in an org without add-ons
**As:** Petra (owner, Pine & Pixel Studio) · **Day:** any, after ONB-33 · **Needs:** ONB-31 done. Pine & Pixel has no add-ons.

1. Open **Brand refresh for Juniper Cafe**, **Jobs**. Look at the list tools.
2. Click **+ New Job**. Look at the form, click **Assign to**. Title **Draft logo options for Juniper Cafe**, Client "Juniper Cafe", Priority **High**, no deadline. **Create job**. This is job P.
3. Open job P and look below the action bar.
4. Click **Start**, then **Block**. Look at the drop-down, type **Waiting on client logo files**, click **Block Job**.
5. Click **Unblock** and confirm. Click **Complete** and confirm **Mark Complete**. Click **Reopen**.
6. Click **Upgrade in org settings**.

- [ ] The list has the search box, the priority box, the status tabs and **Everyone** / **Mine**, and no **Grouped** / **Flat** button and no milestone box
- [ ] The form has no **Milestone** or **Type** box, and **Assign to** lists Petra Stanković only
- [ ] Below the action bar there are no Relationships, Notes, Approvals, Links or Status history sections
- [ ] One locked row below the bar names the sections "Notes · Approvals · Job History · Relationships · Links" with **Upgrade in org settings**
- [ ] The locked row and the section it hides share a name (known gap: the row says "Job History" and the section itself, in an org that has the add-on, is titled "Status history", so this is expected to fail, UX job to be linked)
- [ ] The action bar has no **Request Approval** button
- [ ] **Block** offers no suggestions, because the project has no block reasons, and the typed reason blocks the job
- [ ] **Complete** completes P directly with no approval request, and **Reopen** makes it **In Progress**
- [ ] **Upgrade in org settings** opens **Organisation Settings**

## JOB-23 Relative deadlines in the Serbian UI
**As:** Luka (member, Serbian UI), then Nikola (owner, Office) · **Day:** any, after ONB-33 · **Needs:** JOB-17 done (D is due in 3 days and assigned to Luka), Luka's Deadline format on Absolute

1. As Luka, open **Account settings**, **Jobs**, and set **Deadline format** to its second option (Relative).
2. Open Office **Jobs** and look at the deadline of D, and at the small label beside it.
3. Open D and look at its deadline.
4. Set the Deadline format back to its first option (Absolute).
5. As Nikola, open D, click **⋮**, **Delete Job**, **Delete**. As Luka, reload the Office list.

- [ ] The list shows D's deadline as a Serbian relative time, not English words such as "in 3 days" (known bug: `useFormatDeadline.formatRelative` has English text hard-coded, so this is expected to fail, job to be linked)
- [ ] The job screen shows the deadline the same way
- [ ] The small label beside D's deadline is in Serbian ("Rok uskoro")
- [ ] D is gone from Luka's list, so it raises no deadline alert

## Not manual
- Every illegal status transition (New to Completed, New to Blocked, Blocked to Completed, Completed to New, and so on) and the 400 "Invalid transition": covered by `JobStatusTransitionerTest` ("validate rejects every illegal transition with 400, even for an Owner") and, through the API, by `JobIntegrationTest` (New to Completed, Blocked to Completed, Completed to New). The screen never offers them, so JOB-12, JOB-13 and JOB-15 check the buttons per status instead.
- The block reason being required by the API when the box is empty: covered by `JobStatusTransitionerTest` ("transition to BLOCKED without a reason fails with 400 and changes nothing"). The screen greys out the button (JOB-13).
- A member or a non-assignee calling the API directly to edit, delete, change the status of or reopen a job: covered by `JobStatusTransitionerTest` (the Member-reopen and unassigned-Member cases) and by `JobIntegrationTest` ("MEMBER should be forbidden from updating job fields", "…from deleting a job", "…from changing status of a job not assigned to them", "…from reopening a completed job"). JOB-15 and JOB-16 check what the screens show.
- Assigning a job to a user from another organisation, to a milestone or a type that doesn't exist, or to a type of another project: covered by `JobIntegrationTest` ("assignedTo user belongs to another organisation", on create and on update, JOB-393; "milestoneId does not exist on create"; "typeId belongs to a different project", on create and on update). The picker can't offer them (JOB-02).
- Creating, editing or changing the status of a job in an archived project (409): covered by `JobIntegrationTest` (`createJob_shouldReturn409_whenProjectIsCompleted`, `updateJob_shouldReturn409_whenProjectIsCompleted`, `updateJobStatus_shouldReturn409_whenProjectIsCompleted`). The archived project on screen is in `03-projects.md`.
- The type filter on the list: covered by `JobIntegrationTest` (`listJobs_shouldReturnOnlyJobsOfType_whenTypeIdFilter`). Creating job types is in `07-planning.md`.
- Link rules the screen can't reach: a member updating or deleting a link (403), a link on a job of another project (404), and the link endpoints without the Job links add-on: covered by `JobLinkIntegrationTest` and `RequiresAddonAspectTest` ("Should throw ForbiddenException when add-on is not active"). Pine & Pixel shows no Links section at all (JOB-22).
- Block reason list and delete through the API (403 for a non-member or a member, 404 for an unknown reason, restoring a deleted reason by blocking with the same text): covered by `BlockReasonIntegrationTest`. JOB-13 and JOB-14 check the screens.
- Listing jobs through the API for a member: covered by `JobIntegrationTest` ("MEMBER should see only jobs assigned to them"); JOB-16 checks it on screen.
- Notifications for assigning, changing a deadline or priority, blocking and completing: covered by `JobServiceNotificationEventsTest` and checked on screen in `09-notifications.md`.

## Findings
