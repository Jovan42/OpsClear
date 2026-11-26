# Billing and add-ons

Last run:
Guide: /guide/subscription

This file covers the subscription and its catalog, buying and changing the plan through Paddle (sandbox),
cancelling and resuming, the billing history, the credit balance, what each add-on hides or blocks, and a
past-due payment. The rules are in [ADR-0030](../dev/decisions/0030-subscription-data-model.md),
[ADR-0031](../dev/decisions/0031-feature-gating.md) and [ADR-0044](../dev/decisions/0044-paddle-payment-integration.md).
It uses the world built by `01-onboarding.md` (see the [README](README.md#test-world)): Brightline Renovations
with every add-on bought, and Pine & Pixel Studio with none. Run `01-onboarding.md` first.

**Rules to check throughout:**

- Only the org Owner sees and changes the subscription and the billing history. Admins and Members never see them.
- Selecting a plan is not access. A feature works only when Paddle has confirmed a payment for it.
- Adding something is charged right away. Removing something costs nothing now and takes effect when the paid
  period ends.
- A past-due subscription keeps everything readable and blocks changes to add-on features.

**Where things are.** Everything billing lives on **Organisation Settings** (user menu, **Organisation**):
**Subscription** (base plan, add-ons, the total and **Save subscription**), the **Billing** box (status and the
payment buttons), **Billing history** (click it open) and, for Owners and Admins, **Credit balance**.

**Paddle sandbox.** Pay with the test card `4242 4242 4242 4242`, any future expiry date, CVC `100`, and any name
and billing country. Every payment form must send its requests to Paddle hosts with `sandbox` in the name. After a
payment the page shows **Processing your payment** and updates by itself, which can take a moment.

**Which org changes.** Brightline is the live test world for every other area, so these scenarios leave its plan
alone. All the buying, changing and cancelling happens in **Pine & Pixel Studio**, which is deleted after the run.
The only exception is BIL-20, which is optional and recovers at once.

**Luka's language.** Luka uses the Serbian UI. Where he reads text, the quoted English is the English wording:
read the Serbian equivalent and tick when it means the same.

## BIL-01 Paddle is still in sandbox
**As:** Developer (no login) · **Day:** 1 · **Needs:** access to the prod `.env.prod` and the Paddle dashboard. Stop the whole run if any check fails.

1. On the VPS, open `.env.prod` and look at `PADDLE_CLIENT_TOKEN` and `PADDLE_API_KEY`. Don't paste them anywhere.
2. Open the Paddle dashboard and check which account you are on.

- [ ] `PADDLE_CLIENT_TOKEN` starts with `test_`
- [ ] `PADDLE_API_KEY` starts with `pdl_sdbx_`
- [ ] The Paddle dashboard in use is the Sandbox one

## BIL-02 The catalog: base plan, add-ons and prices
**As:** Ana (org owner) · **Day:** 1 · **Needs:** `01-onboarding.md` done. Don't click **Save subscription** in this scenario.

1. Open **Organisation Settings**, then **Subscription**.
2. Drag **Team members** to its smallest and largest value, and **Active projects** to its smallest and largest value.
3. Note **Base price**, then click the **Notes** card to deselect it and click it again.
4. Switch **Annual billing** on, look at the price on a card and at the total, then switch it off.
5. Click **See all features →** next to **Subscription**.

- [ ] The two sliders show "Up to N" values, **Active projects** ends in **Unlimited**, and **Base price** changes with the sliders
- [ ] The **Add-ons** list has 11 cards: Dashboard, Approvals, Notes, Job status history, Milestones, Job relationships, API keys, Job templates, Recurring scheduling, Job links and Job types, each with a price and a one-line description
- [ ] Deselecting **Notes** removes its green border and lowers **Monthly total** by exactly its price, and selecting it again adds it back
- [ ] With **Annual billing** on the label reads **Monthly total (annual)**, the card prices change, and "Save … /yr compared to monthly." shows
- [ ] **See all features →** opens `/features`
- [ ] Leaving the page without saving changes nothing: Organisation Settings shows the plan from ONB-08 again

## BIL-03 Brightline's subscription is what was bought
**As:** Ana (org owner) · **Day:** 1 · **Needs:** ONB-08 done

1. Open **Organisation Settings** and look at **Subscription** and the **Billing** box.
2. Click **Billing history** open.

- [ ] All 11 add-on cards have a green border, and **Monthly total** equals the amount paid in ONB-08
- [ ] The **Billing** box shows **Active**, with **Update payment method** and **Cancel subscription**, and no "set to cancel" text and no **Plan change scheduled** box
- [ ] **Billing history** lists the ONB-08 payment with a date, a status (completed) and the same amount

## BIL-04 Every add-on is unlocked in Brightline
**As:** Ana (org owner), then Luka (member, Residential Fit-outs) · **Day:** 1 · **Needs:** BIL-03 done

1. As Ana, open Residential Fit-outs and look at the project's top bar. Open **Settings** in the project, scroll to **Job screen**.
2. Open any job and scroll the whole page.
3. Open **Account settings** and look for **API keys**. Open **Organisation Settings** and look for **Org Templates**.
4. As Luka, open Residential Fit-outs and its top bar, then any job assigned to him.

- [ ] Ana's top bar shows Dashboard, Stats, Jobs, Milestones, Templates, Types, Schedules, Approvals, Settings and Links, none with a lock
- [ ] **Require approval to complete jobs** can be switched on and off, and **Sections shown on jobs** lists all five sections as available, not "Not in your plan"
- [ ] The job page shows Notes, Approvals, Job history, Relationships and Links, and no row of locked sections
- [ ] **API Keys** shows the key list, not "not included in your plan", and **Org Templates** is on Organisation Settings
- [ ] Luka's top bar shows the same items without a lock, except **Approvals**, which only Owners and Admins of the project see
- [ ] Luka sees the notes, history, relationships and links on his job, with nothing locked

## BIL-05 Billing is for the Owner; credit balance is for Owners and Admins
**As:** Nikola (org admin), Luka (member), Ana (owner) · **Day:** 1 · **Needs:** BIL-03 done

1. As Nikola, open **Organisation Settings** and look at every section, including the bottom.
2. As Luka, do the same.
3. As Ana, do the same and read **Credit balance**.

- [ ] Nikola sees **Credit balance** with a number, its description ("Credits earned from feedback and discretionary grants …") and no **Subscription**, **Billing** box, **Billing history** or **Danger zone**
- [ ] Luka sees none of **Credit balance**, **Subscription**, **Billing history** and **Danger zone**
- [ ] Ana sees **Credit balance** as well as **Subscription**, **Billing history** and **Danger zone**
- [ ] The credit balance is 0 for both Ana and Nikola, because no credit has been granted yet
- [ ] No past-due banner shows for Ana, Nikola or Luka, since Brightline's subscription is **Active**

## BIL-06 Pine & Pixel: locked project pages
**As:** Petra (owner, Pine & Pixel Studio) · **Day:** 1 · **Needs:** ONB-31 done (the project "Brand refresh for Juniper Cafe" exists), no add-ons bought

1. Open the project. Look at its top bar.
2. Click **Dashboard**, then the browser's Back button.
3. Repeat for **Stats**, **Milestones**, **Templates**, **Types**, **Schedules** and **Approvals**.

- [ ] The top bar shows **Dashboard**, **Stats**, **Milestones**, **Templates**, **Types**, **Schedules**, **Links** and **Approvals** with a lock
- [ ] Each of the seven locked pages (Dashboard, Stats, Milestones, Templates, Types, Schedules, Approvals) shows "<name> is not included in your plan" with **Upgrade in org settings**
- [ ] **Upgrade in org settings** opens **Organisation Settings**
- [ ] **Jobs** and **Settings** are the only items without a lock

## BIL-07 Pine & Pixel: locked parts of a job
**As:** Petra · **Day:** 1 · **Needs:** BIL-06 done, the job "Design menu cards for Juniper Cafe" exists

1. Open "Design menu cards for Juniper Cafe". Scroll the whole page.
2. Click **Upgrade in org settings** in the row at the bottom.
3. Open the project's **Settings**, scroll to **Job screen**, and then to **Require approval to complete jobs**.
4. Back on the job, click **Complete**.

- [ ] The job page has no Notes, Approvals, Job history, Relationships or Links section, and a row of locked sections names all five with **Upgrade in org settings**
- [ ] The row's button opens **Organisation Settings**
- [ ] All five **Sections shown on jobs** are off and marked **Not in your plan**
- [ ] **Require approval to complete jobs** is locked with "Requires an active Approvals add-on."
- [ ] **Complete** completes the job at once, with no approval request, because the gate can't be on

Afterwards reopen the job (set it back to **In Progress**) so later scenarios can use it.

## BIL-08 Pine & Pixel: locked API keys and org templates
**As:** Petra · **Day:** 1 · **Needs:** BIL-06 done

1. Open **Account settings** (user menu) and scroll to the **API keys** section.
2. Open **Organisation Settings** and look for **Org Templates**.

- [ ] **API keys** shows "API Keys is not included in your plan" with **Upgrade in org settings**, and no button to create a key
- [ ] **Org Templates** isn't on **Organisation Settings**
- [ ] **Subscription** lists all 11 add-on cards without a green border

## BIL-09 Preview an upgrade without paying
**As:** Petra (owner, Pine & Pixel Studio) · **Day:** 1 · **Needs:** BIL-08 done

1. On **Organisation Settings**, **Subscription**, click the **Notes** card so it has a green border.
2. Look under **Monthly total**, then click **Save subscription**.
3. Read the window, then close it without confirming.

- [ ] Under **Monthly total** an "Extra to pay now" amount shows once the change is calculated
- [ ] The window is titled **Confirm upgrade**, says "You'll be charged now, prorated for the rest of your current billing period.", and shows **Charged now** with the same amount
- [ ] Closing the window changes nothing: no payment appears in **Billing history**, and the job still has no Notes section

## BIL-10 Buy one add-on: charged now, unlocked after the webhook
**As:** Petra · **Day:** 1 · **Needs:** BIL-09 done, **Notes** still selected

1. Click **Save subscription**, then **Confirm change**.
2. Wait on the page, then open **Billing history**.
3. Open "Design menu cards for Juniper Cafe". In **Notes** add "Client wants A5 cards, double-sided".
4. Open the project's **Settings**, **Job screen**.

- [ ] A payment of the **Charged now** amount appears in **Billing history**, with a date and a status
- [ ] **Notes** keeps its green border after the update, without a refresh
- [ ] The job shows a **Notes** section, the note is saved, and the other four sections stay locked
- [ ] **Notes** is available under **Sections shown on jobs**, and the other four still say **Not in your plan**
- [ ] The top bar still shows a lock on Dashboard, Stats, Milestones, Templates, Types, Schedules, Links and Approvals

## BIL-11 Adding and removing in one change is refused
**As:** Petra · **Day:** 1 · **Needs:** BIL-10 done

1. In **Subscription**, click **Job links** (to add it) and **Notes** (to remove it).
2. Click **Job links** again so only **Notes** is deselected.

- [ ] A warning says "You're adding something more expensive and removing something cheaper in the same change — please save these as two separate changes."
- [ ] **Save subscription** is disabled while both changes are selected
- [ ] With only **Notes** deselected the warning is gone and **Save subscription** is enabled

## BIL-12 Remove an add-on: nothing changes until the period ends
**As:** Petra · **Day:** 1 · **Needs:** BIL-11 done, only **Notes** is deselected

1. Click **Save subscription**.
2. Read the window, click **Confirm change**.
3. Open the job and its **Notes** section.
4. Click **Cancel pending downgrade**.

- [ ] The window is titled **Confirm plan change** and says "No charge now. You'll keep your current plan until <date>, when it will switch to the new plan."
- [ ] A **Plan change scheduled** box appears with "Your plan will switch to the following on <date>" and a list that has no Notes
- [ ] **Billing history** has no new payment, and the Notes section still works
- [ ] **Cancel pending downgrade** removes the box, and **Notes** has its green border back

## BIL-13 Raise the base plan
**As:** Petra · **Day:** 1 · **Needs:** BIL-12 done

1. Drag **Team members** one step up. Read **Extra to pay now**.
2. Click **Save subscription**, then **Confirm change**, then wait.
3. Open **Billing history**.

- [ ] The window is **Confirm upgrade** with **Charged now**, matching **Extra to pay now**
- [ ] After the update the slider stays at the new value and **Base price** is the new price
- [ ] **Billing history** has one more payment for that amount

## BIL-14 Switch to annual billing
**As:** Petra · **Day:** 1 · **Needs:** BIL-13 done

1. Switch **Annual billing** on. Look at **Monthly total (annual)**.
2. Click **Save subscription** and read the window.
3. Click **Confirm change**.

- [ ] **Monthly total (annual)** is lower than the monthly total, and "Save … /yr compared to monthly." shows
- [ ] The window says either **Confirm upgrade** with a **Charged now** amount, or **Confirm plan change** with the date it starts, and nothing is charged before **Confirm change**
- [ ] After **Confirm change** the Billing box still shows **Active** and the list of add-ons is unchanged

## BIL-15 Update the payment method
**As:** Petra · **Day:** 1 · **Needs:** BIL-14 done

1. In the **Billing** box, click **Update payment method**.
2. Enter the test card from the top of this file with a different expiry date, and save.
3. Click **Update payment method** again and leave the form without saving.

- [ ] A payment form opens on the page and its requests go to Paddle hosts with `sandbox` in the name
- [ ] After saving, the Billing box still shows **Active** and no payment is added to **Billing history**
- [ ] Leaving the form without saving changes nothing

## BIL-16 Cancel the subscription
**As:** Petra · **Day:** 1 · **Needs:** BIL-15 done

1. In the **Billing** box, click **Cancel subscription**, read the window, then click **Cancel subscription** in it.
2. Open the job, then the project's top bar.

- [ ] The window is titled **Cancel subscription?** and says "Your subscription will remain active until the end of your current billing period, then it will not renew."
- [ ] The Billing box says "Your subscription is set to cancel on <date>. You can resume it for free at any time before then." and **Cancel subscription** is gone
- [ ] The add-on features she has paid for still work (the Notes section, nothing newly locked)
- [ ] **Resume subscription** is there

## BIL-17 Resume the subscription
**As:** Petra · **Day:** 1 · **Needs:** BIL-16 done

1. Click **Resume subscription**.

- [ ] A toast says "Cancellation removed — your subscription will continue as normal."
- [ ] The "set to cancel" text and **Resume subscription** are gone, and **Cancel subscription** is back
- [ ] **Billing history** has no new payment

## BIL-18 A granted credit is applied to the next charge
**As:** Developer (super-admin, see `14-feedback-admin.md`), then Petra · **Day:** 5+ · **Needs:** the credit grant scenario in `14-feedback-admin.md` has granted credit to Pine & Pixel Studio, and BIL-17 done. Run this after that file.

1. As Petra, open **Organisation Settings** and read **Credit balance**. Leave the page open.
2. In **Subscription**, click one more add-on (for example **Job links**), look at **Credit applied**, then **Save subscription**.
3. Read the window, click **Confirm change**, wait, and read **Credit balance** again.

- [ ] **Credit balance** shows the granted amount (and updates within about 10 seconds of a grant, without a refresh)
- [ ] The window shows **Credit applied** with a negative amount, and **Charged now** is the charge minus the credit
- [ ] After the payment **Credit balance** goes down by the amount that was used, and the payment in **Billing history** shows the reduced amount
- [ ] **Job links** is now unlocked in the project's top bar

## BIL-19 A past-due payment keeps data readable and blocks changes
**As:** Developer, then Petra · **Day:** 5+ · **Needs:** BIL-17 done, a way to fail Pine & Pixel's next payment in Paddle sandbox. The sandbox can't fail a renewal on demand, so try these in order: in the Paddle sandbox dashboard open her subscription, change the payment method to one of Paddle's declining test cards and bill the next period now; or use Paddle's sandbox notification simulator (or replay a past notification) to send `subscription.past_due` for her subscription. A hand-made request to the webhook won't work, because it checks the Paddle signature. If nothing works, mark this scenario and BIL-20 as removed and add "past due: no way to fail a sandbox payment" under **Not manual**. `NTF-25` then stays covered under its own **Not manual**.

1. Put Pine & Pixel's subscription into past due and wait for the webhook.
2. As Petra, open any page, then **Organisation Settings**.
3. Open the job and add a note.
4. Open the job's **Notes** (reading), then create a new job "Design flyer for Juniper Cafe".
5. Click **Update payment method**, enter the test card from the top of this file and save. Wait.

- [ ] A banner on every page says "Your last payment didn't go through — you can still view everything, but new changes are blocked until it's resolved." with **Update payment method**, and the link opens **Organisation Settings**
- [ ] The Billing box shows **Past due**, and "Your last payment didn't go through. Update your payment method to keep your subscription active — your data stays fully visible in the meantime."
- [ ] Adding the note fails with "This organisation's subscription payment is past due — this action is read-only until payment is resolved"
- [ ] The existing notes can still be read, and the new job is created, since only add-on features are blocked
- [ ] Petra gets **Subscription past due** in her notification centre, in the urgent style, once
- [ ] After the new card is accepted the banner disappears, the Billing box shows **Active**, and the note can be added

## BIL-20 Past due in Brightline (only if BIL-19 worked, for NTF-25)
**As:** Developer, then Ana, Nikola, Ivana and Luka · **Day:** 5+, after every other area and just before `15-cross-cutting.md` · **Needs:** BIL-19 ticked, `NTF-25` waiting. Brightline blocks writes to its add-on features until it recovers, so do this last.

1. Put Brightline's subscription into past due the way BIL-19 did, and wait for the webhook.
2. As Ana, Nikola, Ivana and Luka, open any page. Run `NTF-25` now (it plays Ana, Nikola and Ivana).
3. As Luka, add a note on one of his jobs. As Ana, open the **Settings** of Residential Fit-outs.
4. As Ana, click **Update payment method** and pay with the test card. Wait.

- [ ] Ana, Nikola, Ivana and Luka all see the past-due banner, and Ana's **Update payment method** link opens **Organisation Settings**
- [ ] Luka's note fails with the past-due message, and everything on his job stays readable
- [ ] **Require approval to complete jobs** shows "Not enforced: requires an active Approvals add-on." while past due
- [ ] After payment the banner is gone for all four, the Billing box shows **Active**, and Luka can add the note

## Not manual
- Paddle webhook signature checks, unknown customers, bad bodies, and redelivered events that change nothing a second time: covered by `PaddleWebhookIntegrationTest`.
- `subscription.created`, `subscription.activated`, `subscription.updated` (status, scheduled cancellation, billing period start), `subscription.canceled` and `subscription.past_due` setting the stored status, and ignoring `transaction.payment_failed`: covered by `PaddleWebhookIntegrationTest` and `PaddleWebhookServiceTest`.
- A pending downgrade being applied when the billing period rolls over, and a scheduled cancellation ending the subscription (both need a real renewal date): covered by `PaddleWebhookIntegrationTest` and `PaddleWebhookServiceTest`.
- `transaction.completed` detaching a used credit discount, debiting the balance and carrying a partly used discount forward: covered by `PaddleWebhookIntegrationTest`, `CreditGrantPaddleSyncIntegrationTest` and `CreditServiceTest` (BIL-18 is the manual check).
- Granting a credit to Paddle (the one-time discount, minimum amount, rollback on a Paddle error): covered by `CreditGrantPaddleSyncIntegrationTest` and `FeedbackAndCreditsIntegrationTest`.
- Only the Owner can start, update, preview, cancel, resume or cancel a downgrade (403 for Admins and Members), a non-member gets 404, and an internal org is refused (400): covered by `PaddleSubscriptionIntegrationTest` and `SubscriptionIntegrationTest`.
- `GET` and `PUT /api/organisations/{orgId}/subscription`: reading needs membership (404 for strangers, 401 without a login), writing needs the Owner, a downgrade below the current member or active project count is refused: covered by `SubscriptionIntegrationTest` and `SubscriptionServiceTest`.
- `GET /api/subscriptions/catalog` content and prices, and a tier or add-on not yet synced to Paddle ("This plan isn't available for checkout yet"): covered by `SubscriptionCatalogIntegrationTest`, `SubscriptionCatalogServiceTest` and `PaddleSubscriptionIntegrationTest`.
- `GET /api/organisations/{orgId}/credits/balance` is refused for Members (403) and strangers (404): covered by `CreditServiceTest` and `FeedbackAndCreditsIntegrationTest`.
- Every gated endpoint refusing a call with "<ADD-ON> add-on is required to access this feature" (403) when the add-on is missing, only staged, or the org has no subscription, and internal orgs skipping the check: covered by `AddonGatingIntegrationTest`, `RequiresAddonAspectTest` and `AddonAccessCheckerTest` (BIL-06 to BIL-08 are the manual checks of what the UI hides).
- Write calls to gated endpoints refused while past due, reads still allowed: covered by `RequiresAddonAspectTest` and `AddonAccessCheckerTest` (BIL-19 is the manual check, if the sandbox can be put into that state).
- A second cancel, a resume with nothing scheduled, a cancel-downgrade with nothing pending, and a mixed add-and-remove change (409): covered by `PaddleSubscriptionIntegrationTest` and `PaddleSubscriptionServiceTest` (BIL-11 is the manual check of the mixed change).
- The "Subscription required" page that non-owners see before the Owner has paid: it can't be seen in this run, because the Owner pays before anyone is invited (ONB-07, ONB-08).
- A second billing contact for the same email ("Your email is already the billing contact for another organisation's subscription"): covered by `PaddleCustomerServiceTest` and `PaddleCustomerSetupIntegrationTest`.
- The `PaddleWebhookController` itself, called only by Paddle: covered by `PaddleWebhookIntegrationTest`.
- Super-admin credit grant and pricing screens: `14-feedback-admin.md`.

## Findings
