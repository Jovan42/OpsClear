# ADR-0064: Domain Email and Support Contact

**Status:** Proposed
**Date:** 2026-11-26
**Author:** Jovan

## Context

Before the first paying customer, people need a way to reach us, and the app's own emails must come from our domain. ADR-0059 accepted a Gmail sender for testing only and said a domain sender was required before real users sign up. Paddle's domain review and the legal pages (MIL-053) also expect a support address on the site. Today there is none anywhere in the landing page or the app.

Most of the email side was set up by hand on 2026-11-25. This ADR records that setup and decides the remaining code: where replies go, what the emails say about support, and where the address is shown.

Promoted from JOB-428 (Future Consideration, PRJ-002) into PRJ-016 "Go-Live: Email, Operations, Free Trial & Payments" / MIL-050 Domain email and support contact. This ADR is JOB-429.

### Facts from the existing system that shape this design

- **Mailbox:** opsclear.app mail is on **Namecheap Private Email**. `support@opsclear.app` is an alias of the mailbox. A round trip (mail in, reply out as support@) was checked on 2026-11-25. `hello@opsclear.app` is certbot's contact in `docs/dev/DEPLOY.md` and receives DMARC reports.
- **App sender:** Keycloak (verification, password reset) and the backend send through Brevo on port 2525 (ADR-0059). Since 2026-11-25 the sender is `OpsClear <no-reply@opsclear.app>` (`SMTP_FROM`/`SMTP_FROM_NAME` in `.env.prod`, applied to Keycloak with `scripts/keycloak-apply-email.sh`). A password reset on prod arrived from that address, and inbox and spam checks were done.
- **DNS (Namecheap, checked against Namecheap, Cloudflare and Google resolvers):**
  - MX `mx1.privateemail.com` and `mx2.privateemail.com`, priority 10
  - one SPF record: `v=spf1 include:spf.privateemail.com include:spf.brevo.com ~all`
  - DKIM: `privateemail._domainkey` (TXT), and `brevo1._domainkey` / `brevo2._domainkey` (CNAMEs to Brevo)
  - DMARC: `v=DMARC1; p=none; rua=mailto:hello@opsclear.app`
  - Brevo: the `brevo-code` TXT, and the `em`, `img.em` and `r.em` CNAMEs
  - Mail Settings in Namecheap Advanced DNS is **Custom MX**. With "Private Email" selected, Namecheap writes its own SPF record, which drops Brevo.
- **What sends email:** the backend sends only invite emails (`EmailService.sendInvite`). Notifications are in-app only (ADR-0051). Keycloak sends verification and password-reset emails through the `opsclear` email theme (`keycloak/themes/opsclear/email`).
- **No Reply-To anywhere:** `EmailService` sets only `From`. `keycloak-apply-email.sh` sets only `from` and `fromDisplayName`. Replies to an app email go to `no-reply@`.
- **Shared email layout, no footer:** Keycloak's `template.ftl` `emailLayout` macro and the matching `HTML_START`/`HTML_END` in `EmailService` draw a white card with an "OpsClear" header. Neither has a footer. Texts come from `mail/messages_{en,sr}.properties` (backend) and `email/messages/messages_{en,sr}.properties` (Keycloak).
- **In-app entry point:** `UserMenu.tsx` has a **Feedback** item that opens `/feedback`.
- **Public pages:** the landing page (`LandingPage.tsx`, `CTAFooter`), `/features` (`FeaturesPage.tsx`) and `/guide/:slug?` (`GuideLayout.tsx`). Draft PR #613 (legal pages, MIL-053) adds a shared `PublicFooter.tsx` and `LegalLinks.tsx` to these pages and has a `[[SUPPORT_EMAIL]]` placeholder in the legal texts.
- **Layers:** `keycloak/**`, `scripts/**` and `docs/**` are neutral paths (NAMING.md), so they can travel with the backend job.

## Decision

1. **Mail stays on Namecheap Private Email and Brevo**, as set up on 2026-11-25. This ADR records the setup (Facts above) so the server and DNS can be rebuilt. ADR-0059's "domain sender required before real users" is met.
2. **App emails keep `From: OpsClear <no-reply@opsclear.app>` and get `Reply-To: support@opsclear.app`**, in the backend and in Keycloak. A reply lands in the support inbox, and the sender still makes clear the email was automatic.
3. **Every app email gets a small support footer**, in English and Serbian: "Questions? Reply to this email or write to support@opsclear.app", with the address as a `mailto:` link. That covers the invite, verification and password-reset emails.
4. **Signed-in users find support in the user menu:** a **Contact support** item next to **Feedback**, opening `mailto:support@opsclear.app`.
5. **Visitors find support on the public pages:** a `mailto:` link in the footer of the landing page, `/features` and `/guide`.
6. **The address is defined once per side:** one frontend constant, one backend property (`SMTP_REPLY_TO`), and one Keycloak message key per language. Nothing is copied into each template.

