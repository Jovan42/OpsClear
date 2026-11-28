# Production deployment

Production is one DigitalOcean droplet running `docker-compose.prod.yml` from a checkout at
`~/OpsClear`, with settings in `~/OpsClear/.env.prod` (copied from `.env.prod.example`, never
committed). The app is served at **https://opsclear.app**.

## Deploys

`.github/workflows/deploy.yml` runs on every push to `main`: over SSH it does `git pull` and
rebuilds the backend and/or frontend service, depending on which paths changed. It doesn't restart
Keycloak and doesn't re-read changed `.env.prod` values for services it doesn't rebuild. After
changing `.env.prod` or `docker-compose.prod.yml`, run on the droplet:

```sh
cd ~/OpsClear
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

## Launch day: reopen sign-up

Until launch, `OPSCLEAR_SIGNUP_OPEN=false` in `.env.prod` closes organisation creation to everyone
except super users (ADR-0066): `POST /api/organisations` returns 403, the Sign up buttons lead to
`/early-access`, and invites keep working. Empty or unset means open.

On launch day:

1. Set `OPSCLEAR_SIGNUP_OPEN=true` in `~/OpsClear/.env.prod`.
2. Restart the backend so it reads the setting (the frontend asks `GET /api/public/signup`, so it
   needs no rebuild):

   ```sh
   cd ~/OpsClear
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend
   ```

3. Check `curl -s https://opsclear.app/api/public/signup` returns `{"open":true}`, and that the
   landing page Sign up button opens the Keycloak registration again.

## Free trial (ADR-0067)

Every new organisation gets a trial (`OPSCLEAR_TRIAL_DAYS`, default 30) with every add-on, capped at
`OPSCLEAR_TRIAL_MAX_MEMBERS` members and `OPSCLEAR_TRIAL_MAX_PROJECTS` active projects. When the trial ends
without a subscription the org is `LOCKED`. **Lock-out stays off** (`OPSCLEAR_LOCKOUT_ENABLED=false`, the default)
until Paddle is live (JOB-432): while it's off a locked org keeps working and the app shows "contact us" instead of
the paywall. Turn it on (set `true`, restart the backend) only once Paddle live checkout works.

`organisations.trial_ends_at` NULL means "never had a trial", which is `LOCKED` unless the org is internal or paid.
Migration V046 gave every existing non-internal, unpaid org a trial, but orgs created after V046 deployed and before
the trial code (JOB-445) shipped have NULL. Both statements below give those orgs a trial too: they match on
"not internal, not paid", not on `trial_ends_at IS NOT NULL`.

Run them in `psql` on the droplet:

```sh
cd ~/OpsClear
docker compose -f docker-compose.prod.yml --env-file .env.prod exec postgres psql -U opsclear -d opsclear
```

### Once, right after JOB-445 deploys: trials for orgs created in the gap

```sql
UPDATE organisations o
SET trial_ends_at = now() + interval '30 days'
WHERE o.deleted_at IS NULL
  AND o.trial_ends_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM org_subscriptions s
      WHERE s.org_id = o.id
        AND (s.is_internal
             OR (s.paddle_subscription_id IS NOT NULL
                 AND s.subscription_status IS NOT NULL
                 AND s.subscription_status <> 'CANCELED')));
```

Then check `GET /api/organisations/mine` (or the org settings page) shows `access.state` `TRIAL` for a test org and
`INTERNAL` for the OpsClear org.

### Launch day: reset every trial to launch + 30 days

Next to "Launch day: reopen sign-up" above. Every non-internal, unpaid org (with or without a trial so far) gets a
fresh trial from launch day, and the trial email markers are cleared so the ending and ended emails go out again:

```sql
UPDATE organisations o
SET trial_ends_at = now() + interval '30 days',
    trial_ending_email_sent_at = NULL,
    trial_ended_email_sent_at = NULL
WHERE o.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM org_subscriptions s
      WHERE s.org_id = o.id
        AND (s.is_internal
             OR (s.paddle_subscription_id IS NOT NULL
                 AND s.subscription_status IS NOT NULL
                 AND s.subscription_status <> 'CANCELED')));
```

Then check Paddle live (JOB-415 / JOB-432) is on track to be done before day 27 after launch, and turn
`OPSCLEAR_LOCKOUT_ENABLED=true` on only once it is. Until then, expired trials are reported `LOCKED` but not blocked.

## Backups

Daily Droplet Backups plus nightly database dumps to a private DigitalOcean Spaces bucket
(`scripts/backup-db.sh`). Setup, alerting, restore steps and the restore test log:
[BACKUP.md](BACKUP.md).

## Domain and TLS

- DNS (Namecheap): `A @` and `A www` point to the droplet's IP.
- nginx (`frontend/nginx.conf`, in the frontend container) serves `opsclear.app` on 443, redirects
  `www`, port 80 and the old `46-101-205-77.sslip.io` address to `https://opsclear.app`, and
  answers Let's Encrypt's HTTP-01 challenge on port 80 from `/var/www/certbot`.
