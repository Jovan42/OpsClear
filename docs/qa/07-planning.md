# Planning

Last run:
Guide: /guide/milestones, /guide/templates-and-types, /guide/subscription, /guide/roles-permissions

This file covers the three planning add-ons of a project: **Milestones** (stages with a deadline and
a progress bar), **Job templates** (project templates and organisation templates, wildcards, starting
a job from a template) and **Job types** (coloured labels, ordering, delete rules), who may change
each per role, and what an organisation without the add-ons sees. The world is built by
`01-onboarding.md` ([README](README.md#test-world)): Brightline Renovations with Ana, Nikola, Ivana,
Luka and Sara, the projects Residential Fit-outs and Office Refurbishments, and Petra in Pine & Pixel
Studio, which has no add-ons. Run `01-onboarding.md` first, any day after ONB-33. Run this file in
this order: PLN-10, PLN-11, then PLN-01 to PLN-09, then PLN-12 to PLN-21 (the types come first because
job A and a template use them).

**What the screens do** (so the checks below make sense):

- **Who can change what.** Milestones, project templates and types: project Owners and Admins create,
  edit and delete. Members see the lists and can start a job from a template, but see no **+ New**,
  **Edit**, **Delete** or arrow buttons. Organisation templates (**Org Templates** on `/org/settings`):
  only organisation Owners and Admins change them, even a project Owner who is an org Member can't.
  Every organisation member sees the list.
- **Milestones.** Name required (up to 100 characters), description and deadline optional. A deadline
  in the past shows red with "Overdue". Deleting a milestone keeps its jobs, which become Ungrouped.
- **Templates.** Only the name is required (up to 100). Title up to 255. **Deadline offset (days)**
  must be above 0. A project template can set a **Fixed** assignee, a **Milestone** and a **Default
  type**; an org template can't (it names its default type in words, matched by name, ignoring case,
  against the types of the project the job is created in). A template with an active schedule can't be
  deleted. In the New Job window org templates are marked **[Org]**. Using one counts toward "Used n×".
- **Types.** Name required (up to 100), one of ten colours. The arrows change the list order. A type
  that a job or a template still uses can't be deleted.
- **Add-ons.** Milestones, Job templates and Job types are separate add-ons. Without one, its page shows
  an upgrade card and the matching section of the screens is hidden.
- **Other files.** This file doesn't change the milestones **Kitchen phase**, **Bathroom phase** and
  **Handover**, the types, templates or jobs of other files. Lists other files can add to say
  "includes", never an exact count. The schedules in `08-schedules.md` use their own templates.

**Things this file creates** (fictional customers, all in the test world). None of the jobs keeps a
deadline, so no deadline alert is ever sent for them.

| What | Where | Created in | Ends as |
|------|-------|-----------|---------|
| Milestones **Snagging at 9 Alder Court**, **Final clean at 9 Alder Court** | Residential | PLN-01 | Snagging stays, Final clean deleted in PLN-05 |
| Milestone **Fit-out sign-off for Marlow Accounting** | Office | PLN-04 | stays |
| Types **Plumbing**, **Electrical**, **Joinery** | Residential | PLN-10 | Plumbing and Joinery stay, Electrical deleted in PLN-13 |
| Template **Site visit report** | Residential | PLN-06 | stays |
| Template **Snagging walkthrough** | Residential | PLN-07 | deleted in PLN-09 |
| Org template **Handover pack** | Brightline | PLN-16 | stays |
| Job **A** Fit vanity unit at 9 Alder Court | Residential, milestone Snagging, type Plumbing | PLN-03 | Completed, stays |
| Job **B** Fit splashback at 9 Alder Court | Residential, milestone Final clean | PLN-03 | stays, Ungrouped after PLN-05 |
| Job **C** Site visit report for Residential Fit-outs, `<date>` | Residential, from a template | PLN-08 | stays |
| Job **D** Handover pack for 9 Alder Court | Residential, from the org template | PLN-18 | stays |

Everything else in the world is read only here.

## PLN-01 Milestones: create and read
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, after ONB-33 · **Needs:** the Milestones add-on on

1. Open **Residential Fit-outs**, click **Milestones** in the top bar.
2. Click **+ New Milestone**, click **Create** with **Name** empty.
3. Type **Snagging at 9 Alder Court** as **Name**, **Description** "Fix the small defects the client listed on the walkthrough", **Deadline** three weeks from today. Click **Create**.
4. Click **+ New Milestone**, name **Final clean at 9 Alder Court**, no description, no deadline. Click **Create**.
5. Click **+ New Milestone**, paste 101 characters into **Name**, click **Create**. Close the window.

- [ ] The list includes **Kitchen phase**, **Bathroom phase** and **Handover** from earlier files, each with a progress bar
- [ ] The empty name shows an error and no milestone is created
- [ ] Snagging shows its description, "Due" with the date, and a progress bar at 0/0
- [ ] Final clean shows no description and no deadline line
- [ ] The 101 characters are refused, no milestone is created
- [ ] Each milestone has **View jobs →**, **Edit** and **Delete**

## PLN-02 Milestones: edit
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** PLN-01

1. On the Milestones page click **Edit** next to **Snagging at 9 Alder Court**.
2. Change the **Deadline** to a date in the past (yesterday) and click **Save**.
3. Click **Edit** again, move the deadline to three weeks from today, click **Save**.

- [ ] The window opens filled with the current name, description and deadline
- [ ] With yesterday's date the row turns red and reads "Overdue"
- [ ] With the new date the row is back to normal and shows the new date
- [ ] The name and description did not change

## PLN-03 Milestones: jobs and progress
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** PLN-01, PLN-10

1. Open **Jobs**, **+ New Job**. Title **Fit vanity unit at 9 Alder Court**, **Milestone** Snagging at 9 Alder Court, **Type** Plumbing, no deadline. Create it.
2. Create **Fit splashback at 9 Alder Court** with **Milestone** Final clean at 9 Alder Court, no deadline.
3. Open job A, click **Start**, then **Complete** (Ana is owner, no approval is needed to complete).
4. Go back to **Milestones**.
5. Click **View jobs →** next to Snagging at 9 Alder Court.

- [ ] Both jobs are created and show their milestone
- [ ] Snagging shows 1/1 and Final clean shows 0/1
- [ ] **View jobs →** opens the Jobs list with only job A
- [ ] The Jobs list, grouped, shows A under Snagging at 9 Alder Court and B under Final clean at 9 Alder Court

## PLN-04 Milestones in Office Refurbishments
**As:** Nikola (owner, Office Refurbishments) · **Day:** any · **Needs:** the Milestones add-on on

1. Open **Office Refurbishments**, **Milestones**.
2. Create **Fit-out sign-off for Marlow Accounting**, deadline four weeks from today.
3. Open **Residential Fit-outs** through the project list, then **Milestones**.

- [ ] The new milestone shows in Office Refurbishments
- [ ] The Residential list doesn't include it, and the Residential milestones don't show in Office
- [ ] Nikola (admin of Residential) sees **+ New Milestone**, **Edit** and **Delete** there

## PLN-05 Milestones: delete keeps the jobs
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** PLN-03

1. On the Milestones page click **Delete** next to **Final clean at 9 Alder Court**.
2. Read the window, click **Cancel**. Click **Delete** again and confirm.
3. Open **Jobs** and find job B.

- [ ] The window says jobs in the milestone will become ungrouped and names the milestone
- [ ] **Cancel** keeps the milestone
- [ ] After confirming the milestone is gone from the list
- [ ] Job B still exists, its milestone is empty, and the grouped list shows it under **Ungrouped**

## PLN-06 Project templates: create
**As:** Nikola (admin, Residential Fit-outs) · **Day:** any · **Needs:** the Job templates add-on on, PLN-01

1. Open **Residential Fit-outs**, **Templates**. Click **+ New Template**, click **Create** with **Name** empty.
2. **Name** "Site visit report", **Title template** `Site visit report for {{project}}, {{date}}`, **Description template** `Visit by {{creator}}, week {{week}}, {{quarter}}. Visit number {{occurrence}}.`, **Client** "Brightline Renovations", **Priority** Low, **Assignee mode** None, **Deadline offset** `0`.
3. Change the offset to `30`. Click **Create**.

- [ ] The empty name shows an error and nothing is created
- [ ] An offset of 0 is refused
- [ ] The template is created and the row shows its name, priority **Low**, "+30d deadline" and "Used 0×"
- [ ] **Available wildcards** in the window lists the date, day, month, year, week, quarter, project, creator, assignee and usage count
- [ ] The template list includes only templates of Residential and of the organisation, none of Office Refurbishments

## PLN-07 Project templates: fixed assignee, milestone and type
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** PLN-01, PLN-10

1. On **Templates** click **+ New Template**.
2. **Name** "Snagging walkthrough", **Title template** `Snagging walkthrough at {{client}}`, **Client** "Alder Court Residents", **Priority** High, **Assignee mode** Fixed, **Assignee** Ivana, **Milestone** Snagging at 9 Alder Court, **Default type** Joinery, **Deadline offset** `30`. Click **Create**.
3. Click **Edit** on it, change **Assignee mode** to **Ask**, then **Save**. Edit again and set it back to **Fixed** with Ivana.

- [ ] **Fixed** shows an **Assignee** picker that lists the project members
- [ ] The **Milestone** menu lists the project's milestones and **No milestone**, **Default type** lists the project's types
- [ ] The row shows priority **High** and "+30d deadline"
- [ ] The saved edit keeps every other field as it was
- [ ] **Ask** hides the **Assignee** picker

## PLN-08 Start a job from a template
**As:** Luka (member, Residential Fit-outs, Serbian UI) · **Day:** any · **Needs:** PLN-06, PLN-07

1. Open **Residential Fit-outs**, **Jobs**, **+ New Job**.
2. In **Start from template** pick **Site visit report**.
3. Clear the **Deadline** box, and click **Create job**. This is job C.
4. Open **+ New Job** again and pick **Snagging walkthrough**, then pick the empty choice at the top of the list.
5. Open the **Templates** page.

- [ ] The title reads "Site visit report for Residential Fit-outs, " plus today's date (YYYY-MM-DD)
- [ ] The description shows Luka's name, the ISO week number, the quarter (Q1 to Q4) and "Visit number 1"
- [ ] Client is "Brightline Renovations", priority Low, and the deadline was pre-filled 30 days from today before it was cleared
- [ ] Job C is created with no deadline
- [ ] Picking **Snagging walkthrough** filled the title, **High** priority, Ivana as assignee, the milestone Snagging and the type Joinery
- [ ] Choosing the empty option resets every box
- [ ] **Site visit report** now shows "Used 1×", **Snagging walkthrough** still "Used 0×"
- [ ] Luka sees no **+ New Template**, **Edit**, **Delete** or **Schedule** on the Templates page, and the page reads in Serbian

## PLN-09 Templates: delete
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** PLN-07, PLN-08

1. On **Templates** click **Delete** next to **Snagging walkthrough**, read the window, click **Cancel**.
2. Click **Delete** again and confirm.
3. Open **+ New Job**.

- [ ] The window names the template and says it can't be undone
- [ ] The template is gone from the list after confirming
- [ ] The New Job window no longer offers it in **Start from template**
- [ ] **Site visit report** is still offered

## PLN-10 Job types: create
**As:** Ana (owner, Residential Fit-outs) · **Day:** any, first of the planning files · **Needs:** the Job types add-on on

1. Open **Residential Fit-outs**, **Types**.
2. Click **+ New Type**, click **Create** with **Name** empty.
3. Name **Plumbing**, pick **Blue**, click **Create**. Then **Electrical** in **Amber** and **Joinery** in **Teal**.
4. Click **+ New Type**, paste 101 characters into **Name**, pick a colour, click **Create**. Close the window.

- [ ] The empty name shows an error and nothing is created
- [ ] The picker offers ten colours
- [ ] The three types show in the order created, each in its colour
- [ ] The 101 characters are refused

## PLN-11 Job types: edit and reorder
**As:** Nikola (admin, Residential Fit-outs) · **Day:** any · **Needs:** PLN-10

1. On **Types** click **Edit** next to **Electrical**, change the colour to **Red**, click **Save**.
2. Click the **Move up** arrow on **Joinery** twice, then reload the page.
3. Click **Move down** on **Joinery** twice to put it last.

- [ ] The colour of Electrical changes and its name doesn't
- [ ] The first row has no **Move up** and the last row no **Move down**
- [ ] After the two moves up Joinery is first, and the order is the same after the reload
- [ ] After the two moves down Joinery is last again

## PLN-12 Job types show on jobs
**As:** Ivana (member, Residential Fit-outs) · **Day:** any · **Needs:** PLN-03

1. Open the Jobs list of **Residential Fit-outs** and open the **All types** menu.
2. Pick **Plumbing**.
3. Open job A.
4. Open **Types**.

- [ ] The menu lists Plumbing, Electrical and Joinery
- [ ] The list shows only jobs of type Plumbing, including job A with a blue label
- [ ] Job A's screen shows the Plumbing label
- [ ] The Types page lists the three types and Ivana sees no **+ New Type**, arrows, **Edit** or **Delete**

## PLN-13 Job types: delete rules
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** PLN-03, PLN-12

1. On **Types** click **Delete** next to **Plumbing** and confirm.
2. Click **Delete** next to **Electrical** and confirm.
3. Open **Types** again and the Jobs list.

- [ ] Deleting **Plumbing**, which job A uses, is refused with a message naming how many jobs still use it, and the type stays
- [ ] Job A still shows the Plumbing label
- [ ] Deleting **Electrical**, which nothing uses, succeeds and the row is gone
- [ ] **All types** on the Jobs list no longer offers Electrical

## PLN-14 Member cannot change planning data
**As:** Sara (member, Residential Fit-outs) · **Day:** any · **Needs:** PLN-01, PLN-06, PLN-10

1. Open **Residential Fit-outs**, **Milestones**, **Templates**, **Types** one by one.
2. Open **Org Templates** (click your name at the top right, **Organisation**).

- [ ] **Milestones** lists them with **View jobs →** and no **+ New Milestone**, **Edit** or **Delete**
- [ ] **Templates** lists them with no **+ New Template**, **Edit**, **Delete** or **Schedule**
- [ ] **Types** lists them with no **+ New Type**, arrows, **Edit** or **Delete**
- [ ] The Org Templates section lists the organisation templates and shows no **+ New Template**, **Edit** or **Delete**

## PLN-15 Planning is per project, not shared
**As:** Luka (member, both projects) · **Day:** any · **Needs:** PLN-04, PLN-10

1. Open **Office Refurbishments**, **Milestones** and **Types**.
2. Open **+ New Job** in Office Refurbishments.

- [ ] Office has its own milestone **Fit-out sign-off for Marlow Accounting** and none of the Residential ones
- [ ] The Office types list is empty, with a message that there are none, and no create button for Luka
- [ ] The New Job window in Office offers only Office milestones and no Residential types

## PLN-16 Org templates: create
**As:** Ana (org owner) · **Day:** any · **Needs:** PLN-10, the Job templates add-on on

1. Click your name at the top right, **Organisation**, find **Org Templates**.
2. Click **+ New Template**. **Name** "Handover pack", **Title template** `Handover pack for {{project}}`, **Description template** `Prepared by {{creator}} on {{date}}.`, **Priority** Medium, **Deadline offset** `30`, **Default type** (typed) "joinery". Click **Create**.
3. Look at the **Assignee mode** menu and at the fields below it.

- [ ] The organisation template is created and listed with an ID such as `TPL-…`, priority and "+30d"
- [ ] **Assignee mode** has no **Fixed**, and there is no **Assignee** or **Milestone** field
- [ ] **Default type** is a text box with the "matched by name" help text
- [ ] The Residential Templates page now includes **Handover pack** too

## PLN-17 Org templates: who can change them
**As:** Ivana (org member, project admin of Office Refurbishments), then Nikola (org admin) · **Day:** any · **Needs:** PLN-16

1. As Ivana open **Organisation**, then **Org Templates**.
2. As Nikola open the same section, click **Edit** on **Handover pack**, change **Priority** to **High**, **Save**.

- [ ] Ivana sees the list, with no **+ New Template**, **Edit** or **Delete**, although she administers a project
- [ ] Nikola can edit it and the row shows the new priority
- [ ] Ivana's Templates page in Office lists it without **Edit** or **Delete**

## PLN-18 Org template in a project, matched by type name
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** PLN-16, PLN-10

1. Open **Jobs**, **+ New Job**, in **Start from template** pick **[Org] Handover pack**.
2. Change nothing but clear the **Deadline** and create the job (job D, title "Handover pack for Residential Fit-outs").
3. Open **Office Refurbishments**, **+ New Job**, pick **[Org] Handover pack** and close the window with **Cancel**.

- [ ] The template is marked **[Org]** in the list
- [ ] The form is filled with title, description, **Medium** priority (**High** if PLN-17 was run) and the type **Joinery**, matched by name
- [ ] Job D is created with the Joinery label
- [ ] In Office, where there is no Joinery type, the **Type** box stays empty
- [ ] On the Org Templates page **Handover pack** shows "Used 1×"

## PLN-19 Org template: delete
**As:** Nikola (org admin) · **Day:** any · **Needs:** PLN-18

1. Create an organisation template "Warranty call-back" with just a name.
2. Click **Delete** next to it and confirm.
3. Open the New Job window in Residential.

- [ ] It is created, then deleted after confirming
- [ ] It is not offered in **Start from template**
- [ ] **Handover pack** is still offered

## PLN-20 A template used by a schedule can't be deleted
**As:** Ana (owner, Residential Fit-outs) · **Day:** any · **Needs:** PLN-06; the schedules area (`08-schedules.md`) can be run before or after

1. On **Templates** click **Schedule** next to **Site visit report** and create a weekly schedule "Site visit report, weekly" (Mondays 08:00),.
2. Click **Delete** next to **Site visit report** and confirm.
3. Open **Schedules**, click **Edit** on the schedule and check the **Template** it uses.
4. Leave the schedule running and the template in place.

- [ ] **Schedule** opens the New Schedule window with the template selected
- [ ] The first delete is refused with a message naming "Site visit report, weekly", and the template stays
- [ ] The template is still in the list after the refused delete
- [ ] The schedule still uses **Site visit report**

## PLN-21 Add-ons off: Pine & Pixel Studio
**As:** Petra (owner, Pine & Pixel Studio) · **Day:** any · **Needs:** ONB done, Pine & Pixel has a project and no add-ons

1. Open the project of Pine & Pixel Studio, click **Milestones** in the top bar.
2. Open **Templates**, then **Types**.
3. Open the New Job window.
4. Open **Organisation**, look for **Org Templates**.

- [ ] Each of the three pages shows an upgrade card instead of the list, with no create button
- [ ] The New Job window has no **Start from template**, **Milestone** or **Type** box
- [ ] There is no **Org Templates** section on the organisation page
- [ ] No data of Brightline shows anywhere on these pages

## Not manual
- Direct API calls without a project role or from another project: 403 for a non-member and 404 for a milestone, template or type of another project: covered by `MilestoneIntegrationTest` (`createMilestone_shouldReturn403_whenNotMember`, `listMilestones_shouldReturn403_whenNotMember`, `updateMilestone_shouldReturn404_whenMilestoneInDifferentProject`, `deleteMilestone_shouldReturn404_whenMilestoneBelongsToDifferentProject`), `JobTypeIntegrationTest` (`createType_shouldReturn403_whenNotMember`, `updateType_shouldReturn404_whenTypeInDifferentProject`) and `JobTemplateIntegrationTest`.
- A member calling the write endpoints directly (403): covered by `MilestoneIntegrationTest` (`createMilestone_shouldReturn403_forMember`, `deleteMilestone_shouldReturn403_forMember`), `JobTypeIntegrationTest` (`createType_shouldReturn403_forMember`, `updateType_shouldReturn403_forMember`, `deleteType_shouldReturn403_forMember`) and `OrgTemplateIntegrationTest` (`createOrgTemplate_shouldReturn403_forOrgMember`, `updateOrgTemplate_shouldReturn403_forOrgMember`, `deleteOrgTemplate_shouldReturn403_forOrgMember`). PLN-14 and PLN-17 check that the screens hide the buttons.
- Input rules the form already stops (blank name, a name over 100 characters, a missing or invalid colour, a deadline offset of 0 or less, an invalid priority or assignee mode): covered by `MilestoneIntegrationTest` (`createMilestone_shouldReturn400_whenNameBlank`), `JobTypeIntegrationTest` (`createType_shouldReturn400_whenNameBlank`, `createType_shouldReturn400_whenColorMissing`, `createType_shouldReturn400_whenColorInvalid`), `OrgTemplateIntegrationTest` (`createOrgTemplate_shouldReturn400_whenNameBlank`) and the template validation case in `frontend/cypress/e2e/job-templates/job-templates.cy.ts` ("a blank name 400s; an invalid priority or assigneeMode 400s; a deadlineOffsetDays <= 0 400s").
- Scope rules between project and organisation templates: a project template with `defaultTypeName` and an org template with `defaultTypeId` are refused (400), a fixed assignee outside the organisation is a 404 (JOB-393), an org template of another organisation is a 404: covered by `OrgTemplateIntegrationTest` (`createOrgTemplate_shouldReturn400_whenDefaultTypeIdProvided`, `updateOrgTemplate_shouldReturn400_whenDefaultTypeIdProvided`, `updateOrgTemplate_shouldReturn404_whenAssigneeInOtherOrg`, `updateOrgTemplate_shouldReturn404_whenTemplateBelongsToDifferentOrg`, `recordUsage_shouldReturn404_whenOrgTemplateBelongsToDifferentOrg`, `listOrgTemplates_shouldReturn403_whenUserBelongsToDifferentOrg`) and `JobTemplateServiceTest`.
- Every endpoint of the three add-ons answering 403 without the add-on: covered by `RequiresAddonAspectTest` ("Should throw ForbiddenException when add-on is not active"), `JobTypeIntegrationTest` (`createType_shouldReturn403_whenAddonNotActive`) and `OrgTemplateIntegrationTest` (`createOrgTemplate_shouldReturn403_whenAddonNotSubscribed`). PLN-21 checks the screens.
- Type delete blocked by jobs only, templates only, or both, with the counts in the message: covered by `JobTypeIntegrationTest` (`deleteType_shouldReturn409_whenJobReferencesType`, `deleteType_shouldReturn409_whenTemplateReferencesType`, `deleteType_shouldReturn409_whenJobsAndTemplatesReferenceType`). PLN-13 checks the jobs-only case on screen. A type named only by an org template has no delete guard (ADR-0042): covered by `frontend/cypress/e2e/job-types/job-types.cy.ts`.
- Two reorder clicks in flight at once never duplicate an order (JOB-386): covered by `frontend/cypress/e2e/job-types/job-types.cy.ts` ("a second reorder click while one is in flight is blocked").
- Every wildcard and its arithmetic (`{{date+7}}`, `{{month+2}}` across a year end, `{{quarter-2}}`, the ISO week at a year boundary, malformed tokens left as literal text): covered by `frontend/cypress/e2e/job-templates/job-templates.cy.ts` ("every base wildcard resolves", "arithmetic wildcards resolve correctly", "{{month+2}} in November rolls correctly", "{{quarter-2}} in Q1 rolls back", "the ISO week wildcard at a year boundary", "unresolved/malformed wildcard tokens are left as literal text"). PLN-08 checks the common ones.
- The usage counter being incremented atomically, and any member recording usage: covered by `frontend/cypress/e2e/job-templates/job-templates.cy.ts` ("using a template records usage, incrementing usedCount atomically" and "a MEMBER gets 403 on create/update/delete, but any member can record usage").
- Renaming a type after an org template named it, so the next use leaves the type blank: covered by `frontend/cypress/e2e/job-templates/job-templates.cy.ts` ("renaming a job type after an org template's defaultTypeName was configured").
- The progress format preference (fraction or percentage) on the milestone bars: covered by `frontend/cypress/e2e/milestones/milestones.cy.ts` ("the progress format preference toggles"); the setting is in `11-account.md`.
- The project dashboard's job counts per type: covered by `frontend/cypress/e2e/job-types/job-types.cy.ts` (the MEMBER read-only case checks the breakdown); the dashboard screen is in `10-dashboard-stats.md`.

## Findings
