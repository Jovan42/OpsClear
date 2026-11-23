# Notes, status history and relationships

Last run: 2026-11-23, Jovan
Guide: /guide/notes-links-history, /guide/job-relationships

This file covers the three add-on sections of the job page: **Notes** (immutable, markdown),
**Status history** (the shown name is "Job History" in the locked row) and **Relationships**
(Blocked by, Related to, Duplicates), plus their add-on gating and who can do what. It uses the
world built by `01-onboarding.md` (see the [README](README.md#test-world)): Brightline Renovations
with Ana, Nikola, Ivana, Luka and Sara, the projects Residential Fit-outs (gate on, all sections
shown) and Office Refurbishments (Relationships, Links and Job status history hidden), and Petra in
Pine & Pixel Studio, which has no add-ons. Run `01-onboarding.md` first. Rules in
[ADR-0009](../dev/decisions/0009-notes-model.md),
[ADR-0022](../dev/decisions/0022-job-relationships.md) and
[ADR-0028](../dev/decisions/0028-job-status-change-history.md).

**What the screens do** (so the checks below make sense):

- **Notes** open by themselves when the job already has notes, and are collapsed on a job with none.
  Every project member who can open the job can add a note. A note can't be edited or deleted by
  anyone. The "Notes cannot be edited or deleted. Add anyway?" question appears for the first note
  in a browser tab session only.
- **Status history** lists every status change oldest first: from, to, who, when, and the block
  reason when the move was to Blocked. Editing a job's other fields adds nothing.
- **Relationships** link two jobs of the same project. The screen shows **+ Add** and **×** to
  project Owners and Admins only. The API is looser: ADR-0022 says any project member can add or
  remove relationships, and `JobRelationshipService` only checks membership, so only the UI hides
  the buttons from Members. A **Blocked by** link is a record: it stops nothing and changes no status, but when the
  blocking job is completed the waiting job's assignee gets **Blocker cleared**.
- A Member sees only the jobs assigned to them, so the pages below are played by the assignee.
- **Sections open by themselves** when they hold something: Notes when the job has notes, Status
  history always (every job has a "Created as" entry), Relationships when the job has links. A step
  that says "find" a section means: open it if it is collapsed, leave it if it is open.

**Jobs this file creates.** `NHR-01` creates five jobs in Residential Fit-outs for client Harlow
family, none with a deadline, so no deadline alert is ever sent for them. Other scenarios call them
by title. Leave them in the state this file ends with (A In Progress, B Completed, C In Progress,
D New) and don't use them in other areas; `04-jobs.md`, `08-schedules.md` and `09-notifications.md`
create their own. Don't change the day-1 jobs of `01-onboarding.md` or the `NTF-02` jobs here.

| Short name | Title |
|------------|-------|
| A | Replace kitchen window at 18 Maple Row |
| B | Order window glazing for 18 Maple Row |
| C | Remove old window frame at 18 Maple Row |
| D | Replace kitchen window at 18 Maple Road |
| E | Order window glazing for 18 Maple Row (entered twice) |

**Notifications this file sends** (checked in `NHR-24`; `09-notifications.md` day 7+ reads every
entry in each centre, so expect these, about the Maple Row and Maple Road jobs):

| Person | Entries |
|--------|---------|
| Ana | 2 **Note added** (Luka, Nikola), 3 **Status changed** (A started, C started, B started), 1 **Job blocked** (A) |
| Nikola | 1 **Job assigned** (D), 7 **Status changed** (A started, A unblocked, C started, B started, B completed, A completed, A reopened), 1 **Job blocked** (A) |
| Luka | 1 **Job assigned** (A), 3 **Note added** (Ana twice, Nikola), 3 **Status changed** (A unblocked, A completed, A reopened), 1 **Blocker cleared** (A) |
| Ivana | 1 **Job assigned** (B), 1 **Status changed** (B completed) |
| Sara | 1 **Job assigned** (C), 1 **Blocker cleared** (C) |
| Petra | none |

**Luka's language.** From ONB-33 Luka uses the Serbian UI. Where Luka reads a heading, label or
notification, the quoted English text is the English wording: read the Serbian equivalent and tick
when it means the same. Checks of exact English wording are played as Ana, Nikola, Ivana or Sara.
`NHR-20` judges the Serbian text itself.

**Setup.** One browser profile per persona, as in `01-onboarding.md`. For `NHR-02` open a **new
tab** for Ana, because the confirmation about notes is remembered per tab session. Day: every
scenario here runs on any day after ONB-33, nothing depends on the calendar.

## NHR-01 Create the working jobs
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** `01-onboarding.md` done

In Residential Fit-outs, **Jobs**, **+ New Job**, create these jobs. Client "Harlow family",
**Deadline** empty, a one-line description such as "Same grey frame as the front door".

| Title | Priority | Milestone | Assign to |
|-------|----------|-----------|-----------|
| Replace kitchen window at 18 Maple Row | Medium | Kitchen phase | Luka |
| Order window glazing for 18 Maple Row | Medium | Kitchen phase | Ivana |
| Remove old window frame at 18 Maple Row | Low | Kitchen phase | Sara |
| Replace kitchen window at 18 Maple Road | Low | Handover | Nikola |
| Order window glazing for 18 Maple Row (entered twice) | Low | Handover | (leave empty) |

- [x] All five jobs are created as **New**, each with a friendly ID
- [x] None of them shows a deadline
- [x] Luka, Ivana, Sara and Nikola each get one "Assigned you to …" notification, for A, B, C and D
- [x] Ana gets none, and nobody gets one for E, which has no assignee

## NHR-02 The first note
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** NHR-01 done, Ana in a new tab

1. Open A. Find **Notes** (it is collapsed, because A has no notes yet) and click it to open it.
2. Look at the box and the button under it. Type three spaces.
3. Select the spaces, delete them, then type "Measured the opening on site: 1200 x 1050 mm. Client wants the same grey frame as the front door." Look under the box.
4. Click **Add Note**. In the question, click **Cancel**.
5. Click **Add Note** again and confirm.

- [x] The section says "No notes yet." and the **Add Note** button is greyed out while the box is empty
- [x] **Add Note** stays greyed out while the box holds only spaces
- [x] A counter under the box shows the number of characters out of 10000 and goes up as you type
- [x] **Add Note** asks "Notes cannot be edited or deleted. Add anyway?"
- [x] **Cancel** adds no note and keeps the text in the box
- [x] After confirming, the note appears with "Ana Petrović" and the date and time, and the box is empty again and the counter is back to 0/10000
- [x] The note has no edit, delete or other change control
- [x] Luka gets one "Added a note on …" notification from Ana

## NHR-03 The assignee replies
**As:** Luka (member, assignee of A) · **Day:** any, after ONB-33 · **Needs:** NHR-02 done. Luka opens A in a new tab.

1. Look at the bell, then open A.
2. Look at the **Notes** section.
3. Add the note "Frame ordered, the supplier says 5 working days." and confirm the question.
4. As Ana, open the bell.

- [x] Luka's bell had the "Added a note on …" entry from Ana before he opened the job
- [x] The **Notes** section is already open, and Ana's note shows with her name and its time
- [x] Luka's note appears under Ana's with his name, so the order is oldest first
- [x] The confirmation question appeared for Luka's first note in this tab
- [x] Ana gets one "Added a note on …" notification from Luka, because she wrote a note on the job
- [x] Luka gets nothing for his own note

## NHR-04 Markdown in notes
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** NHR-03 done, Ana still in the tab of NHR-02

1. Open A and find its **Notes** (open them if collapsed). In the box click the bold button of the toolbar and type "Window spec" over the placeholder.
2. Start a new line, click the unordered list button and type over the "List item" placeholder with "Frame: grey aluminium, double glazed". On the next line, click it again and type over the placeholder with "Opening: 1200 x 1050 mm".
3. On a new line type `Catalogue: [Glazing supplier](https://example.com/glazing)`. On another line type `<script>alert('x')</script>` and on the next `<b>raw html</b>`.
4. Click **Preview**, then **Write**.
5. Click **Add Note**.
6. Click the **Glazing supplier** link.

- [x] **Preview** shows "Window spec" in bold, a bullet list with the two lines and the link as a link
- [x] **Write** shows the text again as typed, with the markdown marks
- [x] **Add Note** adds the note at once, without the confirmation question
- [x] The saved note shows the same bold text, the bullet list and the link
- [x] The link opens in a new browser tab
- [x] No alert pops up, and `raw html` is not shown in bold
- [x] Luka gets a second "Added a note on …" notification from Ana

## NHR-05 Who can read and add notes
**As:** Nikola (Residential admin), then Sara (member, not assigned to A) · **Day:** any, after ONB-33 · **Needs:** NHR-04 done

1. As Nikola open A and find its **Notes** (open them if collapsed). Add the note "Glazing supplier confirmed delivery for next Tuesday." and confirm.
2. Look at Luka's and Ana's bells.
3. As Sara, copy the address of A from Nikola's browser bar and open it.

- [x] Nikola sees the notes of A in order: Ana, Luka, Ana, with the markdown note formatted, and after step 1 a fourth by Nikola
- [x] Nikola can add a note although he isn't the assignee, and sees no edit or delete control on any note, including his own
- [x] Luka gets a third "Added a note on …" notification from Nikola, and Ana gets a second one from Nikola
- [x] Nikola gets none for his own note
- [x] Sara sees the "Failed to load job." page, not the job
- [x] A toast tells Sara "Access denied: you are not assigned to this job"
- [x] Sara's and Ivana's bells have no entry about A or its notes

## NHR-06 The history starts with the creation
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** NHR-01 done, A still **New**

1. Open B. Find **Status history** (open it if it's collapsed; it normally opens by itself).
2. Open A and find its **Status history** the same way.

- [x] B's **Status history** has one entry, "Created as" with a **New** badge
- [x] The entry says "Ana Petrović" and a date and time
- [x] The section header shows a count of 1
- [x] There is no line about how long the job stayed in a status, because the entry is the latest one
- [x] A shows the same: one "Created as New" entry by Ana

## NHR-07 Status changes are recorded with who, when and why
**As:** Luka (assignee of A), then Ana · **Day:** any, after ONB-33 · **Needs:** NHR-06 done, A **New**, Luka's and Ana's bells open on another page

1. As Luka open A and click **Start**.
2. As Luka click **Block**, pick **Materials not delivered** from the list and confirm.
3. As Ana open A and find **Status history** (open it if it's collapsed).
4. As Ana click **Unblock** and confirm. Check **Status history** again (reload if it doesn't show the new entry).

- [x] After step 3 the history has three entries in order: Created as New, **New** → **In Progress** by "Luka Ilić", **In Progress** → **Blocked** by "Luka Ilić"
- [x] The Blocked entry shows "Reason: Materials not delivered" in red, and no other entry shows a reason
- [x] After step 4 a fourth entry **Blocked** → **In Progress** by "Ana Petrović" appears, without a reason, and the count shows 4
- [x] Every entry shows the person and a date and time
- [x] Between entries a line gives the time the job stayed in the earlier status, for example "0 minutes in …" when the moves were seconds apart
- [ ] The status in that line is written like the badge ("In Progress"), not as a code such as IN_PROGRESS (known bug: `StatusHistory.tsx` passes the raw `entry.changedTo`, so it reads "… in IN_PROGRESS", expected to fail, Bug job to be linked)
- [x] Ana and Nikola each get **Status changed** for the start, **Job blocked** for the block, and Luka and Nikola get **Status changed** for the unblock

## NHR-08 Only status changes are in the history
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** NHR-07 done

1. Open A. Click **Edit**, change **Client** to "Harlow family, 18 Maple Row" and save.
2. Open **Status history**.

- [x] The new client shows on the job
- [x] The history still has the same four entries
- [x] None of the notes of A (or their authors) appears in the history
- [x] Nobody gets a notification for the client change

## NHR-09 The Relationships section and the Add Relationship window
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** NHR-01 done

1. Open C. Look at the **Relationships** header.
2. Click **Relationships** to open it (C has no links, so it starts collapsed).
3. Click **+ Add**. Look at the window.
4. Type "carpet in reception" in **Job**. Clear it, type "Maple".
5. Click **Cancel**.

- [x] The header shows no count and no **+ Add**, and the section opens on "No relationships yet." with a **+ Add** button
- [x] The window is titled **Add Relationship** with three choices: **Blocked by**, **Related to** and **Duplicates**, each with a short description, and **Related to** is preselected
- [x] "carpet in reception" shows "No jobs found.", so the Office job "Strip old carpet in reception" can't be linked to a Residential job
- [x] "Maple" lists A, B, D and E but not C itself
- [x] **Add** is greyed out until a job is chosen
- [x] **Cancel** closes the window and adds nothing

## NHR-10 Blocked by
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** NHR-07 done (A **In Progress**), B **New**

1. Open A. Click **Relationships** to open it (A has no links yet), then **+ Add**, and choose **Blocked by**.
2. Type "glazing" in **Job**. Click "Order window glazing for 18 Maple Row" (not the "entered twice" one) and click **Add**.
3. Look at A. Click the title of B in the list.
4. Look at B's **Relationships**.

- [x] A's list shows a red **Blocked by** label, the title of B as a link and a **New** badge, and the header count shows 1
- [x] A's own status is unchanged, still **In Progress**, with no **Blocked** banner
- [x] The title link opens B
- [x] B shows **Blocks** with the title of A and an **In Progress** badge
- [x] Nobody gets a notification for adding the link

## NHR-11 A Blocked by link stops nothing
**As:** Ana (Residential owner), then Sara (member, assignee of C) · **Day:** any, after ONB-33 · **Needs:** NHR-10 done, C **New**

1. As Ana open C, click **Relationships** to open it (no links yet), then **+ Add**, and choose **Blocked by**, search "glazing", pick B (not E) and add it.
2. As Sara open C and click **Start**.

- [x] C lists **Blocked by** with the title of B and a **New** badge
- [x] Sara can click **Start** and C moves to **In Progress** although B isn't done
- [x] Ana and Nikola get **Status changed** for it
- [x] B now lists **Blocks** twice, for A and for C

## NHR-12 Related to
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** NHR-11 done

1. Open A, click **+ Add**, leave **Related to** chosen, search "Remove old", pick C and click **Add**.
2. Open C from the link.

- [x] A lists a grey **Related to** label, the title of C and its **In Progress** badge, and the header count shows 2
- [x] C lists **Related to** with the title of A, next to its **Blocked by** link to B
- [x] The label reads the same ("Related to") on both jobs

## NHR-13 Duplicates
**As:** Nikola (admin of Residential, assignee of D) · **Day:** any, after ONB-33 · **Needs:** NHR-12 done

1. Open D. Click **Relationships** to open it (no links yet), then **+ Add**, choose **Duplicates**.
2. Type "Maple Row" in **Job**, click "Replace kitchen window at 18 Maple Row" and click **Add**.
3. Open A from the link.

- [x] Nikola, an admin, sees **+ Add** and can add the link
- [x] D lists a yellow **Duplicates** label with the title of A
- [x] A lists **Duplicated by** with the title of D, next to **Blocked by** B and **Related to** C

## NHR-14 The same link twice, and a cycle
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** NHR-13 done

1. Open A, find **Relationships** (open it if collapsed), click **+ Add**, choose **Blocked by**, pick B (not E) and click **Add**.
2. Open B, find **Relationships**, click **+ Add**, choose **Blocked by**, search "Replace kitchen window at 18 Maple Row", pick A and click **Add**.
3. Look at B's list.
4. Click the **×** on the **Blocked by** row of B.

- [x] Step 1 shows an error toast that says "This relationship already exists", and A still has one **Blocked by** row for B
- [x] Step 2 is accepted (the app has no cycle check): B now shows **Blocks** A and **Blocked by** A
- [x] Nothing breaks: A and B both still open and their other links stay
- [x] The **×** removes the **Blocked by** row at once, without a confirmation question, and B shows only **Blocks** for A and C
- [x] A still shows **Blocked by** B

## NHR-15 Remove a link from either side
**As:** Nikola (admin), then Ana · **Day:** any, after ONB-33 · **Needs:** NHR-14 done

1. As Nikola open C and click the **×** on its **Related to** row (A).
2. Open A.
3. As Ana open A and click the **×** on its **Duplicated by** row (D).
4. Open D.

- [x] C no longer lists A, and A no longer lists C
- [x] The jobs A and C are unchanged: same status, same other links
- [x] A no longer lists **Duplicated by** D, and D no longer lists **Duplicates**, so a link is removed from the side that didn't create it
- [x] A still lists **Blocked by** B and C still lists **Blocked by** B

## NHR-16 A linked job that gets deleted
**As:** Ana (Residential owner) · **Day:** any, after ONB-33 · **Needs:** NHR-15 done, E unassigned and **New**

1. Open A, find **Relationships** (open it if collapsed), click **+ Add**, choose **Related to**, search "entered twice", pick E and click **Add**.
2. Open E from the link. Open the menu (the three dots), click **Delete Job** and confirm.
3. Open A again.
4. Click the **×** on the new row.

- [x] E opens from the link, and the delete is confirmed with its title in the message
- [ ] A's row for E shows "(deleted)" in grey italics, not as a link, and without a status badge — ❌ JOB-409 (it is a link, and clicking it opens a 404)
- [x] The header count still includes the row, and the **×** removes it
- [x] Nobody gets a notification for E being deleted, because it had no assignee

## NHR-17 The screen hides link management from Members
**As:** Ivana (member, assignee of B), then Luka (member, assignee of A) · **Day:** any, after ONB-33 · **Needs:** NHR-16 done

1. As Ivana open B and find **Relationships** (open it if collapsed).
2. As Luka open A and find **Relationships** (open it if collapsed).
3. Click the title of B in Luka's list.

- [x] Ivana sees two **Blocks** rows (A and C), each with a status badge
- [x] Ivana's screen shows no **+ Add** and no **×**
- [x] Luka sees **Blocked by** B, and his screen shows no **+ Add** and no **×**
- [x] Luka's click on B ends on the page that says the job failed to load, because B isn't assigned to him

## NHR-18 Completing a blocker tells the waiting jobs
**As:** Ivana (assignee of B), then Ana · **Day:** any, after ONB-33 · **Needs:** NHR-17 done, B **New** with A and C **In Progress** and waiting on it

1. As Ivana open B and click **Start**.
2. As Ana open B, click **Complete** and confirm.
3. Check the bells of Luka, Sara, Ivana, Nikola and Ana.
4. As Ana open A and C and look at their **Relationships**.

- [x] Luka gets **Blocker cleared** about A, which names B, and Sara gets one about C
- [x] Ivana gets **Status changed** for the completion and no **Blocker cleared**, and Nikola gets **Status changed**
- [x] Ana gets nothing for the completion, which she did herself
- [x] On A and C the row for B has a struck-through title and a **Completed** badge
- [x] A and C keep their **In Progress** status, nothing changes by itself
- [x] Ana and Nikola got **Status changed** when Ivana started B

## NHR-19 Complete and reopen in the history
**As:** Ana (owner), then Luka · **Day:** any, after ONB-33 · **Needs:** NHR-18 done, A **In Progress**

1. As Ana open A, click **Complete** and confirm.
2. Find **Status history** (open it if it's collapsed).
3. As Luka open A and look at the buttons.
4. As Ana click **Reopen** and check **Status history** again.

- [x] An owner can complete the job directly although the project's approval gate is on
- [x] The history gets **In Progress** → **Completed** by "Ana Petrović", with a line for the time spent In Progress
- [x] Luka, who is assigned, sees no **Reopen** button on the completed job
- [x] **Reopen** adds **Completed** → **In Progress** by Ana, and the history now has 6 entries
- [x] Luka and Nikola each get **Status changed** for the completion and again for the reopen

## NHR-20 Notes, history and relationships in Serbian
**As:** Luka (member, Serbian UI) · **Day:** any, after ONB-33 · **Needs:** NHR-19 done

1. Open A. Look at the headings of **Notes**, **Relationships** and **Status history**.
2. Make sure each section is open (open it if collapsed).

- [x] The three headings are in Serbian, with no English left in the sections (apart from the status code in the duration line, the known bug in NHR-07)
- [x] The history labels ("Created as", the arrow between statuses, "Reason: …") and the status badges are in Serbian
- [x] The line about the time spent in a status uses Serbian words and plural forms, for example "5 minuta"
- [x] The relationship label is in Serbian
- [x] The note texts stay as their authors wrote them, in English, and the markdown note is still formatted
- [x] The dates and times of notes and history are written the same way as elsewhere in Luka's UI

## NHR-21 Office Refurbishments hides what its settings hide
**As:** Nikola (Office owner) · **Day:** any, after ONB-33 · **Needs:** ONB-23 and ONB-28 done

1. Open "Fit partition walls on the 3rd floor" in Office Refurbishments.
2. Look at the sections under the action bar.

- [x] **Notes** is shown and holds Ivana's note from ONB-28
- [x] **Relationships** and **Status history** aren't shown
- [x] No row of locked sections or **Upgrade in org settings** is shown either, because Brightline owns the add-ons and the project only hides them

## NHR-22 Pine & Pixel: the sections are locked
**As:** Petra (owner of Pine & Pixel, no add-ons) · **Day:** any, after ONB-33 · **Needs:** ONB-31 done

1. Open "Design menu cards for Juniper Cafe". Open the browser's developer tools, Network tab, and reload.
2. Look at the page below the action bar.
3. Click **Upgrade in org settings**.

- [x] The page shows no **Notes**, **Relationships** or **Status history** section
- [x] A row with a lock lists "Notes · Approvals · Job History · Relationships · Links" and **Upgrade in org settings**
- [x] The Network tab shows no request to `/notes` or `/history` for this job
- [x] **Upgrade in org settings** opens **Organisation Settings**

## NHR-23 Pine & Pixel: the server refuses the gated calls too
**As:** Petra (owner of Pine & Pixel, no add-ons) · **Day:** any, after ONB-33 · **Needs:** NHR-22 done. A terminal with `curl` (on macOS use **Copy as cURL (bash)**).

**Keep the token private.** The copied command holds the tester's `Authorization: Bearer …` token. Never paste it, or any header from it, into a job, PR, finding, chat or doc: record only the status and message. Start each command line with a space (it stays out of the history in zsh, the macOS default, after `setopt hist_ignore_space`, and in bash after `HISTCONTROL=ignorespace`) so it stays out of the shell history. The token lasts 5 minutes: copy a fresh request just before each call, and again on a 401.

1. In Petra's browser, Network tab, right-click any request to `/api/` (for example the job) and choose **Copy as cURL**. Paste it in the terminal, with a leading space. The project and job IDs in the path are the friendly IDs in the browser address bar of "Design menu cards for Juniper Cafe".
2. Change the path to `/api/projects/<project>/jobs/<job>/notes` and send it. Copy a fresh request first for each step below.
3. Change it to `/api/projects/<project>/jobs/<job>/history` and send it.
4. Change it to `/api/projects/<project>/jobs/<job>/relationships`, add `-X POST -H 'Content-Type: application/json' -d '{"targetJobId":"00000000-0000-0000-0000-000000000001","type":"RELATED_TO"}'`, and send it.

- [x] The notes call is refused with status 403 and the message "NOTES add-on is required to access this feature"
- [x] The history call is refused with status 403 and "JOB_STATUS_HISTORY add-on is required to access this feature"
- [x] The relationship call is refused with status 403 and "JOB_RELATIONSHIPS add-on is required to access this feature"

## NHR-24 Everyone has the notifications this file sent, and no others
**As:** Ana, Nikola, Luka, Ivana, Sara, Petra · **Day:** any, after NHR-23 · **Needs:** the scenarios above done

1. For each person open `/notifications` and read the entries about the Maple Row and Maple Road jobs.
2. Compare with the table at the top of this file. Filter by type to count them, counting only the entries about the Maple Row and Maple Road jobs (other files add their own).

- [x] Ana has 2 **Note added**, 3 **Status changed** and 1 **Job blocked**, and nothing else from this file
- [x] Nikola has 1 **Job assigned**, 7 **Status changed** and 1 **Job blocked**, and no **Note added** or **Blocker cleared**
- [x] Luka has 1 **Job assigned**, 3 **Note added**, 3 **Status changed** and 1 **Blocker cleared**
- [x] Ivana has 1 **Job assigned** and 1 **Status changed**, and nothing about A, C or D
- [x] Sara has 1 **Job assigned** and 1 **Blocker cleared**, and nothing about B or the notes
- [x] Petra has no notification, and nobody has an entry for an action they did themselves
- [x] No entry was sent for adding, removing or deleting a relationship, for the client edit or for the deleted job E

## NHR-25 A member can't read notes of a job that isn't theirs (API)
**As:** Sara (member, not assigned to A) · **Day:** any, after NHR-05 · **Needs:** NHR-05 done. A terminal with `curl`. Read the token warning in NHR-23: it applies here too.

1. In Sara's browser, Network tab, right-click any request to `/api/` and choose **Copy as cURL**, and paste it with a leading space. The project and job IDs are the friendly IDs from A's address (the one Nikola copied in NHR-05).
2. Change the path to `/api/projects/<Residential>/jobs/<A>/notes` and send it.
3. With a fresh copy, change the path to `/api/projects/<Residential>/notes` and send it.
4. With a fresh copy, change the path to `/api/projects/<Residential>/jobs/<A>/history` and send it.

- [x] The history call is refused with status 403 and "Access denied: you are not assigned to this job"
- [ ] The notes call for A is refused with status 403, as the history call is (likely bug: `NoteService.listByJob` only checks project membership, so Sara probably gets the notes, expected to fail, Bug job to be linked)
- [ ] The project notes call is refused with status 403 (likely bug: `NoteService.listByProject` only checks membership, so it probably returns every job's notes to a Member, expected to fail, Bug job to be linked)

## Not manual
- A job can't be linked to itself (the window never offers it): covered by `JobRelationshipIntegrationTest` ("Should return 400 when job references itself") and `JobRelationshipServiceTest`.
- A job from another project can't be linked: covered by `JobRelationshipIntegrationTest` ("Should return 404 when target job belongs to a different project"); NHR-09 checks that the window doesn't offer one.
- A relationship id on the wrong job is refused: covered by `JobRelationshipIntegrationTest` ("Should return 404 when relationship does not belong to the given job").
- Callers who aren't project members get 403 on notes, history and relationships: covered by `NoteIntegrationTest` ("create should return 403 when caller is not a project member"), `JobHistoryIntegrationTest` ("getHistory_shouldReturn403_whenCallerIsNotMember") and `JobRelationshipIntegrationTest` ("Should return 403 when requester is not a project member").
- A member can't read the history of a job that isn't theirs: covered by `JobHistoryIntegrationTest` ("getHistory_shouldReturn403_whenMemberAccessesUnassignedJob").
- A Member adding or removing a relationship through the API is allowed by design (ADR-0022): covered by `JobRelationshipIntegrationTest` ("Member should create a BLOCKED_BY relationship and receive 201" and "Member should delete a relationship and receive 204"). NHR-17 checks that the screen hides the buttons.
- A blank note or one over 10,000 characters is refused by the server: covered by `NoteIntegrationTest` ("create should return 400 when content is blank" and "… when content exceeds 10000 characters").
- `GET /api/projects/{projectId}/notes` (all notes of a project, grouped by job) has no screen: covered by `NoteIntegrationTest` ("listByProject should return notes grouped by job, jobs ordered by most recent note").
- Notes on a completed (read-only) project are refused: covered by `NoteIntegrationTest` ("createNote_shouldReturn409_whenProjectIsCompleted"). `03-projects.md` completes a project on day 6.
- Past due subscription: notes can still be read and not added: covered by `NoteIntegrationTest` ("createNote should return 403 when org subscription is past due" and "listByJob should still return 200 when org subscription is past due"). `12-billing.md` checks the screens.
- The block reason in the history is stored trimmed: covered by `JobHistoryIntegrationTest` ("getHistory_shouldRecordTrimmedBlockReason_notRawPaddedInput (JOB-256)").

## Findings
- NHR-16: JOB-409 (Bug) a relationship to a deleted job shows "(deleted)" as a link, and clicking it opens the deleted job's page with a 404, instead of plain grey italic text.
