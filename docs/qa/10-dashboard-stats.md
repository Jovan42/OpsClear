# Dashboard and stats

Last run:
Guide: /guide/dashboard, /guide/stats-overview, /guide/preferences, /guide/roles-permissions

This file covers the project **Dashboard** (status ring, summary counts, the Blocked, Overdue and Pending Approvals lists, the breakdown by type), **Stats** (completed jobs per week, the five tiles, the range, the per-member table and drill-down), the **Overview** page (pending approvals across projects and the project directory), the preferences that change them (hidden dashboard sections, default stats range, Overview approvals, landing project), who sees what per role, and what an organisation without the Dashboard add-on sees. The world is built by `01-onboarding.md` ([README](README.md#test-world)): Brightline Renovations with Ana, Nikola, Ivana, Luka and Sara, the projects Residential Fit-outs and Office Refurbishments, and Petra in Pine & Pixel Studio, which has no add-ons. Run `01-onboarding.md` first.

**Run order and time window.** Run DSH-01 to DSH-15 in order, in one sitting, on one UTC day after ONB-33: start after 07:05 UTC and finish before 23:00 UTC. Reasons: the deadline alerts run at 06:00 UTC; the `Daily site safety check` schedule of `08-schedules.md` creates a New job in Residential at 07:00 UTC, which would move the New counts and Ivana's or Sara's numbers; at midnight UTC other open jobs flip to Overdue, and near local midnight (CET) the Monday of the current week can change under the Stats range. DSH-16 and DSH-17 create nothing and can run on any later day.

**If the sitting stops before DSH-11**, clear D's deadline (or delete D) before 06:00 UTC, because D is open with a past deadline. If an alert went out anyway, Ana and Nikola each have one **Job overdue** entry about D, which `09-notifications.md` day 7+ should expect.

**Numbers depend on the other files, so this file reads before and compares after.** Residential holds the day-1 jobs, the working jobs of every other file and the jobs the schedules made, and their statuses and deadlines move as the run goes on. So no check here relies on an absolute count. DSH-01 writes down a **baseline reading**, and the later checks say "goes up by one", "goes down by two" or "is back to the baseline". DSH-15 deletes this file's jobs, after which every number is back to that baseline. Checks that name a total use "at least".

**What the screens do** (so the checks below make sense):

- **Dashboard** (needs the Dashboard add-on). Opening a project lands on the Dashboard unless the user chose another **Default project page**. **Summary** has cards for **New**, **In Progress**, **Blocked**, **Completed** and **Overdue**, and **Pending approvals** for Owners and Admins only. A card of the first four opens the job list on that tab, **Pending approvals** opens the Approvals page, **Overdue** opens nothing. The ring chart only lists statuses that have jobs. **By type** only shows when at least one job has a type. The **Blocked**, **Overdue** and **Pending Approvals** lists only show when they have rows, and the Pending Approvals list shows five rows at most.
- **Who sees what.** Owners and Admins see the whole project. For a Member, the Dashboard and Stats only count the jobs assigned to them. A Member has no **Pending approvals** card and no list of pending approvals. Admins of a project get the same as its Owner.
- **Overdue.** A job is overdue once its deadline day (UTC date) is over and it isn't Completed, so a job due today is not overdue. A Blocked job past its deadline counts as Blocked and as Overdue.
- **Staying fresh.** The Dashboard loads fresh numbers when opened and after you change a job yourself. Other people's changes show after **Refresh** (the icon at the top right, with a note "Updated just now") or when you come back to the browser window. The Overview and the Approvals page also update by themselves.
- **Stats** (the Dashboard add-on). **Team stats** for Owners and Admins, **Your stats** for a Member. The range is **4 weeks**, **12 weeks** or **26 weeks**. A job counts in the week it was completed (weeks start on Monday, in the viewer's time zone). Tiles: **Completed**, **Avg. total time**, **Avg. working time**, **Avg. blocked time** and **Completed late**, with the line "N late out of M with a deadline" under it, which only shows when at least one completed job had a deadline. A job is late only when completed after the end of its deadline day (UTC date). A dash means no job has that number. Deleted jobs are not counted. Owners and Admins also get **Team** and **By member** above the tiles.
- **Overview** (organisation Owners and Admins only, in the user menu). **Pending Approvals** lists the requests waiting in every project where the person is Owner or Admin (shown when the preference **Show approvals on Overview** is on; without the Approvals add-on the heading stays and the list is empty, with "All caught up — no pending approvals across your projects."). **Project Directory** lists every project of the organisation with Owner, Status and Members, fewest members first.
- **Luka's language.** Luka uses the Serbian UI. His checks read the Serbian equivalent of the English labels named here and never compare exact English text.

**Things this file creates** (fictional customer, all in the test world). Every one of them is deleted in DSH-15, so nothing stays behind. Run inside the time window above, none of them raises a deadline alert (D is completed in DSH-11, E is due in 10 days).

| What | Where | Created in | Ends as |
|------|-------|-----------|---------|
| Type **Tiling** | Residential | DSH-01 | deleted in DSH-15 |
| Job **A** Grout kitchen splashback at 6 Hawthorn Row, Ana, type Tiling, no deadline | Residential | DSH-01 | deleted |
| Job **B** Seal bathroom floor at 6 Hawthorn Row, Ana, type Tiling, no deadline | Residential | DSH-01 | deleted |
| Job **C** Fit shower screen at 6 Hawthorn Row, Luka, no deadline | Residential | DSH-01 | deleted |
| Job **D** Collect tile samples for 6 Hawthorn Row, Ana, deadline two days ago | Residential | DSH-01 | deleted |
| Job **E** Order extractor ducting for 6 Hawthorn Row, Ana, deadline today + 10 days | Residential | DSH-01 | deleted |
| Approval requests on C (completion) and E (manual) | Residential | DSH-05 | decided in DSH-10, gone with their jobs |

DSH-16 changes preferences of Ana and puts them back. Everything else in the world is read only here.

**Notifications this file sends** (`09-notifications.md` day 7+ reads every entry in each centre, so expect these, about the Hawthorn Row jobs):

| Person | Entries |
|--------|---------|
| Ana | 2 **Approval requested** (Luka's completion request on C, Nikola's request on E), 1 **Status changed** (Luka starts C) |
| Nikola | 12 **Status changed** (A, B, D and E started, B completed, Luka starts C, C completed by approval, D and A unblocked, D, A and E completed), 2 **Job blocked** (A, D), 1 **Approval requested** (Luka's on C), 1 **Approval decided** (his request on E) |
| Luka | 1 **Job assigned** (C), 1 **Approval decided** (C approved), 1 **Status changed** (C completed), 1 **Job deleted** (C) |
| Ivana, Sara, Petra | none |

No deadline alert is sent when the sitting follows the time window. Only the recovery case above (stopped before DSH-11, no fix before 06:00 UTC) adds one **Job overdue** entry about D to Ana and Nikola.

## DSH-01 Baseline readings, the Tiling type and the working jobs
**As:** Ana (owner, Residential Fit-outs), Luka and Sara (members) · **Day:** any, after ONB-33 · **Needs:** `01-onboarding.md` done, the Dashboard and Job types add-ons on

Write the readings on paper or in a note. They are used by the checks of every later scenario.

1. As Ana, open **Residential Fit-outs**. On the **Dashboard** write down the numbers on the cards **New**, **In Progress**, **Blocked**, **Completed**, **Overdue** and **Pending approvals** (reading **R0**).
2. Click **Stats**. With **12 weeks** selected write down the **Completed** tile and the line under **Completed late** ("N late out of M", or "none" when the line isn't there). Click **By member** and write down the **Completed** number of the rows of Ana, Luka and Sara (reading **S0**).
3. As Luka, open the Residential Dashboard and write down his **New**, **In Progress**, **Blocked**, **Completed** and **Overdue** (reading **L0**). As Sara do the same (reading **SA0**).
4. As Ana, open **Types** in Residential, click **+ New**, name **Tiling**, pick any colour and click **Create**.
5. Open **Jobs**, click **+ New Job** and create the five jobs below. Client "Ashdown family" for all, **Priority** Medium, no milestone, a one-line description such as "Client wants the tiling signed off before the fitters arrive".

   | Job | Title | Assign to | Deadline | Type |
   |-----|-------|-----------|----------|------|
   | A | Grout kitchen splashback at 6 Hawthorn Row | Ana | none | Tiling |
   | B | Seal bathroom floor at 6 Hawthorn Row | Ana | none | Tiling |
   | C | Fit shower screen at 6 Hawthorn Row | Luka | none | none |
   | D | Collect tile samples for 6 Hawthorn Row | Ana | two days ago | none |
   | E | Order extractor ducting for 6 Hawthorn Row | Ana | today + 10 days | none |

- [ ] The Tiling type is created and the five jobs are created as **New**
- [ ] Luka gets one "Assigned you to Fit shower screen…" notification and nobody else gets an assignment

## DSH-02 The Dashboard shows the new jobs
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** DSH-01 done

1. Open **Projects** and click **Residential Fit-outs** (not a tab inside it).
2. Look at the **Status distribution** ring and at **Summary**.
3. Scroll down to **By type** and to the **Overdue** list.
4. Look at the top right of the page.

- [ ] Opening the project lands on the Dashboard
- [ ] **New** is five higher than in R0, and **In Progress**, **Blocked** and **Completed** are the same as in R0
- [ ] **Overdue** is one higher than in R0 and **Pending approvals** is the same as in R0
- [ ] The ring chart has a **New** entry in its legend, and no entry for a status that has no jobs
- [ ] **By type** shows **Tiling** with the number 2 and a bar
- [ ] The **Overdue** list includes "Collect tile samples for 6 Hawthorn Row" with "Ana Petrović" and a red "· due" with the date from two days ago (this assumes **Deadline format** is **Absolute**, its default), and its title shows a count that equals the **Overdue** card
- [ ] The **Overdue** list does not include "Order extractor ducting for 6 Hawthorn Row" (due in 10 days)
- [ ] The top right shows a Refresh icon with a note "Updated just now"

## DSH-03 The summary cards open the job list
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** DSH-02 done

1. On the Dashboard click the **New** card.
2. Go back and click the **Overdue** card.
3. Click the **Pending approvals** card.
4. Go back and click the arrow at the right of the row of "Collect tile samples for 6 Hawthorn Row" in the **Overdue** list.

- [ ] **New** opens the job list with the **New** tab selected, and the number on that tab equals the **New** card
- [ ] **Overdue** does nothing (the card isn't a link)
- [ ] **Pending approvals** opens the project's **Approvals** page
- [ ] The arrow opens that job

## DSH-04 Starting, completing and blocking move the numbers
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** DSH-03 done. Write down the cards again now (reading **R1**).

1. Open A, B, D and E in turn and click **Start** on each.
2. Open B and click **Complete**, then confirm **Mark Complete**.
3. Open A, click **Block**, pick **Waiting on client** and click **Block Job**.
4. Open D, click **Block**, pick **Waiting on inspection** and click **Block Job**.
5. Open the Dashboard.

- [ ] **New** is four lower than in R1
- [ ] **In Progress** is one higher than in R1 (only E is left In Progress)
- [ ] **Blocked** is two higher and **Completed** is one higher than in R1
- [ ] **Overdue** is the same as in R1, because D is overdue and Blocked
- [ ] The **Blocked** list includes A and D, with "Ana Petrović" and "· blocked today", and the reasons in red quotes ("Waiting on client", "Waiting on inspection")
- [ ] A's row is above D's (the oldest block comes first)
- [ ] D's row shows its red "· due" deadline, and A's row shows no deadline (it has none)
- [ ] D is in the **Overdue** list as well as in the **Blocked** list
- [ ] **By type** still shows **Tiling** with 2, and B's completion didn't remove it

## DSH-05 Pending approvals reach the Dashboard
**As:** Ana (owner), Luka (member), Nikola (admin) · **Day:** any · **Needs:** DSH-04 done. Ana has the Residential Dashboard open in one window and **Overview** (user menu) open in another, and the **Pending approvals** card and the Overview's **Pending Approvals** count are written down (reading **P0**).

1. As Luka, open C, click **Start**, then click **Request completion**, type "Shower screen fitted and sealed, ready for sign-off." and send.
2. As Nikola, open E, click **Request Approval**, type "Client asks for a larger extractor duct. Approve the extra cost?" and submit.
3. As Ana, look at the Overview window without reloading.
4. In the Dashboard window click the Refresh icon.
5. Scroll to **Pending Approvals** on the Dashboard.
6. Click the arrow at the right of the row about C.

- [ ] The Overview lists both requests by itself, with the job title, "Residential Fit-outs", and the label **Completion** on C's request only
- [ ] After **Refresh**, the **Pending approvals** card is two higher than P0 and the note reads "Updated just now"
- [ ] The **Pending Approvals** list includes both requests, C's with the label **Completion** and its message, E's with its description, each with the job title and a date
- [ ] The title of the list shows a count that equals the card
- [ ] The arrow opens C
- [ ] As Nikola (admin), the Residential Dashboard shows the same **Pending approvals** card and list
- [ ] As Luka, the Dashboard has no **Pending approvals** card and no **Pending Approvals** list, and his **In Progress** is one higher than in L0

## DSH-06 The Overview: approvals and the project directory
**As:** Ana (org owner), then Nikola (org admin) · **Day:** any · **Needs:** DSH-05 done

1. As Ana, click her name at the top right, choose **Overview** and read the page.
2. Click the request on C.
3. Go back and look at **Project Directory**.
4. As Nikola, open **Overview**.

- [ ] The user menu has **Overview** and the page has **Pending Approvals** and **Project Directory**
- [ ] **Pending Approvals** has a badge "N pending" with N the same as the **Pending approvals** card plus the requests of Ana's other projects, and at least 2
- [ ] Each request shows the job, the project name, the message with the label **Completion** where it applies, and "Requested" with a date and time
- [ ] Clicking a request opens that job
- [ ] **Project Directory** lists **Residential Fit-outs**, **Office Refurbishments** and **Spring Maintenance 2026** (or whichever of them still exist as projects), each with an **Owner**, a **Status** (**Active** or **Completed**) and a **Members** number
- [ ] Residential Fit-outs shows Ana as its Owner and a member count of at least 5
- [ ] The rows run from the fewest members to the most
- [ ] Nikola sees the same page

## DSH-07 Hide sections on the Dashboard and the Overview
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** DSH-05 done (Blocked, Overdue and Pending Approvals lists all have rows)

1. Open **Account settings**, find **Dashboard**. Set **Blocked jobs section** to **Hide**. Open the Residential Dashboard.
2. Back in settings, set **Blocked jobs section** to **Show**, **Overdue jobs section** to **Hide**, and open the Dashboard.
3. Set **Overdue jobs section** to **Show**, **Pending approvals section** to **Hide**, and open the Dashboard.
4. Set **Pending approvals section** to **Show**. Set **Show approvals on Overview** to **Off** and open Residential and look at **Approvals** in the top bar, then open **Overview**.
5. Set **Show approvals on Overview** to **On** and open **Overview** again.

- [ ] With the Blocked section hidden, the list is gone and the **Blocked** card still shows the same number as before
- [ ] With the Overdue section hidden, only that list is gone and the **Overdue** card keeps its number
- [ ] With the Pending Approvals section hidden, only that list is gone and the **Pending approvals** card keeps its number
- [ ] With **Show approvals on Overview** off, **Overview** shows **Project Directory** and no **Pending Approvals**, and the **Approvals** link in the project's top bar still has its badge
- [ ] With it on again, **Pending Approvals** is back on **Overview**
- [ ] Every setting is back on **Show** and **On** at the end

## DSH-08 A Member's Dashboard counts only their own jobs
**As:** Luka (member, Serbian UI), then Sara (member) · **Day:** any · **Needs:** DSH-05 done, readings L0 and SA0 from DSH-01

1. As Luka, open the Residential Dashboard.
2. Scroll through the whole page.
3. As Sara, open the Residential Dashboard.

- [ ] Luka's **New**, **Blocked**, **Completed** and **Overdue** are the same as in L0, and **In Progress** is one higher (job C)
- [ ] Luka's **Blocked** and **Overdue** lists have no row about A, D or E: those jobs belong to Ana
- [ ] Luka has no **Pending approvals** card and no **Pending Approvals** list, even though C has a pending request
- [ ] The headings, cards and lists read in Serbian, with no English left over
- [ ] Sara's five numbers are all the same as in SA0
- [ ] Sara's lists show no row about A, B, C, D or E

## DSH-09 Roles in two projects, and the Overview is closed to Members
**As:** Ivana (admin of Office Refurbishments, member of Residential), then Luka · **Day:** any · **Needs:** DSH-05 done

1. As Ivana, open the Residential Dashboard, then the Dashboard and Stats of **Office Refurbishments**.
2. Open the user menu.
3. Type `/overview` in the address bar.
4. As Luka, type `/overview` in the address bar.

- [ ] In Residential, Ivana's Dashboard has no **Pending approvals** card and her Stats page is titled **Your stats**, with no **Team** and **By member** switch
- [ ] In Office, Ivana's Dashboard has a **Pending approvals** card, her Stats page is titled **Team stats** and has the **Team** and **By member** switch
- [ ] Ivana's user menu has no **Overview**, and the address takes her to **Projects**
- [ ] Luka is taken to **Projects** as well

## DSH-10 Deciding the requests lowers the numbers
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** DSH-05 done. Write down the Dashboard cards (reading **R2**).

1. Open Residential **Approvals**, click **Approve** on the request on C, type "Looks good, thanks." and click **Approve** in the window.
2. Click **Approve** on the request on E and confirm.
3. Open the Dashboard.

- [ ] C is **Completed** without another click, and E stays **In Progress**
- [ ] **Pending approvals** is two lower than in R2 and equals P0 from DSH-05
- [ ] **Completed** is one higher and **In Progress** one lower than in R2
- [ ] The **Pending Approvals** list has no row about C or E
- [ ] Nikola gets one **Approval decided** entry, Luka gets one **Approval decided** and one **Status changed**

## DSH-11 Unblocking and finishing: Overdue clears
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** DSH-10 done. Write down the cards (reading **R3**).

1. Open D, click **Unblock**, confirm. Click **Complete** and confirm **Mark Complete**.
2. Open A, click **Unblock**, confirm. Click **Complete** and confirm **Mark Complete**.
3. Open E and complete it the same way.
4. Open the Dashboard.

- [ ] **Blocked** is two lower than in R3 and **Overdue** is one lower (D is no longer overdue once Completed)
- [ ] **Overdue** is the same as in R0, because D was the only overdue job this file made
- [ ] **Completed** is three higher than in R3
- [ ] The **Blocked** list has no row about A or D, and the **Overdue** list has no row about D
- [ ] **By type** still shows **Tiling** with 2 and still counts the Completed jobs

## DSH-12 Stats: tiles and weekly chart
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** DSH-11 done, reading S0 from DSH-01. A, B, C, D and E are all Completed now.

1. Open **Stats**. The range should show **12 weeks**.
2. Read the five tiles and the line under **Completed late**.
3. Hover the last bar of **Completed per week**.
4. Look at the horizontal axis.

- [ ] The page is titled **Team stats**
- [ ] **Completed** is five higher than in S0
- [ ] **Avg. total time**, **Avg. working time** and **Avg. blocked time** all show a value, not a dash
- [ ] The line under **Completed late** shows one more "late" and two more "with a deadline" than in S0 (D was late, E was on time); when S0 had no line, it counts from 0 out of 0
- [ ] The **Completed late** percentage matches that line, rounded
- [ ] The tooltip on the last bar reads "Week of" with the Monday of this week and a **Completed** number of at least 5
- [ ] The axis labels are dates of Mondays, and the last one is this week's Monday

## DSH-13 Stats: the range
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** DSH-12 done

1. On **Stats** click **4 weeks**, then **26 weeks**, then **12 weeks**.
2. After each click write down the **Completed** tile and the first label on the axis.

- [ ] The selected range button is the highlighted one each time
- [ ] With **4 weeks**, the axis has four labels
- [ ] The first axis label moves back in time from 4 to 12 to 26 weeks
- [ ] **Completed** with 26 weeks is at least as high as with 12, and 12 at least as high as with 4
- [ ] **Completed** with 4 weeks is at least 5

## DSH-14 Stats: by member, drill-down and Members
**As:** Ana (owner), then Nikola (admin), Ivana (member), Luka (member, Serbian UI) · **Day:** any · **Needs:** DSH-12 done

1. As Ana, on **Stats** click **By member** and read the table.
2. Click the row of Luka. Reload the page. Copy the address from the browser bar.
3. Click **Back to team**.
4. As Nikola, open **Stats** and click **By member**.
5. As Ivana, paste the address copied by Ana.
6. As Luka, open **Stats** in Residential.

- [ ] The table has one row per project member (Ana, Ivana, Luka, Nikola and Sara), in alphabetical order, even for people with no completed jobs
- [ ] Ana's row is four higher than in S0 and Luka's row is one higher, and Sara's row is the same as in S0
- [ ] The row of Luka opens his own numbers: "Showing: Luka Ilić" with **Back to team**, the address contains the member, and his tiles and chart show only his jobs
- [ ] After the reload the same member is still shown
- [ ] **Back to team** shows the team totals again, with the same **Completed** number as before the drill-down
- [ ] Nikola sees the same table and can open his own row
- [ ] Ivana sees **Your stats** with no "Showing:" line and no switch, and her **Completed** number matches her own row in Ana's table
- [ ] Luka's page is titled in Serbian "Your stats" (the Serbian equivalent), and his **Completed** matches his row in Ana's table
- [ ] The labels, tiles and chart dates read in Serbian

## DSH-15 Delete the working jobs: everything is back to the baseline
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** DSH-14 done

1. Open each of A, B, C, D and E, click **⋮**, **Delete Job** and **Delete**.
2. Open **Types**, delete **Tiling**.
3. Open the Dashboard and compare it with R0.
4. Open **Stats**, **12 weeks**, and compare it with S0. Click **By member**.

- [ ] Each delete works, and the type **Tiling** deletes without being refused (deleted jobs don't count)
- [ ] The Dashboard's six cards are the same as in R0, and **By type** has no **Tiling** entry
- [ ] **Stats** shows the same **Completed** as in S0, and the same line under **Completed late**
- [ ] In the table Ana's, Luka's and Sara's rows are the same as in S0
- [ ] Luka gets one "Deleted Fit shower screen…" notification and nobody else gets one

## DSH-16 Default stats range and landing project
**As:** Ana (owner) · **Day:** any · **Needs:** DSH-15 done

0. Open **Account settings** and write down Ana's current **Default range** and **Landing project** (they should be 12 weeks and Project list); put those values back at the end.
1. Open **Account settings**. Under **Stats**, set **Default range** to **4 weeks**. Open **Stats** in Residential.
2. Click **26 weeks**, open the **Dashboard**, then **Stats** again.
3. Back in settings set **Default range** to **12 weeks**.
4. Under **Navigation** set **Landing project** to **Specific**, choose **Residential Fit-outs** in **Project**, and type the root address of the app in the address bar.
5. Open **Office Refurbishments** from **Projects**, set **Landing project** to **Last opened** and type the root address again.
6. Set **Landing project** to **Project list** and type the root address again.

- [ ] **Stats** opens on **4 weeks**
- [ ] After picking **26 weeks** on the page and leaving, **Stats** opens on **4 weeks** again, and the setting still says 4 weeks
- [ ] **Default range** is back at 12 weeks
- [ ] With **Specific**, the root address takes Ana to the Residential Dashboard (her **Default project page**)
- [ ] With **Last opened**, it takes her to the Office Refurbishments Dashboard
- [ ] With **Project list**, it takes her to **Projects**
- [ ] **Default range** and **Landing project** are back to the values written down in step 0

## DSH-17 Add-on off: Pine & Pixel Studio
**As:** Petra (owner, Pine & Pixel Studio) · **Day:** any · **Needs:** ONB-31 done (project Brand refresh for Juniper Cafe, no add-ons)

1. Open **Brand refresh for Juniper Cafe** from **Projects** and look at the page and the top bar.
2. Click **Dashboard** in the top bar, then **Stats**.
3. Click **Upgrade in org settings**.
4. Open the user menu.

- [ ] Opening the project lands on the Dashboard, which shows "Dashboard is not included in your plan" and no numbers
- [ ] **Dashboard** and **Stats** show in the top bar with a lock, at the end after **Settings**
- [ ] **Stats** shows "Stats is not included in your plan" and no tiles
- [ ] **Upgrade in org settings** opens **Organisation Settings**
- [ ] The user menu has **Overview** (Petra is the org owner), and the page lists only **Brand refresh for Juniper Cafe** in **Project Directory**, with no Brightline project, and **Pending Approvals** shows its heading with "All caught up — no pending approvals across your projects." and no request of Brightline
- [ ] No data of Brightline shows anywhere

## Not manual
- The dashboard's numbers, lists and ordering (counts per status, blocked oldest first, overdue soonest first, a job due today not overdue and one due two days ago overdue, completed jobs left out of Overdue, a Blocked job past its deadline in both lists, decided approvals left out of pending, the type breakdown grouped and sorted by count): covered by `DashboardIntegrationTest` (`getDashboard_shouldReturnCorrectSummaryCounts`, `getDashboard_shouldIncludeBlockedJobs_inBlockedJobsList`, `getDashboard_shouldNotIncludeCompletedJobs_inOverdueList`, `getDashboard_shouldNotIncludeJobDueToday_butIncludeJobDueTwoDaysAgo_inOverdueList`, `getDashboard_shouldNotIncludeDecidedApprovals_inPendingList`, `getDashboard_shouldReturnTypeBreakdown_groupedAndSorted`) and `DashboardServiceTest` (`get_shouldReturnBlockedJobs_sortedByBlockedAtAsc`, `get_shouldReturnOverdueJobs_sortedByDeadlineAsc`, `get_shouldIncludeBlockedJobs_inOverdueCount_whenPastDeadline`).
- The dashboard for a Member scoped to their own jobs with no pending approvals, 403 for a non-member, 401 without a login: covered by `DashboardIntegrationTest` (`getDashboard_shouldScopeDashboard_toMembersOwnJobs`, `getDashboard_shouldReturnEmptyPendingApprovals_forMember`, `getDashboard_shouldReturn403_forNonMember`, `getDashboard_shouldReturn401_withoutAuth`). DSH-08 and DSH-09 check the screens.
- The empty states ("No jobs yet", "All clear"), the completed-project banner and the Markdown project description, five pending approvals at most with **→ View all**, a failed load with a working retry, and the add-on off with no request sent: covered by `frontend/cypress/e2e/dashboard/dashboard.cy.ts` ("zero jobs shows a distinct "no jobs yet" empty state; jobs with all sections empty shows the "all clear" empty state", "a COMPLETED project shows the completed banner, and a markdown project description renders formatted", "the pending approvals section shows up to 5 with a "view all" link past that, is OWNER/ADMIN-only, and is entirely absent (not empty) for a MEMBER", "a failed dashboard load shows PageError with a working retry", "the DASHBOARD add-on off shows an upgrade card and never fetches").
- Stats arithmetic (lead, cycle and blocked time from the history, reopened jobs counted once, deleted jobs left out, the late rule, weeks bucketed in the viewer's zone around midnight and DST, empty weeks filled, jobs never In Progress): covered by `StatsIntegrationTest` (`stats_shouldComputeLeadCycleAndBlocked_forKnownHistory`, `stats_shouldHandleReopenedJobs`, `stats_shouldExcludeSoftDeletedJobs`, `stats_shouldBucketWeeksInViewerZone_aroundMidnightAndDst`, `stats_shouldCountLate_onlyWhenCompletedAfterDeadlineDay`, `stats_shouldSkipCycleTime_forJobNeverInProgress`) and `StatsServiceTest` (`getProjectStats_shouldAverageLeadCycleAndBlocked`, `getProjectStats_shouldFillEmptyWeeks`, `getProjectStats_shouldReturnNullLateRate_whenNoDeadlines`).
- Invalid `weeks` or time zone (400, checked before membership), a Member asking for another member's stats (403), a non-member (403), an unknown project (404), no Dashboard add-on (403), no login (401), the friendly project ID in the address: covered by `StatsIntegrationTest` (`getStats_shouldReturn400_forInvalidWeeks`, `getStats_shouldReturn400_forInvalidTimezone`, `getStats_shouldReturn400BeforeMembershipCheck_forInvalidParams`, `getStats_shouldReturn403_forMemberWithOtherId`, `getStats_shouldReturn403_forNonMember`, `getStats_shouldReturn404_forUnknownProject`, `getStats_shouldReturn403_withoutDashboardAddon`, `getStats_shouldReturn401_withoutAuth`, `getStats_shouldResolveFriendlyProjectId`).
- The Stats empty state (zero or dash tiles and empty weeks), a failed stats load with retry, the locked Stats tab: covered by `frontend/cypress/e2e/stats/stats-page.cy.ts` ("a project with no completions in range shows the empty state, zero/"—" tiles and the empty weeks", "a failed stats load shows PageError with a working retry", "an org without the DASHBOARD add-on sees a locked Stats tab and an upgrade card, and never fetches").
- The project directory API (all projects of the organisation, fewest members first, 403 for a plain Member, 404 for a caller outside the organisation, 401 without a login): covered by `ProjectDirectoryIntegrationTest` (`getDirectory_shouldReturnAllOrgProjects_sortedByMemberCountAscending`, `getDirectory_shouldReturn403_forPlainMember`, `getDirectory_shouldReturn404_whenCallerNotInOrg`, `getDirectory_shouldReturn401_withoutAuth`). DSH-06 and DSH-17 check the screen.
- The Overview and the Approvals page refreshing on new requests and when a decided request leaves: covered by `frontend/cypress/e2e/approvals/queue-refresh.cy.ts` ("a new request shows on the open Overview without a reload"). DSH-05 checks it by hand.
- The stats range, Overview approvals and landing preferences saved to the server: covered by `frontend/cypress/e2e/user-settings/stats-overview-preferences.cy.ts` ("Stats opens on the chosen default range, and changing it there keeps the preference", "an Owner hides the Overview approvals while the nav badge stays; a Member does not see the setting") and the saved deep link beating the landing project by `frontend/cypress/e2e/user-settings/landing-project.cy.ts` ("a saved deep link still wins over the landing project"). The settings page itself is in `11-account.md`.
- Ten requests at first on the Overview with a **Show … more** button: needs more than ten pending requests across projects, which the test world never has.

## Findings
