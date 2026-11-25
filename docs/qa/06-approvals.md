# Approvals

Last run: 2026-11-25, Jovan
Guide: /guide/job-approval, /guide/project-settings, /guide/roles-permissions

This file covers approvals end to end: **manual approvals** (a person asks an Owner or Admin for a
decision on a job), **completion approvals** (the project gate "Require approval to complete jobs":
a Member's Complete becomes a request), the **Approvals** page of a project (the queue), who may
request, decide and withdraw per role, and the **Approvals add-on** gating. It uses the world built
by `01-onboarding.md` (see the [README](README.md#test-world)): Brightline Renovations with Ana,
Nikola, Ivana, Luka and Sara, the projects Residential Fit-outs (gate **on**) and Office
Refurbishments (gate **off**), and Petra in Pine & Pixel Studio, which has no add-ons. Run
`01-onboarding.md` first. Rules in [ADR-0010](../dev/decisions/0010-approval-model.md),
[ADR-0016](../dev/decisions/0016-approvals-screens.md),
[ADR-0046](../dev/decisions/0046-cross-project-approval-queue.md) and
[ADR-0052](../dev/decisions/0052-gated-completion-approval.md).

**What the screens do** (so the checks below make sense):

- **Who can request.** A manual request: the project Owners and Admins on any job, a Member only on
  a job assigned to them (button **Request Approval** on the job's action bar). Its description is
  required, up to 2000 characters. A request never changes the job's status.
- **Who can decide.** Only project Owners and Admins, with **Approve** or **Reject** and an optional
  comment. A Member sees no **Approve** or **Reject** anywhere, and no **Approvals** link in the top
  bar. `/overview` is for organisation Owners and Admins only.
- **The gate** (Residential only). A Member who clicks Complete on an In Progress job gets a
  completion request instead: the job stays In Progress, a banner "Completion requested by …" shows,
  and the button reads **Approval requested** and is greyed out. Owners and Admins click **Complete**
  and complete directly. **Approve** on a completion request completes the job in the same step
  (the history shows the approver); **Reject** leaves it In Progress. Approving a manual request
  never completes anything, gate or not.
- **Withdraw.** The requester, and any Owner or Admin, can withdraw a pending completion request
  (**Withdraw**). It becomes **Cancelled** and the job stays In Progress. Manual requests can't be
  withdrawn. An Owner or Admin completing the job directly cancels a pending request by itself ("The
  job was completed directly."), and so does deleting the job.
- **Approved, not applied.** A completion request approved while the job isn't In Progress (for
  example it was Blocked meanwhile) is recorded as **Approved, not applied**: the job is not
  completed, the approver sees a warning, the requester gets a notification.
- **Lists.** A job's **Approvals** section lists its requests newest first, with the label
  **Completion** on completion requests and a status label. The project **Approvals** page lists
  only pending requests, grouped by job, oldest first. `/overview` lists the pending requests of
  every project where you are Owner or Admin.
- **Jobs and notifications from other files.** This file doesn't touch the day-1 jobs of
  `01-onboarding.md` or the working jobs of the other area files. Lists that other files can add
  to say "includes", never an exact count.

**Jobs this file creates.** `APR-01` creates seven jobs, none with a deadline, so no deadline alert
is ever sent for them. Other scenarios call them by short name. Leave them in the state this file
ends with and don't use them in other areas.

| Short name | Title | Project | Assignee | Ends as |
|------------|-------|---------|----------|---------|
| A | Fit new front door at 3 Willow Lane | Residential | Luka | In Progress |
| B | Replaster lounge ceiling at 3 Willow Lane | Residential | Ivana | Completed |
| C | Lay oak flooring at 3 Willow Lane | Residential | Luka | Completed |
| D | Fit stair carpet at 3 Willow Lane | Residential | Sara | Completed |
| E | Repoint chimney at 3 Willow Lane | Residential | Sara | Deleted |
| F | Fit glass partition at Dunmore Dental | Office | Luka | Completed |
| G | Hang fire-rated doors at Dunmore Dental | Office | Luka | Completed |

**Notifications this file sends** (checked in `APR-20`; `09-notifications.md` day 7+ reads every
entry in each centre, so expect these, about the Willow Lane and Dunmore Dental jobs):

| Person | Entries |
|--------|---------|
| Ana | 11 **Approval requested** (8 for completion requests on C, B, B, C, D, D, C, E and 3 for manual requests on A), 1 **Status changed** (C unblocked), 1 **Job blocked** (C) |
| Nikola | 13 **Approval requested** (11 as above, plus F's manual request and G's completion request in Office), 12 **Status changed** (A to E started, B completed, D completed, C unblocked, C completed, F started, F completed, G started), 1 **Job blocked** (C) |
| Ivana | 1 **Job assigned** (B), 2 **Approval requested** (F manual, G completion), 2 **Approval decided** (B rejected, B approved), 6 **Status changed** (B started, B completed, F started, F completed, G started, G completed) |
| Luka | 4 **Job assigned** (A, C, F, G), 7 **Approval decided** (C not applied, C approved, A approved, A rejected, A approved, F approved, G approved), 4 **Status changed** (A started, C started, C completed, G completed) |
| Sara | 2 **Job assigned** (D, E), 2 **Approval decided** (D withdrawn, D completed directly), 3 **Status changed** (D started, E started, D completed), 1 **Job deleted** (E) |
| Petra | none |

**Luka's language.** From ONB-33 Luka uses the Serbian UI. Where Luka reads a heading, label,
button or notification, the quoted English text is the English wording: read the Serbian
equivalent and tick when it means the same. Checks of exact English wording are played as Ana,
Nikola, Ivana or Sara. `APR-19` judges the Serbian text itself.

**Setup.** One browser profile per persona, as in `01-onboarding.md`. Run the scenarios in order
in one or two sittings: some rely on a request an earlier one left pending. Every scenario runs on
any day after ONB-33, nothing depends on the calendar.

## APR-01 Create and start the working jobs
**As:** Ana (Residential owner), then Nikola (Office owner) · **Day:** any, after ONB-33 · **Needs:** `01-onboarding.md` done, Residential's gate on and Office's gate off

1. As Ana, in Residential Fit-outs, **Jobs**, **+ New Job**, create these jobs. Client "Gallagher family", **Deadline** empty, **Priority** Medium, no milestone, a one-line description such as "Keep the original skirting boards".

   | Title | Assign to |
   |-------|-----------|
   | Fit new front door at 3 Willow Lane | Luka |
   | Replaster lounge ceiling at 3 Willow Lane | Ivana |
   | Lay oak flooring at 3 Willow Lane | Luka |
   | Fit stair carpet at 3 Willow Lane | Sara |
   | Repoint chimney at 3 Willow Lane | Sara |

2. Open each of the five jobs and click **Start**.
3. As Nikola, in Office Refurbishments, **Jobs**, **+ New Job**, create "Fit glass partition at Dunmore Dental" and "Hang fire-rated doors at Dunmore Dental", client "Dunmore Dental", **Deadline** empty, **Priority** Medium, both assigned to Luka.

- [x] All seven jobs are created as **New**, each with a friendly ID, and none shows a deadline
- [x] Ana's five jobs are **In Progress** after **Start**
- [x] Luka, Ivana and Sara get "Assigned you to …" notifications for their jobs: Luka four (A, C, F, G), Ivana one (B), Sara two (D, E)
- [x] The Approvals section of a new job says "No approval requests."

## APR-02 A member asks to complete a job
**As:** Luka (member, assignee of C), then Ana · **Day:** any, after ONB-33 · **Needs:** APR-01 done. Ana has the Residential **Jobs** page open

1. As Luka, open C and look at the action bar.
2. Click **Request completion**. Read the window. Type three spaces in **Message**, then type over them "Flooring laid, sanded and oiled, ready for sign-off." and look under the box.
3. Click **Cancel**, then click **Request completion** on the bar again and click **Request completion** in the window.
4. Look at the top of the job, the action bar, and the **Approvals** section (open it if it is collapsed).
5. Open Residential **Jobs** and look at the row of C.
6. As Ana, without reloading, look at the bell.

- [x] C's bar offers **Request completion**, **Block** and **Request Approval**, and no **Complete**
- [x] The window explains that an Owner or Admin must approve and that the job stays In Progress until then, and **Message** is marked optional
- [x] A counter under the box shows the number of characters out of 2000 and goes up as you type
- [x] **Cancel** creates nothing: C shows no banner afterwards
- [x] After sending, a toast says "Completion requested" and C is still **In Progress**
- [x] A banner reads "Completion requested by Luka Ilić" with the time, and the message in quotes
- [x] The banner offers Luka **Withdraw** and no **Approve** or **Reject**
- [x] The bar's button reads **Approval requested** and is greyed out
- [x] The Approvals section lists the request with the label **Completion**, the status **Pending**, "Requested by Luka Ilić" and the message, and its title shows "1 pending"
- [x] C's row on the list shows the badge "Awaiting completion approval"
- [x] Ana's bell shows "Requested to complete …" from Luka without a reload
- [x] Luka has no notification for his own request

## APR-03 A completion request without a message
**As:** Ivana (member, assignee of B), then Nikola · **Day:** any, after ONB-33 · **Needs:** APR-01 done

1. As Ivana, open B and click **Request completion**, leave **Message** empty and send.
2. Look at the banner and the Approvals section.
3. As Nikola, open B and look at the banner and the Approvals section.

- [x] B stays **In Progress** and the toast says "Completion requested"
- [x] The banner reads "Completion requested by Ivana Marković" with the time and no quoted message
- [x] The request in the Approvals section reads "Requesting to complete this job" (not an empty line)
- [x] Nikola sees the same banner and request, and the banner offers him **Withdraw**, **Reject** and **Approve**
- [x] Ana and Nikola each get a second "Requested to complete …" entry, this time for B

## APR-04 The approval queue
**As:** Ana (Residential owner), then Nikola · **Day:** any, after ONB-33 · **Needs:** APR-02 and APR-03 done (C and B pending)

1. As Ana, look at the project's top bar, then click **Approvals**.
2. Look at the cards, then click **→ Job** on the first group.
3. Go back, open **Overview** from the main navigation and find **Pending Approvals**.
4. As Nikola, open Residential **Approvals**.

- [x] The top bar shows an **Approvals** link with a badge, and the badge number includes at least 2
- [x] The page title **Approvals** has a badge "N pending" with N at least 2
- [x] The requests are grouped by job, the group for C above the group for B (oldest first)
- [x] Each card has the label **Completion**, the message of the request, "Requested by Luka Ilić" (or Ivana Marković) with the date and time, and **Reject** and **Approve**
- [x] **→ Job** opens that job
- [x] **Pending Approvals** on Overview lists both requests with the job title, the project name "Residential Fit-outs" and the label **Completion**; clicking one opens the job
- [x] Nikola's queue shows the same two requests with the same buttons

## APR-05 Reject a completion request
**As:** Nikola (Residential admin), then Ivana, then Ana · **Day:** any, after ONB-33 · **Needs:** APR-03 done (B pending). Ana has Residential **Approvals** open in her own window, showing B's card

1. As Nikola, open Residential **Approvals**, click **Reject** on the card of B.
2. Read the window. Click **Cancel**, then click **Reject** again, type "Please add the snag photos to a note first." and click **Reject** in the window.
3. As Ivana, open B and look at the banner, the action bar and the Approvals section.
4. As Ana, without reloading, switch back to her Residential **Approvals** window.

- [x] The window is titled "Reject Request" and its comment box is marked optional
- [x] **Cancel** decides nothing: B's card is still in the queue
- [x] After rejecting, B's card disappears from the queue and the badge number goes down by one
- [x] B is still **In Progress** for Ivana, with no banner, and the bar offers **Request completion** again
- [x] The request in the Approvals section shows the status **Rejected**, "Rejected by Nikola Jovanović" with the time, and the comment in quotes
- [x] Ivana gets "Rejected your request on …" from Nikola
- [x] Ana gets no notification for the decision, and B's card leaves her open queue when she returns to the window, without a reload

## APR-06 Approve a completion request: the job completes by itself
**As:** Ivana (member), then Ana (Residential owner), then Nikola · **Day:** any, after ONB-33 · **Needs:** APR-05 done

1. As Ivana, open B, click **Request completion**, type "Snag photos added to the notes." and send.
2. As Ana, open Residential **Approvals**, click **Approve** on the card of B, type "Looks good, thank you." and click **Approve** in the window.
3. Open B and look at its status, the Approvals section and the **Status history** section (open them if collapsed).
4. As Ivana, reload B and look at the action bar.
5. As Nikola, open the bell.

- [x] The window is titled "Approve Request" and its comment box is marked optional
- [x] After approving, B's card is gone from the queue and no warning toast appears
- [x] B is **Completed** without any further click
- [x] The newest request in the Approvals section shows **Approved**, "Approved by Ana Petrović" with the time, and the comment in quotes, and the older one still shows **Rejected**
- [x] The status history ends with the move from In Progress to Completed by "Ana Petrović", not by Ivana
- [x] Ivana's action bar on the Completed B has no buttons
- [x] Ivana gets "Approved completion of …" and a Status changed entry for B
- [x] Nikola gets a Status changed entry for B, and Ana gets none for her own approval

## APR-07 The requester withdraws a completion request
**As:** Luka (member, assignee of C), then Ana · **Day:** any, after ONB-33 · **Needs:** APR-02 done (C pending). Ana has Residential **Approvals** open in her own window, showing C's card

1. As Luka, open C and click **Withdraw** on the banner.
2. Read the window and click **Cancel**. Click **Withdraw** on the banner again and click **Withdraw** in the window.
3. Look at the banner, the action bar and the Approvals section.
4. As Ana, without reloading, switch back to her Residential **Approvals** window, then open the bell.
5. As Luka, click **Request completion**, type "Re-checked the skirting, requesting again." and send.
6. As Ana, look at her Approvals window without reloading.

- [x] The window is titled "Withdraw completion request" and says the job stays In Progress and completion can be requested again
- [x] **Cancel** leaves the request pending: the banner is still there
- [x] After withdrawing, the banner is gone, C is still **In Progress** and the bar offers **Request completion** again
- [x] The request in the Approvals section shows the status **Cancelled** and "Cancelled by Luka Ilić"
- [x] C's card leaves Ana's open queue when she returns to the window, without a reload, and no notification about the withdrawal reaches Ana or Nikola
- [x] After the second request C shows the banner with the new message, and C's card is back in Ana's open queue without a reload

## APR-08 An admin withdraws someone else's request
**As:** Sara (member, assignee of D), then Nikola (Residential admin), then Sara · **Day:** any, after ONB-33 · **Needs:** APR-01 done

1. As Sara, open D, click **Request completion** and send with the message "Carpet fitted, gripper rods checked."
2. As Nikola, open D and look at the banner.
3. Click **Withdraw** and confirm.
4. As Sara, open the bell and then D.

- [x] Nikola's banner for D offers **Withdraw**, **Reject** and **Approve**
- [x] After withdrawing, D is **In Progress** with no banner, and its request shows **Cancelled** with "Cancelled by Nikola Jovanović"
- [x] Sara gets "Cancelled your completion request on …" from Nikola
- [x] Sara's bar on D offers **Request completion** again

## APR-09 Completing directly cancels a pending request
**As:** Sara (member, assignee of D), then Ana (Residential owner), then Sara · **Day:** any, after ONB-33 · **Needs:** APR-08 done

1. As Sara, open D, click **Request completion** and send with the message "Carpet is in and trimmed at the door."
2. As Ana, open D and look at the action bar. Click **Complete** and confirm **Mark Complete**.
3. Look at the Approvals section and Residential **Approvals**.
4. As Sara, open the bell and D.

- [x] Ana's bar on D offers **Complete** (not **Request completion**), because an owner completes directly
- [x] D is **Completed**
- [x] The newest request shows **Cancelled**, "Cancelled by Ana Petrović" and the line "The job was completed directly."
- [x] D's card is gone from the queue
- [x] Sara gets "Cancelled your completion request on …" and a Status changed entry for D, and her bar on D has no buttons

## APR-10 Approved, not applied: the job was blocked meanwhile
**As:** Luka (member, assignee of C), then Nikola (Residential admin), then Luka, then Ana · **Day:** any, after ONB-33 · **Needs:** APR-07 done (C In Progress with a pending request)

1. As Luka, open C. Click **Block**, type **Waiting on flooring adhesive delivery** and click **Block Job**.
2. Look at C's banner and action bar.
3. As Nikola, open Residential **Approvals**, click **Approve** on the card of C and confirm with no comment.
4. Look at the toast, C's status and the Approvals section.
5. As Luka, look at the bell, then click **Unblock** and confirm.
6. Click **Request completion**, send with no message.
7. As Ana, open Residential **Approvals** and approve the card of C.

- [x] C is **Blocked** and the completion banner is still shown, with **Withdraw** for Luka
- [x] Nikola can still approve the request of the Blocked job
- [x] A warning toast says "Approved, but the job wasn't completed because it is Blocked."
- [x] C is still **Blocked** and its request shows an amber **Approved, not applied** with the line "The job wasn't completed because it was Blocked."
- [x] Luka gets "Approved completion of …, but it wasn't completed because it is Blocked"
- [x] After **Unblock** C is **In Progress** with no banner, and the bar offers **Request completion** again
- [x] Ana's approval completes C: C is **Completed**, and the newest request shows **Approved**
- [x] Luka gets "Approved completion of …" and a Status changed entry for C

## APR-11 Deleting a job with a pending request
**As:** Sara (member, assignee of E), then Ana (Residential owner), then Sara · **Day:** any, after ONB-33 · **Needs:** APR-01 done

1. As Sara, open E, click **Request completion** and send with the message "Pointing finished, scaffold can come down."
2. As Ana, look at Residential **Approvals**. Open E, click **⋮**, **Delete Job** and confirm.
3. Look at Residential **Approvals** and the job list.
4. As Sara, open the bell.

- [x] E's card is in Ana's queue before the delete
- [x] After the delete E is gone from the job list and its card is gone from the queue
- [x] Sara gets one "Deleted …" notification for E

## APR-12 A manual approval request
**As:** Luka (member, assignee of A), then Ana, then Nikola · **Day:** any, after ONB-33 · **Needs:** APR-01 done

1. As Luka, open A and click **Request Approval**.
2. Look at **Submit**. Type three spaces, look again, then type "The client wants the upgraded oak door frame, 450 extra. Can we go ahead?" and look under the box.
3. Click **Submit**. Look at the Approvals section and A's status.
4. As Ana, open Residential **Approvals**. As Nikola, open the bell.

- [x] **Submit** is greyed out while **Description** is empty and while it holds only spaces
- [x] A counter under the box shows the number of characters out of 2000
- [x] The request appears in A's Approvals section with the status **Pending**, "Requested by Luka Ilić" and **no** label **Completion**; A is still **In Progress**
- [x] Ana's queue shows A's card without the label **Completion**, with **Reject** and **Approve**
- [x] Ana and Nikola each get "Requested approval on …" from Luka
- [x] Luka sees no **Approve** or **Reject** on his own pending request

## APR-13 What members can't do
**As:** Luka (member), then Sara (member), then Ivana (member in Residential, admin in Office) · **Day:** any, after ONB-33 · **Needs:** APR-12 done (A has pending requests)

1. As Luka, open A and look at its Approvals section and its banner area.
2. As Luka, look at the project's top bar, then type the address of Residential's **Approvals** page (copy it from Ana's browser bar) into the address bar.
3. As Sara, look at Residential's top bar, then open Residential **Approvals** by the same address.
4. As Ivana, look at Residential's top bar, open Residential **Approvals** by the same address, then open Office Refurbishments and look at its top bar.
5. As Ivana, open **Overview** from the address bar (`/overview`).

- [x] Luka's Approvals section on A lists the pending requests with no **Approve** or **Reject** buttons
- [x] Luka's top bar has no **Approvals** link, and the address takes him back to Residential **Jobs**
- [x] Sara's top bar has no **Approvals** link, and the address takes her back to Residential **Jobs**
- [x] Ivana, a member in Residential, has no **Approvals** link there and the address takes her back to Residential **Jobs**
- [x] In Office, where Ivana is an admin, her top bar has the **Approvals** link
- [x] Ivana, a member of the organisation, can't open Overview: she lands on the project list

## APR-14 Approving a manual request does not complete the job
**As:** Nikola (Residential admin), then Luka, then Ana · **Day:** any, after ONB-33 · **Needs:** APR-12 done (Luka's request on A pending, A In Progress, Residential's gate on)

1. As Nikola, open Residential **Approvals**, click **Approve** on Luka's card of A (the one starting "The client wants the upgraded oak door frame"), type "Approved, go ahead with the oak frame." and confirm.
2. Open A and look at its status and the Approvals section.
3. As Luka, open the bell, open A and look at the action bar.
4. As Luka, click **Request Approval**, type "Can we swap the brass handle for matte black?" and submit.
5. As Ana, open Residential **Approvals**, click **Reject** on that card, type "Keep the brass handle, the client chose it." and confirm.
6. As Luka, look at the bell and A's Approvals section.

- [x] After the approval A is still **In Progress**
- [x] The request shows **Approved**, "Approved by Nikola Jovanović" and the comment in quotes, and shows no label **Completion**
- [x] Luka gets "Approved your request on …" from Nikola
- [x] Luka's bar on A still offers **Request completion** and not **Complete**
- [x] Ana's rejection shows **Rejected**, "Rejected by Ana Petrović" and her comment, and A is still **In Progress**
- [x] Luka gets "Rejected your request on …" from Ana
- [x] A's Approvals section lists the two requests newest first: the matte black one (Rejected), then the oak frame one (Approved)

## APR-15 Two admins decide the same request
**As:** Luka, then Ana and Nikola at the same time · **Day:** any, after ONB-33 · **Needs:** APR-14 done. Ana and Nikola each have Residential **Approvals** open

1. As Luka, on A click **Request Approval**, type "Confirm the Thursday delivery slot for the door." and submit.
2. Without reloading, wait until Ana's and Nikola's Residential **Approvals** both show Luka's new card.
3. As Ana, click **Approve** on it and leave the window open, without confirming.
4. As Nikola, click **Approve** on the same card, type "Thursday is fine." and confirm.
5. As Ana, confirm in her window.
6. As Ana, close the window and look at the queue. As Luka, open the bell.

- [x] Luka's new card appears in Ana's and Nikola's open queues without a reload
- [x] Nikola's approval is accepted and the card is gone from his queue
- [x] Ana's confirm shows a red message "This approval was already decided by another user."
- [x] The card is already gone from Ana's queue behind the window, and stays gone after she closes it, without a reload
- [x] The request in A's Approvals section is **Approved** by Nikola Jovanović
- [x] Luka gets one "Approved your request on …" entry for this request, from Nikola

## APR-16 Office Refurbishments: the gate is off
**As:** Luka (member, assignee of F), then Ivana (Office admin), then Nikola (Office owner), then Ana · **Day:** any, after ONB-33 · **Needs:** APR-01 done, Office's gate off

1. As Luka, open F and click **Start**. Look at the action bar.
2. Click **Request Approval**, type "Client asks for frosted glass on the partition. Approve the extra cost?" and submit.
3. As Ivana, open Office **Approvals** and look at the card. Click **Approve**, type "Fine, frosted it is." and confirm.
4. As Nikola, open the bell.
5. As Luka, look at the bell, then click **Complete** and confirm **Mark Complete**.
6. As Ana, open Residential **Approvals** and **Overview**.

- [x] Luka's bar on In Progress F offers **Complete** (not **Request completion**), **Block** and **Request Approval**
- [x] The request shows in Office **Approvals** without the label **Completion**, and Ivana, an admin who isn't the project owner, can approve it
- [x] Nikola and Ivana each get "Requested approval on …" from Luka, and Ana gets nothing about F
- [x] Luka gets "Approved your request on …" from Ivana
- [x] **Complete** asks for a confirmation and completes F at once: no banner, no **Awaiting completion approval** badge on the list
- [x] None of F's requests appears in Residential's queue or on Ana's Overview

## APR-17 Turning the gate on and off
**As:** Nikola (Office owner), then Luka (member, assignee of G), then Nikola · **Day:** any, after ONB-33 · **Needs:** APR-01 done, Office's gate off. Finish this scenario in one sitting, because with Office's gate on `JOB-17` in `04-jobs.md` would give Luka **Request completion**

1. As Luka, open G and click **Start**.
2. As Nikola, open Office Refurbishments **Settings**, **Job screen**, switch on **Require approval to complete jobs**.
3. As Luka, reload G and look at the action bar. Click **Request completion** and send with the message "Doors hung, closers adjusted."
4. As Nikola, switch **Require approval to complete jobs** off again.
5. As Luka, reload G and look at the banner and the action bar.
6. As Nikola, open Office **Approvals**, click **Approve** on G's card and confirm.
7. As Luka, open the bell and G.

- [x] Switching the gate on shows "Jobs now need approval to complete", and off "Jobs no longer need approval to complete"
- [x] With the gate on Luka's bar on G offers **Request completion**, and sending shows the banner and the greyed **Approval requested**
- [x] With the gate turned off again G still shows the banner and the request is still **Pending**, and Luka's bar now offers **Complete** again
- [x] Approving the pending request completes G, although the gate is off
- [x] Luka gets "Approved completion of …" and a Status changed entry for G
- [x] Office's gate ends **off** (Settings shows the switch off after a reload)

## APR-18 An org without the Approvals add-on
**As:** Petra (owner, Pine & Pixel Studio) · **Day:** any, after ONB-33 · **Needs:** ONB-31 done. Pine & Pixel has no add-ons.

1. Open **Brand refresh for Juniper Cafe** and look at the project's top bar.
2. Click the locked **Approvals** link.
3. Click **Upgrade in org settings**.
4. Go back, open **Settings**, scroll to **Job screen**, and look at **Require approval to complete jobs**.
5. Open the job "Draft logo options for Juniper Cafe" and look at its action bar. Open **Overview**.

- [x] The top bar shows **Approvals** with a lock
- [x] The page says "Approvals is not included in your plan" with **Upgrade in org settings**, and no approval queue
- [x] **Upgrade in org settings** opens **Organisation Settings**
- [x] **Require approval to complete jobs** is locked with "Requires an active Approvals add-on."
- [x] The job's action bar has no **Request Approval** button and no **Request completion**: **Complete** completes directly (`JOB-22` in `04-jobs.md` checks the rest of the job screen)
- [x] **Overview** shows "All caught up — no pending approvals across your projects." and no error message

## APR-19 The Serbian UI
**As:** Luka (member, Serbian UI), then Ana · **Day:** any, after ONB-33 · **Needs:** APR-10, APR-14 and APR-15 done: C Completed with an **Approved, not applied** request, A with one Pending, one Rejected and two Approved requests

1. As Luka, open C and open the Approvals section. Then open A and its Approvals section.
2. On A click **Request completion** (the Serbian text on the bar), look at the window and click **Cancel**.
3. Open the bell and read the approval entries. Open **Overview**.
4. As Ana, change nothing: just open Residential **Approvals** to confirm the queue is in English for her.

- [x] The labels **Completion**, **Pending**, **Approved**, **Rejected**, **Cancelled** and **Approved, not applied**, "Requested by …", "Approved by …" and "Rejected by …" are in Serbian and read naturally
- [x] The line "The job wasn't completed because it was Blocked." is in Serbian, and the status name inside it is Serbian too
- [x] The **Request completion** window (title, explanation, **Message**, **Cancel**, button) is in Serbian
- [x] The approval notifications ("Approved completion of …", "Approved your request on …", "Rejected your request on …") are in Serbian
- [x] No English text is left on the screens above (known Serbian wording choice to judge: the Serbian texts call a job "zadatak" in some places and "posao" in others, tick when you accept it, or link a `UX:` job)
- [x] Ana's own screens stay in English

## APR-20 Notifications of the whole file
**As:** Ana, Nikola, Ivana, Luka, Sara, Petra, each in her own profile · **Day:** any, after APR-19 · **Needs:** APR-01 to APR-19 done

Open each person's bell (or **Notifications**) and filter by the Willow Lane and Dunmore Dental jobs. Compare with the table at the top of this file.

- [x] Ana has 11 **Approval requested**, 1 **Status changed** and 1 **Job blocked** about these jobs
- [x] Nikola has 13 **Approval requested**, 12 **Status changed** and 1 **Job blocked** about these jobs
- [x] Ivana has 1 **Job assigned**, 2 **Approval requested**, 2 **Approval decided** and 6 **Status changed**
- [x] Luka has 4 **Job assigned**, 7 **Approval decided** and 4 **Status changed**
- [x] Sara has 2 **Job assigned**, 2 **Approval decided**, 3 **Status changed** and 1 **Job deleted**
- [x] Petra has no notification, and nobody has an entry for an action they did themselves

## Not manual
- Calling the approval endpoints through the API without the Approvals add-on (403): covered by `RequiresAddonAspectTest` ("Should throw ForbiddenException when add-on is not active") and the add-on cases of `CompletionApprovalIntegrationTest` ("Gate on but the org lacks the APPROVALS add-on: gate not enforced, 200 completed"). `APR-18` checks what the screens show.
- Past due subscription: the gate pauses, the cancel endpoint is refused (403) and a Member completes directly: covered by `CompletionApprovalIntegrationTest` ("Gate on, APPROVALS add-on but subscription PAST_DUE: gate paused, 200 completed") and `CompletionApprovalCancelIntegrationTest` ("Subscription PAST_DUE: cancel endpoint 403; the Member completes directly and their request is cancelled"). `12-billing.md` checks the screens.
- A Member requesting approval on a job that isn't theirs (403 "Access denied: you are not assigned to this job"), a blank description or one over 2000 characters (400), a caller who isn't a project member (403), and unknown project or job (404): covered by `ApprovalIntegrationTest` ("request should return 403 when unassigned member requests approval on a job they don't own", "… 400 when description is blank", "… 400 when description exceeds 2000 characters", "… 403 when caller is not a project member", "… 404 when job does not exist"). The screen never offers these (`APR-12`, `APR-13`).
- A Member deciding, or reading the project queue, through the API (403): covered by `ApprovalIntegrationTest` ("decide should return 403 when a member tries to decide", "listPendingByProject should return 403 when a member requests the pending queue") and `CompletionApprovalIntegrationTest` ("A Member can't decide a completion approval (403)"). `APR-13` checks what the screens show.
- Deciding a request twice (409), setting a decision back to Pending (400) or to Cancelled (400, withdrawal has its own endpoint): covered by `ApprovalIntegrationTest` ("decide should return 409 when approval has already been decided", "… 400 when status is PENDING") and `CompletionApprovalCancelIntegrationTest` ("Deciding with status CANCELLED is a 400: withdrawal has its own endpoint", "A cancelled approval can't be decided afterwards (409)"). `APR-15` checks the 409 on screen.
- Another Member withdrawing a request (403), withdrawing a manual request (409), withdrawing a decided or already cancelled request (409): covered by `CompletionApprovalCancelIntegrationTest` ("Another Member: 403, approval stays pending", "Manual approval: 409, even for its requester, and it stays pending", "Already decided (rejected): 409, status unchanged", "Already cancelled: a second withdrawal is 409"). The screen only shows **Withdraw** to people who may use it.
- Two completion requests at once (double click, two Members, 409 "A completion approval is already pending for this job"): covered by `CompletionApprovalIntegrationTest` ("A second request while one is pending gets 409 and creates nothing", "Race guard: concurrent completion requests create exactly one approval, the rest get 409").
- A decision racing a status change (row lock: approve then records "not applied", or the block fails): covered by `CompletionApprovalIntegrationTest` ("Row lock: a decision waits for an uncommitted block, then records 'not applied' with reason BLOCKED", "Row lock: a block waits for an uncommitted approval, then fails (400) instead of overwriting it"). `APR-10` checks the not-applied outcome by hand.
- Requests, decisions and withdrawals in an archived (completed) project (409): covered by `ApprovalIntegrationTest` (`request_shouldReturn409_whenProjectIsCompleted`, `decide_shouldReturn409_whenProjectIsCompleted`), `CompletionApprovalIntegrationTest` ("Completed project: a completion request is rejected with 409 as today") and `CompletionApprovalCancelIntegrationTest` ("Completed project: withdrawal rejected with 409 as today"). The archived project on screen is in `03-projects.md`.
- A Member's New to Completed attempt (400, no approval created) and the add-on lapsing while a request is pending (the Member completes directly and the request is cancelled): covered by `CompletionApprovalIntegrationTest` ("Gated Member: an illegal transition (New → Completed) is still 400 and creates no approval") and `CompletionApprovalCancelIntegrationTest` ("Add-on lapsed: cancel endpoint 403; the Member completes directly and their request is cancelled").
- The cross-project queue API only listing projects where the caller is Owner or Admin, oldest first, and requiring a login: covered by `ApprovalIntegrationTest` ("pendingAcrossOrgs should return oldest-first, only from projects where the caller is Owner/Admin, with project context", "pendingAcrossOrgs should return 401 without authentication"). `APR-04` and `APR-13` check it on `/overview`.
- Blocking a job leaves its pending request pending, and a job's list and detail carry the pending request: covered by `CompletionApprovalCancelIntegrationTest` ("Blocking a job with a pending request leaves the request pending") and `CompletionApprovalIntegrationTest` ("Job detail and job list show pendingCompletionApproval (null on jobs without one)"). `APR-10` and `APR-02` check them on screen.
- The notification payloads (`kind`, `decision`, `completionApplied`, the not-applied reason): covered by `NotificationServiceFanOutTest`, `NotificationEventListenerTest` and `ApprovalServiceCompletionTest`; checked on screen here and in `09-notifications.md`.
- Notification cleanup after 30/90 days: covered by `NotificationCleanupJobTest`.

## Findings