## Product decisions

| Topic | Decision |
|---|---|
| Mail host | Namecheap Private Email (already in use). Zoho and other hosts not needed. |
| App sender | `OpsClear <no-reply@opsclear.app>` through Brevo (done on prod 2026-11-25). |
| Replies to app emails | `Reply-To: support@opsclear.app`. Decided over sending from support@ and over relying on a no-reply alias. |
| Email footer | "Questions? Reply to this email or write to support@opsclear.app" (en, sr), grey small text, under the card text. |
| In the app | **Contact support** in the user menu, next to **Feedback**, as a `mailto:` link. |
| Public pages | `mailto:` link in the footer of the landing page, `/features` and `/guide`. No `/contact` page. |
| Legal pages | Use the same address. They fill `[[SUPPORT_EMAIL]]` in PR #613 (MIL-053). |
| Paddle | support@ set as the support email in the seller settings (manual, MIL-053 needs it for the domain review). |
| DMARC | Stays `p=none` for now. Tightening to `quarantine` after a few weeks of clean reports is a later change, not part of this milestone. |

**Serbian texts:** footer "Imate pitanje? Odgovorite na ovaj mejl ili pišite na support@opsclear.app". Menu item "Kontaktirajte podršku". The implementing jobs may adjust wording to match the existing sr locale files.

## Technical design

### Database

No change.

### API

No change.

### Backend

- **Property:** `opsclear.mail.reply-to=${SMTP_REPLY_TO:}` in `application.properties`. Empty by default, so dev (Mailpit), tests and CI behave as today.
- **`EmailService`:** takes the property in its constructor. When it isn't blank, `helper.setReplyTo(replyTo)`. When it's blank, no `Reply-To` header is sent.
- **Footer:** `mail/messages_{en,sr}.properties` get `footer.support` (and an HTML variant if needed for the link). `inviteText` appends the footer line. `inviteHtml` adds it as the last grey paragraph inside the card, with a `mailto:` link, using the existing `SMALL_GREY` style. The address comes from the reply-to property, so the footer is left out when it's blank (dev shows no prod address).
- **Compose:** `docker-compose.prod.yml` passes `SMTP_REPLY_TO: ${SMTP_REPLY_TO:-}` to `backend`. `.env.prod.example` lists it.
- **Tests:** unit tests on `EmailService` (mocked `JavaMailSender`, captured `MimeMessage`): Reply-To set when configured, absent when blank, footer in text and HTML for en and sr, footer absent when blank.

### Keycloak (neutral path, ships with the backend job)

- **`scripts/keycloak-apply-email.sh`:** when `SMTP_REPLY_TO` is set in the env file, also set `smtpServer.replyTo` (and `replyToDisplayName` = `SMTP_FROM_NAME`). It stays optional, so older env files keep working. `--help` lists it.
- **Email theme:** `email/messages/messages_{en,sr}.properties` get `supportFooter` with the address. `template.ftl`'s `emailLayout` renders it after `<#nested>` as a small grey paragraph with a `mailto:` link. The text templates (`email/text/*.ftl` if the theme overrides them, otherwise the base theme's) get the same line where the theme controls them.
- **Prod:** theme changes reach prod Keycloak on the next page load (themes mounted, not cached under `start-dev`, ADR-0059). Reply-To needs the script re-run after `SMTP_REPLY_TO` is added to `.env.prod`.

### Frontend

- **Constant:** `SUPPORT_EMAIL = 'support@opsclear.app'` in one shared module (for example `frontend/src/utils/support.ts`), with a `mailto:` helper.
- **User menu:** a **Contact support** item in `UserMenu.tsx` right after **Feedback**. It's an `<a href="mailto:…">`, not a route. en and sr strings in the existing namespace that holds the menu texts.
- **Public footers:** a support `mailto:` link in the landing `CTAFooter`, the `/features` page footer and the `/guide` footer (logged-out view). If PR #613 has merged by then, add it to its shared `PublicFooter.tsx` instead of three places. Otherwise keep the change small so whichever PR merges second resolves an easy conflict.
- **User guide:** if a guide page covers feedback or help, mention **Contact support** there (en, sr).
- **E2E:** the landing footer support link has the right `mailto:` href (`@smoke` if cheap), and the user menu shows **Contact support**.

### Docs

