CREATE TABLE email_signups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list            VARCHAR(50)  NOT NULL,
    email           VARCHAR(254) NOT NULL,
    consent_at      TIMESTAMPTZ  NOT NULL,
    locale          VARCHAR(5),
    utm_source      VARCHAR(100),
    utm_medium      VARCHAR(100),
    utm_campaign    VARCHAR(100),
    code            VARCHAR(30),
    redeemed_at     TIMESTAMPTZ,
    redeemed_org_id UUID REFERENCES organisations (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_email_signups_list_email ON email_signups (list, lower(email));
