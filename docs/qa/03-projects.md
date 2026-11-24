# Projects

Last run: 2026-11-22 to 2026-11-24, Jovan
Guide: /guide/first-project, /guide/project-settings, /guide/roles-permissions

Create, edit, complete and delete projects, the project directory, project members and roles,
the job screen settings (section visibility and the completion gate) and project links. It runs on
the world `01-onboarding.md` built (see the [README](README.md#test-world)). Creating the three
projects, the first block reasons, adding the first members and the first section and gate
settings are already checked in ONB-17 to ONB-24, ONB-31 and ONB-32, so they aren't repeated here.

**What the app calls it.** The README and ADR-0063 say "archive" for Spring Maintenance 2026. The
app has no archive: a project is **Completed** (**Complete project** in **Danger zone**), moves to
the **Completed** tab and goes read-only, and can be **Reactivate**d. PRJ-15 to PRJ-17 do this.

**Keeping the other files safe.** Everything here that adds, demotes or removes people happens in
a throwaway project, **Warehouse Extension 2026**, created in PRJ-03 and deleted in PRJ-14. Nobody
is added to, removed from, promoted or demoted in Residential Fit-outs, Office Refurbishments or
Spring Maintenance 2026, so the schedules in `08-schedules.md` (Ivana and Sara in Residential) and
the notification checks in `09-notifications.md` still hold. The only settings changed in the
three real projects are put back at the end of the scenario (Office's **Notes** switch in PRJ-10,
Office's gate in PRJ-11). Residential gets two project links (PRJ-12) that nothing else uses.

**Project roles are not org roles.** Ana is the org Owner, but in Warehouse Extension 2026 she is
only a project Member, which shows that an org role gives no project rights. Nikola is the org
Admin and Ivana is an org Member, and in Warehouse they are Admin and Owner.

**Dates.** Day 1 is today (UTC). PRJ-14 runs on day 5 and PRJ-15 to PRJ-17 on day 6, after the
notification scenarios that still need the projects as they are.

## PRJ-01 The Projects page: tabs, counts and badges
**As:** Ana (Residential and Spring owner), then Nikola (Office owner, Residential admin) · **Day:** 1 · **Needs:** ONB-19 and ONB-24 done. Run this before PRJ-03, which adds a project.

1. As Ana, open **Projects**.
2. Click the **Completed** tab, then **All**, then **Active**.
3. Click the gear icon at the bottom right of the **Residential Fit-outs** card.
4. Go back to **Projects**. As Nikola, open **Projects**.

- [x] Ana's **Active** tab lists **Residential Fit-outs** and **Spring Maintenance 2026**, each with an **Owner** badge
- [x] The tab counts read **Active** 2, **Completed** 0 and **All** 2
- [x] The **Completed** tab says "No projects match this filter."
- [x] The gear icon opens **Project Settings** for Residential Fit-outs
- [x] Nikola's **Active** tab lists **Office Refurbishments** with an **Owner** badge and **Residential Fit-outs** with no badge, because he is only its Admin
- [x] Nikola's counts read **Active** 2, **Completed** 0 and **All** 2

## PRJ-02 The new project form checks its input
**As:** Ana (org owner) · **Day:** 1 · **Needs:** PRJ-01 done

1. On **Projects** click **+ New Project**.
2. In **Name** paste `Riverside Apartments Block C phase two bathrooms and kitchens fit-out works pack1` (81 characters) and click **Create project**.
3. Replace the name with `Residential Fit-outs` and click **Create project**.
4. Open **Block reasons**. Type `Waiting on client` and click **Add**. Type `Waiting on client` again and click **Add**.
5. Click **Remove** on the entry.
6. Click **Cancel**. Click **+ New Project** again.

- [x] The 81-character name shows "Max 80 characters" and no project is created
- [x] The duplicate name is refused with "A project with this name already exists", the window stays open with the typed values, and the **Active** count stays 2
- [x] The second identical block reason isn't added: the list has one entry and **Block reasons** shows (1)
- [x] **Remove** takes the entry out and the (1) disappears
- [x] **Cancel** closes the window and creates nothing
- [x] The window opened again is empty: no name, no description, no block reasons

## PRJ-03 An org member creates a project and becomes its Owner
**As:** Ivana (org member), then Ana and Nikola · **Day:** 1 · **Needs:** PRJ-02 done

1. As Ivana, open **Projects** and click **+ New Project**.
2. Type **Warehouse Extension 2026** in **Name** and "Slab, steel frame and cladding for the yard warehouse at the Brightline depot." in **Description**.
3. Open **Block reasons**, add **Waiting on steel delivery** and **Waiting on planning sign-off**, and click **Create project**.
4. Open **Projects** again as Ivana, then as Ana and as Nikola.
5. As Ivana open the project, **Settings**.

- [x] **Create project** works for an org member who is neither Owner nor Admin
- [x] Ivana's **Active** tab lists **Warehouse Extension 2026** with an **Owner** badge, next to **Office Refurbishments** and **Residential Fit-outs** with no badge, so the count reads 3
- [x] **Project Settings** lists both block reasons
- [x] Ana's **Projects** list doesn't show it, although she is the org Owner
- [x] Nikola's **Projects** list doesn't show it, although he is an org Admin

This project is the throwaway for PRJ-04 to PRJ-11. PRJ-14 deletes it.

## PRJ-04 The project switcher lists what each person may open
**As:** Ana, Nikola, Ivana, then Petra · **Day:** 1 · **Needs:** PRJ-03 done. Run it before PRJ-06, which adds Ana and Nikola to Warehouse.

1. As Ana, open **Residential Fit-outs**, **Milestones**. Click the project name in the top bar.
2. Click **Office Refurbishments** in the list.
3. Click **Spring Maintenance 2026** in the list.
4. As Nikola, open **Residential Fit-outs** and click the project name in the top bar.
5. As Ivana, open **Warehouse Extension 2026** and click the project name in the top bar.
6. As Petra, open **Brand refresh for Juniper Cafe** and click the project name in the top bar.

- [x] Ana's list shows **Spring Maintenance 2026** as a normal entry, and **Office Refurbishments** and **Warehouse Extension 2026** greyed out, because she is the org Owner but not a member of them
- [x] Clicking a greyed entry does nothing: the page and the list stay as they are
- [x] Clicking **Spring Maintenance 2026** opens its **Milestones** page, the same kind of page she was on
- [x] Nikola's list shows **Office Refurbishments** as a normal entry, and **Spring Maintenance 2026** and **Warehouse Extension 2026** greyed out
- [x] Ivana's list shows **Office Refurbishments** and **Residential Fit-outs** as normal entries and nothing greyed, and **Spring Maintenance 2026** isn't listed, because she is only an org Member
- [x] Petra's list says "No other active projects", and nothing from Brightline appears

## PRJ-05 Opening a project by address, and a project you're not in
**As:** Ana, then Nikola · **Day:** 1 · **Needs:** PRJ-04 done

1. As Ana, open **Residential Fit-outs** from **Projects** and read the address: `/projects/<ID>/…`, where `<ID>` is the project's friendly ID.
2. Type `/projects/<ID>` (the ID alone) in the address bar.
3. As Nikola, open **Office Refurbishments** and copy its friendly ID from the address bar.
4. As Ana, type `/projects/<Office ID>/settings` in the address bar.

- [x] The ID alone sends Ana on to the project's default page (**Dashboard**, unless she changed **Default project page** in **Account settings**) without staying on a blank page
- [x] Opening Office's settings as Ana shows a red message "You are not a member of this project"
- [x] That page shows no Office data: the name box is empty and **Members** lists nobody

## PRJ-06 Add members by name or email
**As:** Ivana (Warehouse owner), then Nikola and Ana · **Day:** 1 · **Needs:** PRJ-04 done

1. As Ivana, open **Warehouse Extension 2026**, **Settings**, **Members**.
2. In **Search by name or email…** type `N`.
3. Type `vanov`. Pick Nikola, choose **Admin** and click **Add**.
4. Type `etro`. Pick Ana, leave the role on **Member** and click **Add**.
5. Type `etra`, then `tanko`.
6. Type `arkov`.
7. Type `uka`, pick Luka, choose **Owner** and click **Add**.
8. As Nikola and as Ana, open the bell.

The search matches a name that contains the text, or an email that starts with it. All personas share one mailbox, so the fragments above sit in the middle of a name and can't be the start of the shared address. Don't replace them with the start of a name.

The two notification boxes below only look for the one entry named in them. Other files' scenarios add their own entries to the same centres, so scroll to find it.

- [x] One character shows no suggestions
- [x] `vanov` lists only Nikola Jovanović, with his email, found by a fragment of his last name
- [x] Nikola is added and the table shows him as **Admin**
- [x] `etro` finds Ana by a fragment of her last name, and she is added as **Member**
- [x] `etra` and `tanko` list nobody, because Petra isn't in Brightline
- [x] `arkov` lists Ivana Marković greyed out with "(already a member)", and she can't be picked
- [x] Adding Luka as **Owner** is refused with "Cannot assign OWNER role", and Luka isn't in the table
- [x] Nikola gets "Added you to Warehouse Extension 2026 as Admin" in the notification centre
- [x] Ana gets "Added you to Warehouse Extension 2026 as Member" in the notification centre

## PRJ-07 What each project role sees on Settings
**As:** Ivana (project Owner), Nikola (project Admin), then Ana (project Member) · **Day:** 1 · **Needs:** PRJ-06 done

1. As Ivana, open **Warehouse Extension 2026**, **Settings**, and look at every section from the top.
2. Repeat as Nikola.
3. Repeat as Ana.

- [x] Owner: **Name** and **Description** are editable with **Save changes**
- [x] Owner: **Members** has the add form, a role box and **Remove** on Nikola's and Ana's rows, and only a badge on her own
- [x] Owner: **Block Reasons** lists both reasons, each with **Remove**
- [x] Owner: **Danger zone** has **Complete project** and **Delete project**
- [x] Admin: **Name** and **Description** are editable with **Save changes**
- [x] Admin: **Members** has the add form and a role box and **Remove** on Ana's row, and only badges on Ivana's (Owner) row and his own
- [x] Admin: **Block Reasons** is shown with **Remove**, and the **Job screen** switches can be changed
- [x] Admin: there's no **Danger zone**
- [x] Member (Ana, although she is the org Owner): **Name** and **Description** are greyed out with no **Save changes**
- [x] Member: **Members** lists everyone with badges only, no add form, role boxes or **Remove**
- [x] Member: there's no **Block Reasons** section and no **Danger zone**
- [x] Member: the **Job screen** switches are locked with "Only project Owners and Admins can change this."

## PRJ-08 Edit the name and description
**As:** Ivana (Warehouse owner), then Nikola (Warehouse admin) · **Day:** 1 · **Needs:** PRJ-07 done

1. As Ivana, open **Settings**. Look at **Save changes**.
2. Clear **Name** and click **Save changes**.
3. Type `Warehouse Extension 2026 - phase 1` and click **Cancel**.
4. Type `Warehouse Extension 2026 - phase 1` again and click **Save changes**. Reload the page.
5. As Nikola, open the same **Settings**. Change **Description** to "Slab, steel frame and cladding for the yard warehouse. Planning approval received." and click **Save changes**.
6. As Ivana, reload the page and look at the project name in the top bar.

- [x] **Save changes** is disabled until something is edited
- [x] An empty name shows "Name is required" and nothing is saved
- [x] **Cancel** puts the original name back
- [x] After **Save changes** and a reload the new name is still there, and the project name in the top bar shows it
- [x] Nikola (Admin) can save a new description, and Ivana sees it after a reload

The project is now called **Warehouse Extension 2026 - phase 1**. Use that name from here on.

## PRJ-09 An Admin manages the roles and the team
**As:** Nikola (Warehouse admin), with Ana, Luka and Ivana · **Day:** 1 · **Needs:** PRJ-08 done

1. As Nikola, open **Settings**, **Members**. Change Ana's role box from **Member** to **Owner**.
2. Change Ana's role box to **Admin**. As Ana, reload her **Settings** page.
3. As Nikola, change Ana's role box back to **Member**. As Ana, reload.
4. As Nikola, type `<mailbox>+luka` in the search, pick Luka, leave **Member** and click **Add**. As Luka, open **Projects**.
5. As Nikola, click **Remove** on Luka's row. As Luka, reload **Projects**.
6. As Ivana, look at Nikola's row. As Nikola, look at his own row and at Ivana's.

- [x] Choosing **Owner** is refused with "Cannot assign OWNER role", and Ana's role box goes back to **Member**
- [x] Ana's role changes to **Admin** and stays after a reload
- [x] Ana as Admin now has the add form and editable **Name** and **Description**, and still no **Danger zone**
- [x] After Nikola sets Ana back to **Member**, Ana's page is read-only again
- [x] Luka is found by the email start `<mailbox>+luka`, added as **Member**, and **Warehouse Extension 2026 - phase 1** shows in his **Projects** list
- [x] After **Remove** Luka's row is gone, and the project is no longer in his **Projects** list after a reload
- [x] Ivana, the Owner, sees a role box and **Remove** on Nikola's row
- [x] Nikola's row shows only a badge for Ivana's Owner role and for himself, with no role box or **Remove**

## PRJ-10 Hiding job sections changes the job screen for everyone
**As:** Ivana (Office admin), then Luka, then Ana · **Day:** 1 · **Needs:** ONB-23 and ONB-28 done (Office hides Relationships, Links and Job status history, and "Fit partition walls on the 3rd floor" has Ivana's note)

1. As Ivana, open **Office Refurbishments**, **Settings**, **Job screen**. Switch **Notes** off.
2. Open "Fit partition walls on the 3rd floor".
3. As Luka, open "Replace ceiling lights in the open-plan office" in Office.
4. As Ana, open "Tile bathroom at 14 Elm St" in Residential.
5. As Ivana, go back to **Settings** and switch **Notes** on. Open "Fit partition walls on the 3rd floor" again.

- [x] Ivana, an Admin and not the Owner, can switch **Notes** off, and a message such as "Notes now hidden on jobs" appears
- [x] The partition job shows no notes section for Ivana
- [x] Luka's job in Office shows no notes section either
- [x] Ana's job in Residential still shows its notes, because the setting is per project
- [x] After switching **Notes** on, "Frames delivered to site, waiting for plasterboard." is back with its author and time, so nothing was deleted
- [x] **Relationships**, **Links** and **Job status history** are still off and **Notes** and **Approvals** are on, as ONB-23 left them

## PRJ-11 An Admin turns the completion approval gate on and off
**As:** Ivana (Office admin) · **Day:** 1 · **Needs:** PRJ-10 done. Office's gate is off (ONB-23).

1. Open **Office Refurbishments**, **Settings**, **Job screen**. Switch **Require approval to complete jobs** on.
2. Reload the page.
3. Switch it off again.

- [x] Switching it on shows "Jobs now need approval to complete"
- [x] After the reload the gate is still on
- [x] Switching it off shows "Jobs no longer need approval to complete"

Leave the gate off, as ONB-23 set it.

## PRJ-12 Project links
**As:** Ana (Residential owner), then Sara (member), Nikola and Petra · **Day:** 1 · **Needs:** ONB-22 done and Job links bought (ONB-08). Don't confuse this with the **Links** section on a job, which `04-jobs.md` covers.

1. As Ana, open **Residential Fit-outs**. Click **Links** in the top bar.
2. Click **+ Add link**. Type `https://www.example.com/residential/floor-plans` and the label `Floor plans`. Click **Save**.
3. Click **+ Add link**. Type `https://www.figma.com/file/k4tch3n/kitchen-layouts` and look at the label box. Change the label to `Kitchen layouts` and click **Save**.
4. Click **+ Add link**. Type `https://www.example.com/handover-pack` with no label. Click **Save**.
5. Click **+ Add link**. Type `javascript:alert(1)` and click **Save**. Replace it with `data:text/html,hello` and click **Save**. Replace it with `floor plans` and click **Save**. Click **Cancel**.
6. Click the pencil icon on **Floor plans**, change the label to `Floor plans rev 2` and click **Cancel**. Click the pencil again, change the label to `Floor plans rev 2` and the URL to `https://www.example.com/residential/floor-plans-v2`, and click **Save**.
7. Click the **×** on the handover link and confirm.
8. As Sara, open **Residential Fit-outs**, **Links**. Click **+ Add link**, type `https://www.example.com/residential/client-contacts` with the label `Client contact sheet` and click **Save**.
9. As Ana, reload and open **Links**.
10. As Nikola, open **Office Refurbishments**, **Links**.
11. As Petra, click **Links** in the top bar of her project.

- [x] The list says "No links yet." before the first link
- [x] **Floor plans** shows in the list and opens its address in a new tab when clicked
- [x] Typing the Figma address fills the label with "Figma" by itself
- [x] The link with no label shows its address as the text
- [x] The **Links** item in the top bar shows the number of links
- [x] `javascript:alert(1)` is refused with "URL is invalid or uses a disallowed scheme" and nothing is added
- [x] `data:text/html,hello` is refused with the same message
- [x] `floor plans` (no scheme) is refused with the same message
- [x] **Cancel** on the edit keeps the old label, and **Save** replaces both the label and the address
- [x] The **×** asks for confirmation first, then the link is gone
- [x] Sara (a Member) can add a link
- [x] Sara sees no pencil icon and no **×** on any link, including her own
- [x] Ana sees Sara's **Client contact sheet** and has the pencil and **×** on it
- [x] Office's **Links** says "No links yet.", so links don't cross projects
- [x] Petra's **Links** item has a lock, and clicking it opens **Organisation Settings**, not a dropdown

Residential ends with three links: **Floor plans rev 2**, **Kitchen layouts** and **Client contact sheet**.

## PRJ-13 Luka sees the project screens in Serbian
**As:** Luka (member, Serbian UI) · **Day:** 1 · **Needs:** ONB-33 and PRJ-12 done. Don't expect exact English text.

1. Open **Projects**, click the tabs, then open the gear icon of Residential Fit-outs.
2. Look at every section of the settings page.
3. Open the project's **Links** item in the top bar.

- [x] The heading, tabs, tab counts and badges of the project list are in Serbian, with no English words left
- [x] The settings page headings, labels, role names and the read-only note on the **Job screen** are in Serbian, and no raw text keys appear
- [x] The project names, descriptions and link labels stay as typed
- [x] The links panel is in Serbian, shows **Floor plans rev 2**, **Kitchen layouts** and **Client contact sheet**, and has no edit or delete controls
- [x] The **Add link** control is there, because a Member may add links, and the page doesn't break

## PRJ-14 Delete a project
**As:** Ivana (Warehouse owner), then Nikola and Ana · **Day:** 5 · **Needs:** PRJ-04 to PRJ-09 done. Nothing later uses this project.

1. As Ivana, open **Warehouse Extension 2026 - phase 1**, **Settings**, **Danger zone**. Click **Delete project**.
2. In the window, type the name in lower case.
3. Click **Cancel**. Click **Delete project** again.
4. Type the name exactly as it is: `Warehouse Extension 2026 - phase 1`. Click **Delete project** in the window.
5. As Nikola and as Ana, open **Projects**, then open the project switcher in the top bar of Residential.

- [x] The window "Delete project?" says the project and all its jobs, notes and approvals will be deleted and that this can't be undone
- [x] The **Delete project** button in the window stays disabled until the name matches exactly, so lower case doesn't work
- [x] **Cancel** closes the window and the project is still there
- [x] The exact name enables the button, and deleting opens **Projects**, where **Warehouse Extension 2026 - phase 1** is gone and the **Active** count is 2
- [x] Nikola's and Ana's **Projects** lists no longer show it
- [x] Neither switcher lists it any more

## PRJ-15 Completing a project is refused while it has open jobs
**As:** Ana (Spring owner) · **Day:** 6 · **Needs:** ONB-28 done (the boiler job is in Spring Maintenance 2026) and **NTF-21 done**: that scenario adds Sara to Spring Maintenance 2026 on day 2 and then removes her again, and a completed project refuses changes, so don't run this before it. Spring should hold only the boiler job. If anything else is still open there, complete it first, so the count below is 1.

1. Open **Spring Maintenance 2026**, **Settings**, **Danger zone**. Click **Complete project** while "Quarterly boiler service at Hartley Court" is still **New**.
2. Open the boiler job and click **Start**. Go back to **Settings** and click **Complete project** again.
3. Open the boiler job, click **Complete** and confirm with **Mark Complete**.

- [x] The first attempt is refused with "Cannot complete project: 1 job(s) …" and the project stays active (**Danger zone** still says **Complete this project**)
- [x] The message doesn't call the job In Progress or Blocked while it is New (known wording issue: the backend text always says "IN_PROGRESS or BLOCKED", but a New job blocks too, so this is expected to fail, job to be linked)
- [x] The second attempt, with the job **In Progress**, is refused with the same count
- [x] The job becomes **Completed**

## PRJ-16 Complete the project and see it go read-only
**As:** Ana (Spring owner) · **Day:** 6 · **Needs:** PRJ-15 done

1. Open **Spring Maintenance 2026**, **Settings**, **Danger zone** and click **Complete project**.
2. Look at the page, then at **Job screen**.
3. Open **Projects**. Click the **Completed** tab.
4. Open **Dashboard** for Spring Maintenance 2026, then **Jobs**.
5. Open the boiler job.
6. Open **Settings**, change **Name** to `Spring Maintenance 2026 (closed)` and click **Save changes**. If it saves, set the name back.
7. Open **Residential Fit-outs** and click the project name in the top bar.

- [x] The project completes and **Settings** shows "This project is completed and no longer accepts changes."
- [x] **Danger zone** now offers **Reactivate this project** with a **Reactivate** button
- [x] The **Job screen** switches and the gate switch are greyed out, with no "Only project Owners and Admins" note
- [x] **Projects** shows **Active** 1, **Completed** 1 and **All** 2, and Spring Maintenance 2026 is under **Completed** with a **Completed** badge and no **Owner** badge
- [x] **Dashboard** shows "This project is completed. It is now read-only."
- [x] **+ New Job** on **Jobs** is disabled
- [x] The boiler job has no **Start**, **Complete** or **Reopen** buttons
- [x] **Edit** on the boiler job is disabled
- [x] The boiler job has no form to add a note
- [x] Renaming the completed project is refused (known bug: `ProjectService.update` has no completed-project check and the settings page keeps the name editable, so this is expected to fail, job to be linked)
- [x] The switcher in Residential no longer lists Spring Maintenance 2026

## PRJ-17 Reactivate, then complete again
**As:** Ana (Spring owner) · **Day:** 6 · **Needs:** PRJ-16 done. Spring Maintenance 2026 must end this scenario **Completed**.

1. Open **Spring Maintenance 2026**, **Settings**, **Danger zone** and click **Reactivate**.
2. Look at the page, then open **Jobs**.
3. Open **Projects**.
4. Go back to **Settings** and click **Complete project** again.

- [x] The banner disappears and **Danger zone** offers **Complete this project** again
- [x] The **Job screen** switches can be changed again
- [x] **+ New Job** is enabled again
- [x] Spring Maintenance 2026 is back under the **Active** tab
- [x] Completing again works at once, with no open-jobs refusal, because the boiler job is **Completed**
- [x] Spring Maintenance 2026 is under the **Completed** tab again

## Not manual
- A blank name, a name over 255 characters and more than 20 or a blank block reason are refused by the API: covered by the Cypress tests `a blank/whitespace name 400s…` and `more than 20 block reasons, or a blank one in the list, 400s on create` in `frontend/cypress/e2e/projects/projects.cy.ts`. PRJ-02 checks the 80-character form limit.
- The same project name under two different owners is allowed and under one owner refused, and a name is free again after a soft delete: covered by the Cypress test `a duplicate name under the same owner 409s…` and by `ProjectIntegrationTest` (`Should return 409 when project name already exists for the same owner`, `Should return 201 when project name is reused after soft delete`).
- A Member can't update, complete or delete a project, an Admin can't complete or delete it, and an org member who isn't in the project is refused: covered by `ProjectIntegrationTest` (`Should return 403 when MEMBER tries to update project`, `Should return 403 when ADMIN tries to delete project`, `Should return 403 when ADMIN tries to change project status`, `updateProject_shouldReturn403_whenCallerIsOrgMemberButNotProjectMember`) and the Cypress test `a MEMBER gets 403 updating project details; an ADMIN (not OWNER) gets a stricter 403…`.
- The project list filter by status (`?status=`): covered by `ProjectIntegrationTest` (`Should return only completed projects when filtered by COMPLETED`, `Should return all projects when filtered by ALL`). The page always loads every project and filters in the browser.
- The directory API `GET /api/organisations/{orgId}/projects/directory` (`ProjectDirectoryController`) refusing a plain org member, a caller outside the org and a call with no login, and its sort order: covered by `ProjectDirectoryIntegrationTest`. PRJ-04 checks what the switcher shows each role. The switcher sorts by recently opened, not by member count, so the API sort isn't visible there.
- A project's friendly ID or its UUID both work in API paths, and an unknown ID returns 404: covered by `FriendlyIdRoutingIntegrationTest` and `FriendlyIdResolverTest`.
- Adding someone from another organisation to a project (JOB-393), adding someone twice, assigning or changing a role to Owner, changing or removing the Owner, and a membership id from another project: covered by `ProjectMemberIntegrationTest` and the Cypress test `member-management edge cases…`. The search can only return Brightline people (PRJ-06), so the UI can't send the cross-org case.
- Removing the only assignee of a schedule pauses it: covered by `ProjectMemberIntegrationTest` (`removeMember — auto-pauses schedule when removed member was its only assignee`) and checked from the schedules side in `08-schedules.md`. Nobody is removed from a project with schedules in this file.
- The settings API (gate and hidden sections): a Member gets 403, a completed project gets 409, an unknown section gets 400, the gate can't be turned on without an active Approvals add-on or while the subscription is past due, and the section endpoints keep working when a section is hidden: covered by `ProjectSettingsIntegrationTest` and `ProjectJobSectionsIntegrationTest`. ONB-31 checks the locked gate in the UI.
- A Member can't edit or delete a project link, a member of another project can't touch the links, and project links appear in the project list and detail responses: covered by `ProjectLinkIntegrationTest`. The Job links add-on gate on `ProjectLinkController` is the shared `@RequiresAddon` check, covered by `RequiresAddonAspectTest`, and PRJ-12 checks the locked **Links** item for Petra.
- A soft-deleted project missing from the project list, and deleting a project answering 204: covered by `ProjectIntegrationTest` (`Should not list soft-deleted projects`, `Should soft delete project and return 204`).

## Findings