- **`docs/dev/DEPLOY.md`:** a "Domain email" section: where mail is managed (Namecheap Private Email, Brevo), the DNS records listed in Facts, the Custom MX warning, the env vars (`SMTP_FROM`, `SMTP_FROM_NAME`, `SMTP_REPLY_TO`) and the steps after a change (restart backend, re-run `scripts/keycloak-apply-email.sh`).
- **ADR-0059:** a dated note that the domain sender is in place (2026-11-25), pointing to this ADR.

### Constraints & edge cases

- **Blank `SMTP_REPLY_TO`:** no Reply-To header and no backend footer, so local and CI emails stay as they are. Keycloak's footer comes from the theme and does show the address in dev, which is harmless.
- **Keycloak theme vs base templates:** only the HTML layout and the messages are themed today. If a text template isn't overridden, the plain-text part of that email may lack the footer. Acceptable; most clients show the HTML part.
- **Address must work before it's shown:** it does (round trip checked 2026-11-25), so the code can merge and deploy straight away.
- **Spam:** adding a Reply-To on another address of the same domain doesn't affect SPF, DKIM or DMARC alignment.
- **Custom MX:** switching Namecheap Mail Settings back to "Private Email" silently drops Brevo from SPF. Recorded in DEPLOY.md.

## Alternatives considered

### Send app emails from support@ (decided against)

One address for everything and no Reply-To code. Rejected: automatic emails and real support mail would share a sender, so a support reply could be mistaken for an automatic one, and support@'s reputation would carry every bulk send.

### Rely on the no-reply@ alias (decided against)

No code change if no-reply@ is an alias of the mailbox. Rejected: clients still show "no-reply", which tells people not to reply, and it depends on an alias nobody remembers.

### Zoho Mail (decided against)

The Future Consideration first proposed Zoho. Rejected: mail was already on Namecheap Private Email with the domain, so a second host would only add cost and DNS conflicts.

### A /contact page (decided against)

A page with the address, company details and maybe a form. Rejected for now: a `mailto:` link in every footer and in the user menu is enough, and the legal pages (MIL-053) carry the company details. A form would need spam protection and storage.

### Support address in an environment variable on the frontend (decided against)

Rejected: the address doesn't change between environments, and a build-time variable adds config for nothing. One constant is simpler.

## Consequences

### Positive

- Customers can reach us from the site, the app and every email.
- Replies to app emails reach a person.
- ADR-0059's requirement is met and the mail setup is written down for rebuilds.
- Paddle's domain review has the support address it needs (MIL-053).

### Negative

- Support mail arrives in one personal mailbox. Handing support to someone else later means moving the alias.
- The address is in the backend, Keycloak and frontend (one place each). Changing it later touches three places.

### Neutral

- DMARC stays at `p=none` until a later tightening.
- The legal pages PR (#613) and the frontend job here both touch the public footers.

## Implementation order

Each job is one layer (JOBS.md). Neutral paths (`keycloak/**`, `scripts/**`, `docs/**`) go with the backend job.

1. **Backend: Reply-To and support footer in app emails.** `opsclear.mail.reply-to` / `SMTP_REPLY_TO`, `EmailService` Reply-To and footer (en, sr) with unit tests, compose and `.env.prod.example`, `keycloak-apply-email.sh` `replyTo`, the Keycloak email theme footer (en, sr), the `DEPLOY.md` "Domain email" section and the ADR-0059 note. After merge (developer): add `SMTP_REPLY_TO=support@opsclear.app` to `.env.prod`, restart the backend, re-run the script, and check a password reset and an invite show the Reply-To and the footer.
2. **Frontend: support address in the user menu and the public footers.** Shared constant, **Contact support** in `UserMenu.tsx`, `mailto:` links on the landing footer, `/features` and `/guide` (en, sr), guide mention if relevant, E2E. Not blocked by job 1. Best started after PR #613 merges, otherwise kept small for an easy conflict.
3. **Chore: Paddle support email.** Set `support@opsclear.app` in the Paddle seller settings (manual). Can be done any time; MIL-053's domain review needs it.

## References

- JOB-428 (Future Consideration), JOB-429 (this ADR), PRJ-016 / MIL-050
- ADR-0059 Email Verification and Transactional Email
- ADR-0051 Notifications (in-app only)
- PR #613 (legal pages, MIL-053), PR #614 (backups, uses the same `.env.prod` conventions)
- `backend/src/main/java/com/opsclear/service/EmailService.java`, `keycloak/themes/opsclear/email/`, `scripts/keycloak-apply-email.sh`, `frontend/src/components/UserMenu.tsx`, `frontend/src/features/landing/LandingPage.tsx`
