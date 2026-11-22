# Recurring schedules

Last run: 2026-11-22, Jovan (in progress)
Guide: /guide/recurring-jobs

Recurring schedules create a job from a template on a timetable, hand it to people in turn, and keep
a list of runs that were missed. This file covers creating, previewing, editing, pausing, resuming
and deleting schedules, the jobs the poller creates over several days, the missed-runs list, the
add-on gating and the role limits. The world it uses is built by `01-onboarding.md` (see the
[README](README.md#test-world)).

**What the app does that shapes this file:**

- **The poller runs every minute** and creates a job when a schedule's next run is due. After a
  run time passes, allow a couple of minutes, then refresh.
- **The smallest interval is one hour.** A cron that fires more often is refused. So the "per-minute
  schedule just before a deploy" idea from the README doesn't work: a run is only recorded as
  missed after the app was away for more than a full interval. SCH-17 produces missed runs by
  pausing a schedule over several days and resuming it. SCH-20 is the optional variant with the
  app stopped for over an hour.
- **Only the project's Owners and Admins manage schedules.** Members see the list read-only.
- **Job templates and Recurring scheduling are add-ons.** Brightline has both. Pine & Pixel has
  neither.
- **Times.** All times are UTC (the timezone field is set to `UTC` below). The page shows times in
  your own timezone and format, so compare the *moment*, not the clock digits. Day 1 is today in
  UTC, "Day 3" is today + 2 days. Near midnight UTC, work the dates out from the UTC clock.

**Names used here** (other files refer to them, so keep them exactly):

| Thing | Name |
|-------|------|
| Template for the daily schedule | **Site safety check** |
| Template for the weekly schedule | **Toolbox talk** |
| Daily schedule (Ivana, then Sara in turn) | **Daily site safety check** |
| Weekly schedule, no assignee | **Weekly toolbox talk** |
| Short-lived schedule used for edits and delete | **Monthly fire drill log** |
| Job title made by the daily schedule | `Site safety check for Residential Fit-outs - <run date>` |
| Job title made by the weekly schedule | `Toolbox talk week <ISO week number>` |

All schedules live in **Residential Fit-outs** (Ana owner, Nikola admin, Ivana, Luka and Sara
members) unless a scenario says otherwise. The English UI is used throughout. Luka is only a project
member here and is not played, because he uses the Serbian UI.

**Setup.** `01-onboarding.md` is done through ONB-26 and ONB-31. Create the daily schedule
(SCH-05) after 07:00 UTC on day 1, so its first run is the 07:00 UTC run on day 2. If you create it
before 07:00 UTC, the first run is today and every "Day 2" check below moves one day earlier.

## SCH-01 Create the two templates the schedules use
**As:** Ana (Residential owner) · **Day:** 1 · **Needs:** ONB-22 done. The Templates page is covered in depth by `07-planning.md`; this only sets up the data.

1. Open **Residential Fit-outs**, **Templates**, click **+ New Template**.
2. Name **Site safety check**, Title template `Site safety check for {{project}} - {{date}}`, Description template "Walk every active site, check exits and tool storage, photograph anything unsafe.", Client "Brightline internal", Priority **Medium**, Deadline offset (days) `1`. Click **Create**.
3. Click **+ New Template** again. Name **Toolbox talk**, Title template `Toolbox talk week {{week}}`, Description template "Ten-minute talk with the crew before the first job of the week.", Priority **Low**, leave the deadline offset empty. Click **Create**.

- [x] **Site safety check** is listed with a "+1d deadline" badge
- [x] **Toolbox talk** is listed with no deadline badge
- [x] Both show "Used 0×"

## SCH-02 The Schedules page starts empty and the form opens with sensible defaults
**As:** Ana (Residential owner) · **Day:** 1 · **Needs:** SCH-01 done

1. Open **Residential Fit-outs**, click **Schedules** in the top bar.
2. Click **+ New Schedule**.
3. Open the **Template** menu.
4. Look at the **Schedule** section and the **Timezone** field.

- [x] The page is titled **Schedules** and says "No schedules yet. Create a recurring schedule to automatically create jobs from templates."
- [x] The page has **+ New Schedule** and **Create first schedule**
- [x] The **New Schedule** window opens with **Weekly** chosen, "Every" **Monday** "at" 09:00 and a line in words describing it
- [x] The **Template** menu lists **Site safety check** and **Toolbox talk**
- [x] **Next occurrences** lists five dates, all Mondays at 09:00, and **Timezone** starts as your browser's timezone
- [x] **Cancel** closes the window without creating anything

## SCH-03 The preview follows the choices
**As:** Ana (Residential owner) · **Day:** 1 · **Needs:** SCH-02 done

1. Click **+ New Schedule**. Set **Timezone** to `UTC`.
2. Click **Daily**, set the time to 07:00.
3. Click **Monthly**, set **Day** to 1, time 09:00.
4. Click **Advanced**, replace the text with `0 0 8 * * MON-FRI`.
5. Replace the text with `0 0 8 * *` (five fields only), wait a second.
6. Click **Cancel**.

- [x] **Daily** shows a line in words saying every day at 07:00 and five consecutive days in **Next occurrences**, each at 07:00 UTC
- [x] **Monthly** shows the 1st of the next five months at 09:00 UTC
- [x] **Advanced** lists five dates, Monday to Friday only, at 08:00 UTC, and the line in words says the same
- [x] With only five fields, **Next occurrences** disappears (the form doesn't ask for a preview) and the form still opens and closes normally
- [x] The helper text under the **Advanced** field says it is a 6-field cron: sec min hour dom month dow

## SCH-04 Bad input is refused and nothing is created
**As:** Ana (Residential owner) · **Day:** 1 · **Needs:** SCH-01 done

1. Click **+ New Schedule**, Name "Trial", Template **Site safety check**, **Timezone** `UTC`.
2. Click **Advanced**, type `0 */5 * * * *` (every five minutes), click **Create schedule**.
3. Type `0 0 25 * * *` (hour 25), click **Create schedule**.
4. Click **Weekly**, set **Timezone** to `Mars/Olympus`, click **Create schedule**.
5. Set **Timezone** back to `UTC`, set **Template** to "Select a template…", click **Create schedule**.
6. Clear **Name**, pick a template, click **Create schedule**.
7. Click **Cancel**.

- [x] Step 2 shows "Cron expression must have a minimum interval of 1 hour" in the window and the window stays open
- [x] Step 3 shows a message starting "Invalid cron expression:" and the window stays open
- [x] Step 4 shows "Invalid timezone: Mars/Olympus" and the window stays open
- [x] Step 5 doesn't submit: the browser asks for a template
- [x] Step 6 doesn't submit: the browser asks for a name
- [x] After **Cancel** the Schedules page still shows the empty state

## SCH-05 Create the daily schedule with two people in rotation
**As:** Ana (Residential owner) · **Day:** 1, after 07:00 UTC · **Needs:** SCH-01 done

1. Click **+ New Schedule**. Name **Daily site safety check**, Template **Site safety check**.
2. Click **Daily**, set the time to 07:00, **Timezone** `UTC`.
3. Under **Assignee rotation**, click the field, type "Sara", pick Sara. Then add Ivana the same way.
4. Click the up arrow (▲) on Ivana's row.
5. Leave **Pause until** and **Expires at** empty and click **Create schedule**.

- [x] Sara is listed as #1 and Ivana as #2 after step 3, and Ivana is #1 and Sara #2 after step 4
- [x] The first row's up arrow and the last row's down arrow are greyed out
- [x] The window closes and the page lists **Daily site safety check** with an **Active** label
- [x] The row shows "Template: Site safety check" and a line in words saying it runs every day at 07:00
- [x] **Next:** shows the next 07:00 UTC moment, the same one the preview listed first
- [x] The row shows two circles, "I" then "S" (Ivana first)
- [x] The row has **Pause**, **Edit** and **Delete**

## SCH-06 Create the weekly schedule with no assignee
**As:** Ana (Residential owner) · **Day:** 1 · **Needs:** SCH-05 done

1. Click **+ New Schedule**. Name **Weekly toolbox talk**, Template **Toolbox talk**.
2. Click **Weekly**, choose the weekday of day 3 (today + 2 days), time 07:00, **Timezone** `UTC`.
3. Don't add anyone under **Assignee rotation**. Click **Create schedule**.

- [x] The schedule is listed as **Active** with "Template: Toolbox talk"
- [x] The row shows no assignee circles
- [x] **Next:** is day 3 at 07:00 UTC
- [x] The page now lists two schedules

## SCH-07 Create a schedule that starts paused
**As:** Ana (Residential owner) · **Day:** 1 · **Needs:** SCH-06 done

1. Click **+ New Schedule**. Name **Monthly fire drill log**, Template **Toolbox talk**.
2. Click **Monthly**, **Day** 1, time 09:00, **Timezone** `UTC`.
3. Add Sara under **Assignee rotation**.
4. Set **Pause until** to a date three months from now, leave **Expires at** empty, click **Create schedule**.

- [x] The schedule is listed with a **Paused** label, not **Active**
- [x] The row has **Resume** instead of **Pause**
- [x] The other two schedules are still **Active**

## SCH-08 Members can look but not manage, and the role is per project
**As:** Sara (Residential member), then Ivana (Residential member, Office admin) · **Day:** 1 · **Needs:** SCH-07 done

1. As Sara, open Residential, click **Schedules**.
2. As Ivana, open Residential, **Schedules**.
3. As Ivana, open **Office Refurbishments**, **Schedules**.

- [x] Sara sees all three schedules with their status, template, timing, **Next:** and circles
- [x] Sara has no **+ New Schedule**, **Pause**, **Resume**, **Edit** or **Delete** anywhere on the page
- [x] Ivana sees the same read-only page in Residential
- [x] In Office Refurbishments Ivana (admin there) sees the empty state with **+ New Schedule**
- [x] Sara and Ivana still see **Schedules** in the top bar of Residential (it is not hidden from members)

## SCH-09 Without the add-on the schedules are locked
**As:** Petra (owner of Pine & Pixel Studio, no add-ons), then Ana · **Day:** 1 · **Needs:** ONB-31 done (Petra's project **Brand refresh for Juniper Cafe** exists)

1. As Petra, open **Brand refresh for Juniper Cafe** and look at the top bar.
2. Click the locked **Schedules** item.
3. Click **Upgrade in org settings**.
4. Open the project's **Templates** page.
5. As Ana, open Residential and look at the top bar.

- [x] The top bar shows **Schedules** with a lock next to it
- [x] The page says "Recurring Scheduling is not included in your plan" with "Add this feature to your subscription to unlock it for your team." and no **+ New Schedule**
- [x] **Upgrade in org settings** opens **Organisation Settings**
- [x] **Templates** is locked the same way, so there is no **Schedule** button to start from
- [x] Ana's top bar shows **Schedules** without a lock

## SCH-10 Pause and resume
**As:** Nikola (Residential admin) · **Day:** 1 · **Needs:** SCH-07 done

1. Open Residential, **Schedules**. On **Weekly toolbox talk** click **Pause**.
2. In the window, click **Custom date**.
3. Choose **1 day** and click **Pause**.
4. Click **Resume** on **Weekly toolbox talk**.
5. On **Monthly fire drill log** (already paused) click **Resume**.
6. Click **Pause** on **Monthly fire drill log**, choose **Indefinitely**, click **Pause**.

- [x] The window is titled `Pause "Weekly toolbox talk"` and offers **1 day**, **1 week**, **1 month**, **Custom date** and **Indefinitely**
- [x] With **Custom date** chosen and no date entered, the **Pause** button in the window is disabled
- [x] After step 3 the row shows **Paused** and **Resume**
- [x] After step 4 the row is **Active** again with **Pause**
- [x] After step 5 **Monthly fire drill log** is **Active**
- [x] After step 6 it is **Paused** again and stays so after a reload
- [x] Nikola, as an admin, could do all of this without an error message

## SCH-11 Edit schedules
**As:** Nikola (Residential admin) · **Day:** 1 · **Needs:** SCH-10 done. Known bug JOB-405 (see the expected-to-fail box): the edit form doesn't send the schedule's current pause or end dates, so saving an edit may clear them.

1. On **Daily site safety check** click **Edit**, look at the form, click **Cancel**.
2. On **Weekly toolbox talk** click **Edit**. Change the name to **Weekly toolbox talk (all sites)** and click **Save changes**.
3. On the same row click **Edit** again and look at the form, then change the name back to **Weekly toolbox talk** and save.
4. On **Monthly fire drill log** (paused indefinitely in SCH-10) click **Edit**, change only the name to **Monthly fire drill log (head office)**, click **Save changes**.
5. Click **Edit** on it again, remove Sara with the × on her row and click **Save changes**.

- [x] The window for the daily schedule is titled **Edit Schedule** with the name, the template **Site safety check**, the timing 07:00 UTC (shown as **Daily** at 07:00, or as the cron `0 0 7 * * *` on **Advanced**) and the two people in order (Ivana #1, Sara #2) already filled in
- [x] The weekly schedule's window shows **Weekly**, the day-3 weekday, 07:00 and `UTC` already filled in
- [x] After step 2 the row shows the new name and is still **Active**, and **Next:** is unchanged
- [ ] After step 4 the row shows the new name and is still **Paused** — ❌ JOB-405 (fix in PR #598)
- [x] After step 5 the row shows **No assignees** and no circles
- [x] **Resume** is shown on that row

## SCH-12 Expiry and delete
**As:** Ana (Residential owner) · **Day:** 1 · **Needs:** SCH-11 done

1. On **Monthly fire drill log (head office)** click **Edit**, add Sara, set **Expires at** to yesterday 09:00, click **Save changes**.
2. Click **Delete** on that row, then **Cancel**.
3. Click **Delete** again and read the window, then click **Delete** in it.

- [x] The row shows **Expired**
- [x] After **Cancel** the schedule is still listed
- [x] The window is titled **Delete Schedule** and says `Delete "Monthly fire drill log (head office)"? Jobs already created by this schedule will not be deleted.`
- [x] After the second **Delete** the row is gone and the other two schedules are untouched and **Active**

## SCH-13 Day 2: the daily schedule created a job
**As:** Ana, Ivana, Sara · **Day:** 2, after 07:00 UTC · **Needs:** SCH-05 done on day 1 after 07:00 UTC, and the poller has had two minutes. The assigned-to notification for this job is checked in NTF-27.

1. As Ana, open Residential, **Jobs**, and look for the new job.
2. Open it.
3. As Ana, open **Schedules**, then **Templates**.
4. As Ivana, open Residential, **Jobs**. As Sara, do the same.

- [ ] The job `Site safety check for Residential Fit-outs - <day 2 date>` exists with the status **New**, priority **Medium**, client "Brightline internal" and the description from the template
- [ ] It is assigned to Ivana (the first in the rotation)
- [ ] Its deadline is the day after the run date
- [ ] The job page shows "Created by schedule" with the link **Daily site safety check**, which opens the Schedules page
- [ ] **Daily site safety check** now shows **Next:** day 3 at 07:00 UTC and is still **Active**
- [ ] **Site safety check** on the Templates page shows "Used 1×"
- [ ] Ivana sees the job in her list and Sara does not

## SCH-14 Day 3: the weekly schedule runs and the rotation moves to Sara
**As:** Ana, Nikola, Ivana, Sara · **Day:** 3, after 07:00 UTC · **Needs:** SCH-13 done. The weekly schedule was set to day 3 in SCH-06.

1. As Ana, open Residential, **Jobs**, look for the new jobs.
2. As Nikola, open **Jobs** and **Schedules**.
3. As Ivana, then Sara, open **Jobs**.

- [ ] `Toolbox talk week <week number>` exists as **New** with priority **Low**, no deadline and no assignee
- [ ] A second `Site safety check for Residential Fit-outs - <day 3 date>` exists, assigned to Sara
- [ ] Nikola sees both new jobs
- [ ] **Weekly toolbox talk** shows **Next:** one week later at 07:00 UTC
- [ ] Sara's list has the day-3 safety check job and not the toolbox talk
- [ ] Ivana's list has neither new job (her day-2 job is still there)
- [ ] The toolbox talk job's page says "Created by schedule" with **Weekly toolbox talk**

## SCH-15 Day 4: the third run, then pause the daily schedule
**As:** Ivana, then Nikola · **Day:** 4, after 07:00 UTC · **Needs:** SCH-14 done

1. As Ivana, open **Jobs** and look for the day-4 safety check job.
2. As Nikola, open **Schedules**. On **Daily site safety check** click **Pause**, choose **Indefinitely**, click **Pause**.

- [ ] `Site safety check for Residential Fit-outs - <day 4 date>` exists and is assigned to Ivana again (the rotation wrapped round)
- [ ] After step 2 **Daily site safety check** shows **Paused** and **Resume**
- [ ] **Weekly toolbox talk** is still **Active**

## SCH-16 Day 5: a paused schedule creates nothing
**As:** Ana · **Day:** 5, after 07:00 UTC and two minutes · **Needs:** SCH-15 done

1. Open Residential, **Jobs** and search for "Site safety check".
2. Open **Schedules**.

- [ ] The list has the day-2, day-3 and day-4 jobs and none for day 5
- [ ] **Daily site safety check** is still **Paused**
- [ ] **Weekly toolbox talk** is **Active**
- [ ] Its **Next:** is still the day-3 run plus one week

## SCH-17 Resume after a long pause: runs that were skipped are listed as missed
**As:** Ana · **Day:** 7, after 07:00 UTC and before 07:00 UTC on day 8 · **Needs:** SCH-16 done. The schedule was paused on day 4 after its run, so its next run was day 5 07:00 UTC. Resume on day 7 after 07:00 UTC, three ticks later (day 5, 6 and 7): the poller doesn't record the first skipped tick (day 5), only the ones after it, so the list gets exactly two entries (day 6 and day 7, 07:00 UTC). Resuming later gives more entries and breaks the counts in SCH-18. This is also what makes the `SCHEDULE_RUN_MISSED` notifications of NTF-24, so do it before NTF-24.

1. Open Residential, **Schedules**. On **Daily site safety check** click **Resume**.
2. Wait two minutes and reload the page.
3. Open **Jobs** and search for "Site safety check".

- [ ] After **Resume** the row says **Active**
- [ ] The row shows a banner with "2 missed runs" under the schedule
- [ ] **Next:** shows the next 07:00 UTC moment in the future
- [ ] No new `Site safety check …` job was made for the time the schedule sat paused: the list still has the day-2, day-3 and day-4 jobs only
- [ ] Ana (Owner) has two "Schedule \"Daily site safety check\" missed a run in Residential Fit-outs" entries in her notification centre, one per missed run (recipients and text are checked in NTF-24)

## SCH-18 The missed-runs list
**As:** Sara (member), then Ana (Owner) · **Day:** 7 · **Needs:** SCH-17 done

1. As Sara, open Residential, **Schedules**, click the missed-runs banner.
2. As Ana, open **Schedules**, click the banner to open the list.
3. On the first (oldest) entry click **Create job**.
4. Click **View** in the green box.
5. Back on **Schedules**, open the banner again and click **Dismiss** on the remaining entry.

- [ ] Sara can open the banner and sees the missed dates, but has no **Create job**, **Dismiss** or **Dismiss all**
- [ ] Ana's list has two rows, oldest first (day 6, then day 7), each with its date, each with **Create job** and **Dismiss**, and a **Dismiss all** button above them (shown only while there is more than one)
- [ ] **Create job** shows a green box "Job created:" with the new job's ID and its title `Site safety check for Residential Fit-outs - <day 6 date>`
- [ ] **View** opens that job, which is **New** and shows "Created by schedule" with **Daily site safety check**
- [ ] The entry you used is gone from the list, the banner now says "1 missed run" and **Dismiss all** is no longer shown
- [ ] After **Dismiss** on the last entry the list is empty and no job was made for it, and after a reload the banner is gone

## SCH-19 Deleting a schedule keeps its jobs
**As:** Ana (Residential owner) · **Day:** 7 (before the weekly schedule's second run) · **Needs:** SCH-18 done

1. Open **Templates** and note the "Used" count on **Toolbox talk**.
2. Open **Schedules**. On **Weekly toolbox talk** click **Delete** and confirm.
3. Open **Jobs** and search for "Toolbox talk".
4. Open **Templates** again.

- [ ] **Weekly toolbox talk** is gone from the list
- [ ] **Daily site safety check** is still listed
- [ ] The job `Toolbox talk week <week number>` from day 3 is still in the job list, unchanged
- [ ] **Toolbox talk** on the Templates page shows the same "Used" count as before the delete

## SCH-20 Missed run after the app was stopped (optional)
**As:** Nikola, then Ana, Ivana · **Day:** 7 or later · **Needs:** the developer can stop and start the prod backend. Skip this if that isn't practical: SCH-17 already covers the list and the notification. This is the only scenario where the app itself was down, and it needs the app stopped for **more than one hour**, because a missed run is only recorded when the next run time is also past. A normal deploy of a few minutes won't produce one.

1. As Nikola, open **Schedules**, click **+ New Schedule**. Name **Hourly yard check**, Template **Toolbox talk**, **Advanced**, cron `0 0 * * * *` (every hour on the hour), **Timezone** `UTC`. Click **Create schedule**.
2. Just before the next top of the hour, the developer stops the backend. Leave it stopped until a little past the top of the hour after that (about 65 minutes).
3. Start the backend again, wait two minutes.
4. As Nikola, open **Schedules** and click the banner on **Hourly yard check**.
5. As Ana, then Ivana (a member), check the notification centre.
6. As Nikola, delete **Hourly yard check**.

- [ ] **Hourly yard check** was created as **Active**, with **Next:** at the next top of the hour
- [ ] After the restart the row has a banner with at least "1 missed run", and its date is on the hour, in the past
- [ ] No job was made for the missed time
- [ ] Ana and Nikola have a "Schedule … missed a run" entry (details in NTF-24) and Ivana has none
- [ ] **Hourly yard check** is gone after step 6

## Not manual
- Member, non-member and wrong-project calls to the schedule endpoints are refused (403 or 404), including an assignee from another organisation: covered by `RecurringScheduleIntegrationTest` and `ScheduleMissedRunIntegrationTest`.
- The add-on check on the schedule and missed-run endpoints: no test calls them without `RECURRING_SCHEDULING` (`RequiresAddonAspectTest` only tests the aspect in general, with mocks), and `SchedulePreviewController` has no add-on check. SCH-09 (Petra) is the only check of the gating and it covers the page and top bar only. See the follow-ups in PR #594.
- Dismiss all as an API call, materialising a missed run with a bad timezone, and the 404 for an unknown missed run: covered by `ScheduleMissedRunIntegrationTest`.
- The poller creating, skipping and recording runs (rotation, deadline offset, priority, template soft-deleted, invalid timezone, no future occurrence): covered by `SchedulerPollerTest`, `SchedulerPollerIntegrationTest` and `ScheduleJobCreatorTest`.
- A schedule going to **No assignees** when the last assignee is removed from the project: covered by `RecurringScheduleIntegrationTest` (`removeMember …`) and `ProjectMemberServiceTest`.
- Status labels computed from the pause and end dates: covered by `RecurringScheduleResponseTest`.
- `SCHEDULE_RUN_MISSED` fan-out and mutes: covered by `NotificationScheduledEventsIntegrationTest` and `NotificationServiceScheduledEventsTest`.
- The preview endpoint's 400s (blank or invalid cron, invalid timezone) and the empty list for a cron with no future run: covered by `RecurringScheduleIntegrationTest` (`previewCron …`).

## Findings

- SCH-11: JOB-405 (Bug) editing a schedule un-pauses it and clears its end date: the edit form doesn't send the current pause or end dates and `RecurringScheduleService.update` writes them as null.
