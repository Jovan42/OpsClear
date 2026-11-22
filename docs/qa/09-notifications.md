# Notifications

Last run: 2026-11-20, Jovan (in progress)
Guide: /guide/notifications

This file covers the notification centre (bell, dropdown and `/notifications` page), live updates
over the stream, notification settings, every one of the 16 notification types, and the daily
deadline alerts that need real calendar days. The rules are in
[ADR-0051](../dev/decisions/0051-in-app-notification-centre.md). It uses the world built by
`01-onboarding.md` (see the [README](README.md#test-world)): Brightline Renovations with Ana,
Nikola, Ivana, Luka and Sara, the projects Residential Fit-outs (gate on) and Office Refurbishments
(gate off), and Petra in Pine & Pixel Studio. Run `01-onboarding.md` first.

**Rules to check throughout** (each scenario names the people who must get a notification and the
people who must not):

- The person who did the action never gets a notification about it.
- One action gives one notification per person, even when they qualify twice (for example an admin
  who is also the assignee).
- A member only hears about jobs they can see, which are the ones assigned to them.
- Notifications are in-app only. No email is sent for any of them.

**What to open.** The bell is in the top bar and shows a red unread badge. Click it for the
dropdown, **View all** opens `/notifications`. "Check the centre" below means: open the bell, or
`/notifications` when the dropdown is too short. Entries from the system (deadline alerts,
schedules, billing, feedback) show **OpsClear** as the source.

**Setup.** Keep the six browser profiles from `01-onboarding.md` open. Notifications arrive live, so
leave a tab on a normal page (not the bell) for anyone you're waiting on. Day 1 is today (UTC), "Day
3" is today + 2 and "Day 4" is today + 3, as in `01-onboarding.md`. Deadline alerts run daily at
06:00 UTC, so the deadline scenarios start on day 2 after that time.

**Jobs these scenarios use.** `NTF-02` creates five extra jobs, so the notification checks don't
disturb the day-1 jobs that `01-onboarding.md` says to leave alone. Don't change those day-1 jobs'
deadlines, assignees or status here: the day plan's deadline checks (`NTF-26` to `NTF-30`) use them.
The scenarios run in the order written, and several leave a job in a state the next one expects
(named in `Needs:`).

**Luka's language.** From ONB-33 Luka uses the Serbian UI. In every check where Luka reads an entry
or a setting, the quoted English text is the English wording: read the Serbian equivalent and tick
when it means the same. NTF-33 judges the Serbian text itself. Checks of exact English wording are
played as Sara or Ivana.

## NTF-01 An empty notification centre
**As:** Petra (owner, Pine & Pixel Studio) · **Day:** 1 · **Needs:** Petra signed up and her org created in `01-onboarding.md`

1. Look at the top bar. Click the bell.
2. Click **View all**.
3. Click **Unread**, then pick one type in **Filter by type**.

- [x] The bell shows no badge
- [x] The dropdown says "No notifications yet." and has no **Mark all as read**
- [x] `/notifications` says "No notifications yet."
- [x] **Unread** shows "No unread notifications." and a type filter shows "No notifications of this type."

## NTF-02 Create the notification working jobs
**As:** Ana (Residential owner) · **Day:** 1 · **Needs:** `01-onboarding.md` done

In Residential Fit-outs, **Jobs**, **+ New Job**, create these jobs. Use a one-line description such
as "Client wants the work finished before the weekend". Other scenarios in this file call them by
title.

| Title | Client | Deadline | Priority | Milestone | Assign to |
|-------|--------|----------|----------|-----------|-----------|
| Fit heated towel rail at 14 Elm St | Okafor family | none | Medium | Bathroom phase | Luka |
| Re-seal shower at 6 Alder Court | Pryce family | **Day 3** | Medium | Bathroom phase | Sara |
| Replace boiler flue at 27 Hawthorn Rd | Calloway family | none | Medium | Kitchen phase | Nikola |
| Make good plaster at 27 Hawthorn Rd | Calloway family | none | Low | Kitchen phase | Ivana |
| Remove garden shed at 3 Willow Lane | Ashby family | none | Low | Handover | Sara |

Then create a sixth job with **Assign to** empty: "Get quote for skip hire at 3 Willow Lane", client Ashby family, no deadline, Low priority. Delete it again (**Delete Job**) once checked.

- [x] All five jobs in the table are created as **New**
- [x] Luka, Sara, Nikola and Ivana each get an "Assigned you to …" notification (type **Job assigned**) for each job assigned to them, with Ana as the source
- [x] Sara has two of them, one for the shower and one for the shed
- [x] Ana gets none, because she created them
- [x] The sixth job, created with **Assign to** empty, notifies no one

## NTF-03 A reassigned job notifies the new assignee live
**As:** Ana, watching Ivana's screen too · **Day:** 1 · **Needs:** NTF-02 done, the shed job still **New** with Sara. Ivana has the app open on **Projects**, with the bell closed.

1. As Ana, open "Remove garden shed at 3 Willow Lane" (Sara's job), click **Edit**, change **Assign to** to Ivana and save.
2. Watch Ivana's top bar without touching her page.
3. As Ana, edit the job again and assign it back to Sara.
4. Check the centre as Sara, Ivana, Nikola and Ana.

- [x] Ivana's badge goes up by one within a few seconds, with no refresh
- [x] Ivana's entry reads "Assigned you to …", names Ana, shows "Remove garden shed at 3 Willow Lane" and is highlighted as unread
- [x] Sara, the previous assignee, gets nothing when it moves to Ivana
- [x] Sara gets "Assigned you to …" when it moves back
- [x] Nikola and Ana get nothing for either change (Ana made them)

## NTF-04 Dropdown and click-through
**As:** Ivana · **Day:** 1 · **Needs:** NTF-03 done, and Ivana has more than 10 notifications. If she has fewer, check the other boxes now and the "10 newest" one later.

1. Click the bell and look at the list and the header.
2. Click the "Assigned you to Make good plaster at 27 Hawthorn Rd" entry.
3. Click the bell again, then **Mark all as read**.
4. Click the bell and **View all**.

- [x] The dropdown lists at most the 10 newest entries, with unread ones highlighted and a relative time ("5 min ago")
- [x] Clicking an entry opens that job, marks the entry read and drops the badge by one
- [x] **Mark all as read** removes the badge and the highlight on every entry
- [x] **View all** opens `/notifications`
- [x] Pressing Escape, or clicking outside, closes the dropdown

## NTF-05 Where each entry opens
**As:** Ana, Sara, Ivana · **Day:** 2 · **Needs:** the job events (NTF-13 to NTF-23) done, so the centres hold one of each type except schedule and past due, which NTF-24 and NTF-25 check themselves.

1. For each entry below, open it from the centre and note where you land.
2. Use Ana for the approval and members entries, Sara for the project entry, Ivana for the deleted-job and feedback entries.
3. Hover over the deleted-job entry.

- [ ] A job entry (assigned, status, blocked, note, deadline) opens that job
- [ ] An approval entry opens the job at its approvals section
- [ ] **Added to project** opens the project
- [ ] **Invite accepted** opens the org members page
- [ ] **Feedback resolved** opens **Feedback**
- [ ] A deleted-job entry reads "Deleted …" with the job's title, has no link, and shows the tooltip "This job was deleted" on hover

## NTF-06 The Notifications page, filters and paging
**As:** Ana · **Day:** 4 · **Needs:** Ana has more than 20 notifications. Run this once the invites, jobs and deadline alerts have piled up. If she has fewer, run it on day 5.

1. Open `/notifications`. Look at the first page, the pager and the order.
2. Click **Next**, then **Previous**.
3. Click **Unread**. Pick a type in **Filter by type**, then **All types**.
4. On one entry click **Mark as read**, then **Mark as unread**.
5. Click **Mark all as read**.

- [ ] The page shows 20 entries, newest first, with "Page 1 of N" and **Next**
- [ ] **Next** and **Previous** move between pages, and the page number follows
- [ ] **Unread** lists only unread entries and **Filter by type** lists only that type, and the two combine
- [ ] **Mark as read** and **Mark as unread** on one entry change its highlight and the bell badge at once
- [ ] **Mark all as read** clears the badge and every highlight, including on other pages
- [ ] A filter with no match shows "No unread notifications." or "No notifications of this type."

## NTF-07 Read state follows across tabs
**As:** Nikola, in two tabs · **Day:** 1 · **Needs:** NTF-02 done, Nikola has unread notifications

1. Open the app in two tabs for Nikola, both on **Projects**.
2. In tab A open the bell and click one entry.
3. In tab B, don't touch the page. Look at the badge.
4. In tab A click **Mark all as read**.
5. Look at tab B.

- [x] Tab B's badge drops by one shortly after step 2, without a refresh
- [x] Tab B's badge disappears after step 4
- [x] Opening the bell in tab B shows every entry as read

## NTF-08 Missed notifications catch up after a lost connection
**As:** Luka (and Ana to cause the event) · **Day:** 1 · **Needs:** NTF-02 done

1. As Luka, open the app. In the browser's developer tools, Network tab, set throttling to **Offline**. Note the badge. If the open stream keeps delivering anyway (DevTools Offline doesn't always cut it), turn off the computer's Wi-Fi or network instead, and turn it back on in step 4.
2. As Ana, add a note on "Fit heated towel rail at 14 Elm St" and confirm it. If you took the whole computer offline, do this from the second device used in NTF-09.
3. Wait ten seconds. Look at Luka's badge.
4. Set Luka's throttling back to **No throttling**. Wait up to a minute.

- [x] While offline, Luka's badge doesn't change
- [x] After going back online the badge catches up by itself, with no manual refresh, and the note entry is in the list
- [x] No duplicate entry appears

Ana's note makes her a participant in the thread. NTF-15 expects that: its first step adds a second note by her.

## NTF-09 Notification sound
**As:** Ivana, with Ana causing events from a second device · **Day:** 1 · **Needs:** NTF-02 done. Ivana's browser has sound on. Ana uses a phone, or another computer, logged in as Ana. Don't use another browser window on Ivana's machine: the sound only plays in a visible, focused tab, so acting in another window takes focus away from Ivana's tab.

1. As Ivana, open the user menu, **Account settings**, and find **Notifications**. Check **Notification sound** is **On**.
2. Keep Ivana's tab in front and focused, and don't touch her machine. On the second device, as Ana, open "Make good plaster at 27 Hawthorn Rd" and change the priority to Medium, then straight away set a deadline of Day 4 + 25 days (two saves within three seconds).
3. Switch Ivana's tab to the background (another tab or window). As Ana, remove the deadline.
4. In Ivana's settings set **Notification sound** to **Off** and reload. As Ana, set the priority back to Low.

- [x] A short sound plays in the tab you're using when a notification arrives
- [x] The two quick events play one sound, not two
- [x] Nothing plays while the tab is in the background
- [x] With the sound **Off**, nothing plays and the notification still appears
- [x] The choice is still **Off** after a reload

Set **Notification sound** back to **On** at the end. Ivana now has four "Deadline or priority changed" entries for this job.

## NTF-10 Settings list every type
**As:** Sara · **Day:** 1 · **Needs:** NTF-02 done

1. Open the user menu, **Account settings**, and scroll to **Notifications**.
2. Read **Notification types** from top to bottom.

- [x] **Notification types** has two groups, **Job events** and **Project, organisation and account**
- [x] 16 types are listed, 11 job events and 5 project, organisation and account ones
- [x] Every type is on and none is muted by default
- [x] **Subscription past due** is on and locked, with "Always on: billing problems affect the whole organisation."
- [x] The section says the settings apply on all your devices

## NTF-11 Mute and unmute a type
**As:** Luka, with Ana causing events · **Day:** 1 · **Needs:** NTF-10 done, "Fit heated towel rail at 14 Elm St" still **New** and assigned to Luka

1. As Luka, open **Account settings**, **Notifications** and turn **Status changed** off.
2. Open a private window, sign in as Luka and look at the same section.
3. As Ana, open the towel rail job and click **Start**. Then, as Ana, set the priority to High.
4. As Luka, check the centre.
5. Turn **Status changed** on again.

- [x] Turning the toggle off saves with no error and stays off after a reload
- [x] The private window also shows **Status changed** off
- [x] Luka gets the "Deadline or priority changed" entry but no "Moved … to In Progress" one, and the badge counted only that entry
- [x] After turning it on again, the missed status notification doesn't show up later
- [x] Other types keep arriving while one is muted (the priority entry is the proof; NTF-14 proves **Status changed** works again)

Leave the towel rail **In Progress** with priority High: NTF-14 to NTF-17 expect that.

## NTF-12 A change that can't be saved is put back
**As:** Sara · **Day:** 1 · **Needs:** NTF-10 done

1. Open **Account settings**, **Notifications**. Set the browser to **Offline** in developer tools.
2. Turn **Note added** off.
3. Set the browser back online and reload.

- [x] The toggle jumps back on and "Couldn't save that change, so it was put back." shows
- [x] After the reload **Note added** is still on

## NTF-13 Status changes notify the assignee's watchers
**As:** Luka, Ana, Nikola · **Day:** 1 · **Needs:** NTF-11 done

1. As Luka, open "Fix leaking tap at 7 Cedar Close" and click **Start**.
2. Check the centre as Ana, Nikola, Luka, Ivana and Sara.

- [x] Ana and Nikola each get "Moved … from New to In Progress", with Luka as the source
- [x] Luka, who did it, gets nothing
- [x] Ivana and Sara, who are only members and not assigned, get nothing

## NTF-14 Blocked jobs notify urgently, and unblocking says so
**As:** Luka, then Ana · **Day:** 1 · **Needs:** NTF-11 done (towel rail **In Progress**)

1. As Luka, open "Fit heated towel rail at 14 Elm St" and click **Block**. Choose **Waiting on client** and confirm.
2. Check the centre as Ana, Nikola, Luka, Ivana and Sara.
3. As Ana, open the same job, click **Unblock** and confirm.
4. Check the centre as Luka and Nikola.

- [x] Ana and Nikola get **Job blocked** ("Blocked …") with "Reason: Waiting on client", in the urgent (red) style
- [x] Only the one **Job blocked** entry arrives, with no extra "Status changed" entry for the same move
- [x] Luka (who did it), Ivana and Sara get nothing
- [x] Unblocking gives Luka a "Moved … from Blocked to In Progress" entry, which shows **Status changed** works again for him
- [x] Unblocking also gives Nikola a "Moved … from Blocked to In Progress" entry
- [x] Ana, who unblocked it, gets none

## NTF-15 Notes notify the assignee and everyone already in the thread
**As:** Ana, Luka, Nikola · **Day:** 1 · **Needs:** NTF-14 done. Ana already wrote one note on the towel rail job in NTF-08.

1. As Ana, open "Fit heated towel rail at 14 Elm St" and add the note "Rail delivered to site, fitting tomorrow morning." Confirm it.
2. Check the centre as Luka, Nikola and Sara.
3. As Luka, add the note "Fitted, waiting for the plumber to pressure test." Confirm it.
4. Check the centre as Ana, Nikola and Luka.

- [ ] Luka (assignee) gets "Added a note on …" from Ana
- [ ] Nikola (admin, not in the thread) and Sara get nothing from Ana's note
- [ ] Ana (in the thread) gets "Added a note on …" from Luka
- [ ] Nikola still gets nothing for Luka's note
- [ ] Luka gets nothing for his own note, and Ana gets nothing for hers

## NTF-16 Only a changed deadline or priority notifies
**As:** Ana · **Day:** 1 · **Needs:** NTF-15 done

1. Open "Fit heated towel rail at 14 Elm St", click **Edit**, change only the description and save.
2. Change the priority to Low and save.
3. Change the deadline to Day 4 + 20 days and save.
4. Remove the deadline and save.
5. Check Luka's centre.

- [ ] A description-only edit notifies no one
- [ ] The priority change gives Luka one "Deadline or priority changed" entry with "Priority: Low"
- [ ] The deadline change gives Luka one entry with "New deadline: …" in his date format
- [ ] Removing the deadline gives an entry saying "Deadline removed"
- [ ] Ana, who made each change, gets none

## NTF-17 Approval request and decision
**As:** Luka, Ana, Nikola · **Day:** 1 · **Needs:** NTF-16 done, Residential's gate on (`01-onboarding.md`)

1. As Luka, open "Fit heated towel rail at 14 Elm St" and click **Request completion**. Add the reason "Fitted and pressure tested" and confirm.
2. Check the centre as Ana, Nikola, Ivana, Sara and Luka.
3. As Ana, open the request, click **Reject** and comment "Photo of the finished joints please".
4. Check Luka's centre. As Luka, request completion again.
5. As Nikola, approve the request with the comment "Looks fine".
6. Check Luka's centre.

- [ ] Ana and Nikola each get "Requested to complete …" from Luka, and it opens the job at its approvals
- [ ] Ivana, Sara and Luka get nothing about the request
- [ ] Luka gets "Rejected your request on …" from Ana, with the comment shown
- [ ] After the second request Ana and Nikola each get a new request entry
- [ ] Luka gets "Approved completion of …" from Nikola
- [ ] The job is **Completed**

## NTF-18 Changing the assignee and the deadline in one edit
**As:** Ana · **Day:** 1 · **Needs:** NTF-02 done, "Remove garden shed at 3 Willow Lane" still **New** with Sara

1. Open the job, click **Edit**, set **Assign to** to Ivana and Deadline to Day 4 + 30 days, and save once.
2. Check Ivana's, Sara's and Ana's centres.

- [ ] Ivana gets exactly one new "Assigned you to …" entry (on top of the one from NTF-03), which shows the deadline, and no "Deadline or priority changed"
- [ ] Sara, the previous assignee, gets nothing
- [ ] Ana gets nothing

## NTF-19 A deleted job notifies its assignee
**As:** Ana · **Day:** 1 · **Needs:** NTF-18 done

1. Open "Remove garden shed at 3 Willow Lane", click **Delete Job** and confirm.
2. Check Ivana's centre. Click the entry.

- [ ] Ivana gets "Deleted …" with Ana as the source
- [ ] The entry still shows the job's title, though the job is gone
- [ ] Clicking it opens nothing, and hovering over it shows the tooltip "This job was deleted"
- [ ] Ana and Sara get nothing

## NTF-20 Completing a blocker clears the jobs waiting on it
**As:** Ana, Nikola, Ivana · **Day:** 1 · **Needs:** NTF-02 done. Both Hawthorn Rd jobs still **New**.

1. As Ana, open "Make good plaster at 27 Hawthorn Rd", find **Relationships**, click **+ Add**, choose **Blocked by**, search "Replace boiler flue" and add it.
2. As Ana, open "Replace boiler flue at 27 Hawthorn Rd" and click **Start**.
3. Check Nikola's centre. As Ana, click **Complete** and confirm.
4. Check Ivana's, Nikola's, Sara's and Ana's centres.

- [ ] Nikola, who is both the assignee and a project admin, gets exactly one "Moved … from New to In Progress" at step 2, and one "Moved … to Completed" at step 3
- [ ] Ivana gets "Replace boiler flue … is completed, so … is no longer waiting on it", about the plaster job
- [ ] Ivana gets exactly one entry for the completion, and no "Status changed" for the flue (she isn't on it)
- [ ] Ana and Sara get nothing

## NTF-21 Added to a project, and what happens when you're removed
**As:** Ana, Sara · **Day:** 2 · **Needs:** `01-onboarding.md` done, Spring Maintenance 2026 not archived yet. Do this before `03-projects.md` archives it.

1. As Sara, open the bell, find "Added you to Residential Fit-outs as Member" from day 1 and click it.
2. As Ana, open **Spring Maintenance 2026**, **Settings**, **Members**, search `<mailbox>+sara`, choose **Member** and click **Add**.
3. Check Sara's centre and click the new entry.
4. As Ana, remove Sara from Spring Maintenance 2026.
5. Check Sara's centre and badge again.
6. Check Nikola's and Ivana's centres for their day-1 entries.

- [ ] The day-1 entry opens Residential Fit-outs
- [ ] Sara gets "Added you to Spring Maintenance 2026 as Member" from Ana, and Ana gets none
- [ ] The entry opens the project
- [ ] After she's removed, Sara's centre and badge no longer show the Spring Maintenance entry, while her Residential entries stay
- [ ] Nikola's day-1 entry for Residential says "as Admin" and Ivana's says "as Member"

Re-add Sara to Spring Maintenance 2026 only if `03-projects.md` needs her there.

## NTF-22 Invite accepted goes to the inviter
**As:** Ana, Nikola · **Day:** 1 · **Needs:** the four invites accepted in ONB-11 and ONB-13 (Ana sent them all)

1. As Ana, open the bell and look for the accepted invites.
2. Click one.
3. As Nikola, check the centre for any "Accepted your invite" entries.

- [ ] Ana has four "Accepted your invite to Brightline Renovations" entries, one each from Nikola, Ivana, Luka and Sara
- [ ] An entry opens **Manage members**
- [ ] Nikola has none, because Ana is the inviter

## NTF-23 Feedback resolved
**As:** Ivana, and the super-admin (the developer's real account) · **Day:** 2 · **Needs:** nothing

1. As Ivana, open **Feedback**, **New submission**. Submit type **Bug**, title "Print button missing on job page", description "Steps: open a job, look for Print. Expected: a print option." Submit a second one: type **Feature**, title "Add a photo field to jobs", description "Site photos on each job would help the crew."
2. As the super-admin, open `/admin/feedback`. On the first submission click **Decline**.
3. On the second submission click **Grant credit**, set the amount to 5 and confirm.
4. As Ivana, check the centre and **My submissions**.
5. Click the credit entry.

- [ ] Both submissions show **Pending review** in Ivana's **My submissions** until reviewed
- [ ] Ivana gets "\"Print button missing on job page\" was reviewed and declined" from **OpsClear**
- [ ] Ivana gets "\"Add a photo field to jobs\" earned a credit of 5" from **OpsClear**
- [ ] **My submissions** shows **Declined** and **Credit granted** to match
- [ ] The entry opens **Feedback**
- [ ] Nobody else in Brightline gets either notification

## NTF-24 Schedule run missed
**As:** Ana, Nikola, Ivana · **Day:** 7, after 07:00 UTC · **Needs:** SCH-17 in `08-schedules.md` done on day 7 (resuming the paused **Daily site safety check** in Residential Fit-outs, which records exactly two missed runs: day 6 and day 7; the first skipped run, day 5, isn't recorded). A deploy can't cause a missed run: a run counts as missed only when the next one is past too, so the app would have to be down for over an hour. The optional SCH-20 (backend stopped for about 65 minutes, **Hourly yard check**) causes the same kind of notification, but its entries name **Hourly yard check** and there is usually only one. The steps and boxes below are written for SCH-17.

1. After the resume in SCH-17, wait two minutes.
2. As Ana (Owner) and Nikola (Admin), check the centre.
3. As Ivana and Sara (Members of Residential Fit-outs), check theirs.
4. As Ana, click the entry.

- [ ] Ana and Nikola each get "Schedule \"Daily site safety check\" missed a run in Residential Fit-outs" with "Missed run: …" and the time, two entries each (day 6 and day 7)
- [ ] Ivana and Sara get nothing
- [ ] The entries come from **OpsClear**
- [ ] Clicking an entry opens Residential Fit-outs, **Schedules**

## NTF-25 Subscription past due (depends on Chore 12, JOB-377)
**As:** Ana, Nikola, Ivana · **Day:** 5+ · **Needs:** `12-billing.md` has put Brightline's subscription into PAST_DUE. The Paddle sandbox has no way to fail a renewal on demand, so run this only if that file finds a way. Otherwise leave it unticked, and the type stays covered under **Not manual**.

1. After the subscription goes past due, check the centre as Ana, Nikola and Ivana.
2. Click the entry as Ana, then as Nikola.
3. As Ana, open **Account settings**, **Notifications**.

- [ ] Ana and Nikola get "The subscription payment for Brightline Renovations is past due" in the urgent style, from **OpsClear**
- [ ] Ana's entry says to update the payment method in billing, and Nikola's says to ask an organisation Owner
- [ ] Ivana (member) gets nothing
- [ ] Ana's entry opens **Organisation Settings** at billing, and Nikola's opens **Organisation Settings**
- [ ] **Subscription past due** is still locked on in Ana's settings

## NTF-26 Day 2: deadline soon for jobs due on day 3
**As:** Luka, Ivana, Sara, Ana, Nikola · **Day:** 2, after 06:00 UTC · **Needs:** NTF-02 and the day-1 jobs of `01-onboarding.md` untouched. Jobs due day 3: "Tile bathroom at 14 Elm St" (Luka), "Paint hallway and stairs at 9 Birch Rd" (Ivana), "Fit partition walls on the 3rd floor" (Ivana), "Re-seal shower at 6 Alder Court" (Sara).

1. Check each person's centre after 06:00 UTC, and the time on the entries.
2. As Ana, open "Re-seal shower at 6 Alder Court", click **Edit**, change the deadline to Day 5 and save.
3. Check Sara's centre.

- [ ] Luka has "Tile bathroom at 14 Elm St is due tomorrow", under **Deadline tomorrow**, from **OpsClear**
- [ ] Ivana has one such entry for the paint job and one for the partition walls
- [ ] Sara has one for the shower job, then "Updated …" with "New deadline: …" after Ana's edit
- [ ] Ana and Nikola have no **Deadline tomorrow** entry (only the assignee gets it)
- [ ] The jobs due on day 4, and the jobs with no deadline, have none yet
- [ ] The entries arrived after 06:00 UTC and not before

## NTF-27 Schedule-created jobs notify the assignee
**As:** Ivana, Sara, Ana, Nikola · **Day:** 2 and 3, after 07:00 UTC · **Needs:** `08-schedules.md` done up to the day-2 and day-3 runs: **Daily site safety check** in Residential Fit-outs (07:00 UTC, Ivana first, then Sara) and **Weekly toolbox talk** (no assignee, runs on day 3)

1. On day 2 after 07:00 UTC, check Ivana's centre, then Ana's and Nikola's.
2. On day 3 after 07:00 UTC, check Sara's centre and the others again.

- [ ] Ivana has "Assigned you to …" for `Site safety check for Residential Fit-outs - <day 2 date>`, from **OpsClear**, with no person as the source
- [ ] Sara has the same for `Site safety check for Residential Fit-outs - <day 3 date>` on day 3
- [ ] Ana and Nikola get nothing just because the schedule ran
- [ ] `Toolbox talk week <week number>`, made on day 3 with no assignee, notifies no one

## NTF-28 Day 3: deadline soon for jobs due on day 4, and complete the tile job
**As:** Luka, Ana, Ivana, Sara · **Day:** 3, after 06:00 UTC · **Needs:** NTF-26 done

1. Check Luka's, Ivana's and Sara's centres.
2. As Luka, open "Tile bathroom at 14 Elm St" and click **Request completion**, with a reason, before the day ends.
3. As Ana, approve it.

- [ ] Luka has "Install kitchen worktops at 31 Oak Ave is due tomorrow" and "Replace ceiling lights in the open-plan office is due tomorrow"
- [ ] Nobody gets a second **Deadline tomorrow** for a job that already had one on day 2
- [ ] Sara has no new **Deadline tomorrow** today, because the shower deadline moved to Day 5 (NTF-29 checks it tomorrow)
- [ ] The tile job becomes **Completed** and Luka gets "Approved completion of …"

## NTF-29 Day 4: overdue, and no repeats
**As:** Ivana, Ana, Nikola, Luka, Sara · **Day:** 4, after 06:00 UTC · **Needs:** NTF-28 done. Open on day 4: "Paint hallway and stairs at 9 Birch Rd" and "Fit partition walls on the 3rd floor". Completed: the tile job.

1. Check Ivana's, Ana's, Nikola's, Luka's and Sara's centres after 06:00 UTC.
2. Open the paint job and the partition job.

- [ ] Ivana has "… is overdue" for both open jobs, under **Job overdue**, in the urgent style, from **OpsClear**, with "Was due …"
- [ ] Ana (owner of Residential) has one for the paint job and none for the partition job (she isn't in Office)
- [ ] Nikola has one for the paint job (admin of Residential) and one for the partition job (owner of Office)
- [ ] Ivana has exactly one entry for the partition job, though she is both its assignee and an Office admin
- [ ] Nobody has "… is overdue" for "Tile bathroom at 14 Elm St", because it is Completed
- [ ] Luka has no **Job overdue** yet, because his jobs are due today (the alert comes after the deadline day has passed)
- [ ] Sara gets "Re-seal shower at 6 Alder Court is due tomorrow" once, for its new Day 5 deadline, and no overdue entry
- [ ] The day-3 jobs get no second **Deadline tomorrow** today

## NTF-30 Day 5+: still no repeats
**As:** Ivana, Ana, Nikola, Luka, Sara · **Day:** 5, after 06:00 UTC (look again on day 6 if you can) · **Needs:** NTF-29 done

1. Check each person's centre after 06:00 UTC on day 5.
2. Count **Job overdue** entries per job and person.

- [ ] "Install kitchen worktops at 31 Oak Ave" is overdue once each for Luka, Ana and Nikola
- [ ] "Replace ceiling lights in the open-plan office" is overdue once each for Luka, Nikola (Office owner) and Ivana (Office admin)
- [ ] The paint and partition jobs still have one **Job overdue** per person, not a new one each day
- [ ] The shower job (if still open) is overdue once for Sara, Ana and Nikola on day 6, with no repeat after that
- [ ] No **Deadline tomorrow** repeats for any job

## NTF-31 Each persona only hears what they should
**As:** Luka, Ivana, Sara, Nikola, Ana · **Day:** 7 or later, after NTF-24 · **Needs:** the scenarios above done

1. Open `/notifications` for each persona and read every entry, newest to oldest. After NTF-24, Ana and Nikola also have **Schedule run missed** entries (two each), which are expected. Nobody else has any.
2. Use **Filter by type** for each type a persona shouldn't have.

- [ ] Luka has no **Approval requested**, no **Invite accepted**, and no entry about a job that isn't his
- [ ] Ivana has no entry about the towel rail or the shower job, and no **Approval requested**
- [ ] Sara has nothing about a job assigned to someone else
- [ ] Nikola and Ana have **Job blocked**, **Status changed** and **Approval requested** only for projects where they are Owner or Admin
- [ ] Nobody has an entry for something they did themselves
- [ ] No notification email arrived in the mailbox for any persona (invite and verification emails apart)

## NTF-32 Notifications don't cross organisations
**As:** Petra, Ana · **Day:** 7 or later, after NTF-24 · **Needs:** the scenarios above done

1. As Petra, open the bell and `/notifications`.
2. As Ana, scan the centre for anything from Pine & Pixel Studio.

- [ ] Petra's centre has no Brightline entry of any type
- [ ] Ana's centre has no entry from Pine & Pixel Studio, and none mentions Petra
- [ ] Petra's badge doesn't move when something happens in Brightline

## NTF-33 Notifications in Serbian
**As:** Luka · **Day:** 2 · **Needs:** ONB-33 done (Luka's UI is Serbian), Luka has several kinds of notification

1. Open the bell and `/notifications`.
2. Open **Account settings**, **Notifications**.
3. Switch **Language** to English, check the centre, then switch back to Serbian.

- [ ] The entries, type names, filters, buttons and the settings section are in Serbian
- [ ] The relative times and dates are in Serbian
- [ ] Switching to English changes the existing entries too, without a reload, and switching back restores Serbian
- [ ] Names, job titles and project names stay as typed

## Not manual
- Notification cleanup (read after 30 days, unread after 90): covered by `NotificationCleanupJobTest` and the cleanup test in `NotificationIntegrationTest`.
- The `99+` badge text above 99 unread: covered by `notificationContent.test.ts`.
- Another user's notification returns 404, and the API refuses `SUBSCRIPTION_PAST_DUE` in the mute list with 400: covered by `NotificationIntegrationTest` and `NotificationSettingsIntegrationTest`.
- The stream's 25-second heartbeat and dropping closed streams: covered by `NotificationStreamRegistryTest` and `NotificationStreamIntegrationTest`.
- Deadline alert edge cases (an unassigned job records the alert but notifies no one, completed or deleted jobs and projects get none, stale alerts dropped before fan-out, the duplicate-run guard): covered by `DeadlineAlertJobTest` and `NotificationScheduledEventsIntegrationTest`.
- A Member who wrote notes and was later unassigned stops getting `NOTE_ADDED`, and fan-out skipped when a job or project was deleted in between: covered by `NotificationFanOutIntegrationTest` and `NotificationJobLifecycleIntegrationTest`.
- `SCHEDULE_RUN_MISSED` fan-out and mutes: covered by `NotificationScheduledEventsIntegrationTest` (NTF-24 is the manual check, after SCH-17).
- `SUBSCRIPTION_PAST_DUE` only on the first move into `PAST_DUE`, and never muted: covered by `PaddleWebhookServiceTest`, `PaddleWebhookIntegrationTest` and `NotificationOrgEventsIntegrationTest` (NTF-25 runs only if Chore 12, JOB-377, finds a way to get the sandbox into that state).

## Findings