- `.env.prod`: `APP_URL=https://opsclear.app`. Every URL the app builds (Keycloak hostname, token
  issuer, CORS origin, email links, the frontend's Keycloak URL) comes from it.
- Certificate: Let's Encrypt, issued and renewed by **certbot on the droplet**, in
  `/etc/letsencrypt/live/opsclear.app/`, mounted read-only into the frontend container. The old
  sslip.io certificate stays in `/etc/nginx/certs/` for the redirect.
- Renewal: certbot's systemd timer runs `certbot renew` twice a day through the webroot; the deploy
  hook `/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh` reloads nginx after a renewal.
  Check with `sudo certbot renew --dry-run`.

### First-time setup (done once, JOB-414)

nginx won't start without the certificate, so the certificate comes first, issued with certbot's
own temporary web server while the frontend is stopped (about 30 seconds of downtime).

```sh
# 1. Firewall: ports 80 and 443 open (also check any DigitalOcean cloud firewall)
sudo ufw status
sudo ufw allow 80/tcp && sudo ufw allow 443/tcp

# 2. certbot and the webroot folder
sudo apt update && sudo apt install -y certbot
sudo mkdir -p /var/www/certbot

# 3. Issue the certificate (frontend stopped so certbot can use port 80)
cd ~/OpsClear
docker compose -f docker-compose.prod.yml --env-file .env.prod stop frontend
sudo certbot certonly --standalone -d opsclear.app -d www.opsclear.app \
  -m hello@opsclear.app --agree-tos -n
docker compose -f docker-compose.prod.yml --env-file .env.prod start frontend

# 4. .env.prod: add APP_URL=https://opsclear.app and set APP_HOST=opsclear.app

# 5. Merge the PR, wait for the deploy action, then restart everything with the new settings
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 6. Point the Keycloak client at the new URL (root URL for "Back to OpsClear", login/logout URLs)
./keycloak/apply-realm-config.sh docker-compose.prod.yml .env.prod

# 7. Switch renewals to the webroot (no downtime from now on) and reload nginx after each renewal
sudo certbot certonly --webroot -w /var/www/certbot --cert-name opsclear.app \
  -d opsclear.app -d www.opsclear.app --force-renewal -n
printf '#!/bin/sh\ncd %s && docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T frontend nginx -s reload\n' \
  "$HOME/OpsClear" | sudo tee /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
sudo /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
sudo certbot renew --dry-run
```

**Rolling back:** remove `APP_URL` from `.env.prod`, revert the PR on `main` (the deploy action
redeploys the old nginx config), and run the `up -d --build` command above.

## Domain email

Set up by hand on 2026-11-25 (ADR-0064, ADR-0059). Written down here so the server and the DNS can
be rebuilt.

- **Mailbox: Namecheap Private Email.** One mailbox for `opsclear.app`. `support@opsclear.app` is
  an alias of it and is the address customers see (Reply-To, email footers, the app and the site).
  `hello@opsclear.app` is certbot's contact and receives the DMARC reports.
- **App sender: Brevo**, SMTP relay `smtp-relay.brevo.com` on port **2525** (DigitalOcean blocks
  587 and 465). Keycloak (verification, password reset) and the backend (invites) both send as
  `OpsClear <no-reply@opsclear.app>`, with `Reply-To: support@opsclear.app`.

### DNS records (Namecheap, Advanced DNS)

| Type | Host | Value |
|---|---|---|
| MX | `@` | `mx1.privateemail.com`, priority 10 |
| MX | `@` | `mx2.privateemail.com`, priority 10 |
| TXT | `@` | `v=spf1 include:spf.privateemail.com include:spf.brevo.com ~all` (the only SPF record) |
| TXT | `privateemail._domainkey` | DKIM key from Namecheap Private Email |
| CNAME | `brevo1._domainkey`, `brevo2._domainkey` | DKIM targets given by Brevo |
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:hello@opsclear.app` |
| TXT | `@` | `brevo-code:...` (Brevo domain verification) |
| CNAME | `em`, `img.em`, `r.em` | Brevo tracking targets given by Brevo |

**Keep Mail Settings on Custom MX.** In Namecheap Advanced DNS, the "Mail Settings" box must stay
on **Custom MX** with the MX records above. Selecting "Private Email" makes Namecheap write its own
SPF record, which drops `include:spf.brevo.com`, and app emails start failing SPF without any error.

Check the records from outside with, for example, `dig +short MX opsclear.app`,
`dig +short TXT opsclear.app` and `dig +short TXT _dmarc.opsclear.app`.

### Settings in `.env.prod`

| Variable | Prod value | Used by |
|---|---|---|
| `SMTP_FROM` | `no-reply@opsclear.app` | backend, Keycloak |
| `SMTP_FROM_NAME` | `OpsClear` | backend, Keycloak (also the Reply-To display name) |
| `SMTP_REPLY_TO` | `support@opsclear.app` | backend (Reply-To and the invite footer), Keycloak (Reply-To) |

`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_AUTH` and `SMTP_STARTTLS` hold the
Brevo connection, see `.env.prod.example`. `SMTP_REPLY_TO` is optional: empty means no Reply-To and
no footer in backend emails. The Keycloak footer comes from the email theme
(`keycloak/themes/opsclear/email/messages/`, key `supportFooter`), not from this variable.

### After a change

1. Restart the backend so it reads the new values:
   `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend`.
2. Apply the settings to Keycloak: `scripts/keycloak-apply-email.sh` (Keycloak keeps its SMTP
   settings in the realm, not in the env).
3. Send an invite and a password reset, and check the sender, the Reply-To and the footer.
