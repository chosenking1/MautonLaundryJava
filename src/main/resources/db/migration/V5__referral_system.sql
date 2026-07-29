-- Referral system: a standalone partner-referral and commission engine,
-- separate from (but optionally linked to) the discount system.
--
-- WHY IF NOT EXISTS: this migration was never committed (a blanket *.sql
-- gitignore excluded it) and so has never run in any environment -- yet the
-- tables it creates already exist everywhere, because ddl-auto=update built them
-- from the entities. A bare CREATE TABLE would therefore fail on its very first
-- run, failing Flyway and refusing to start the app. Guarding each object lets
-- the migration reconcile with what ddl-auto already made instead of colliding
-- with it. Safe to relax the guards only once every environment's
-- flyway_schema_history records V5.
--
-- Design notes:
--  * referrers has NO foreign key to users. Referrers (specialists,
--    influencers, estate managers, ...) are managed entirely from the admin
--    portal and need not be platform users.
--  * referral_attributions is UNIQUE on user_id: one referrer per user, set at
--    registration only.
--  * referral_booking_events is UNIQUE on booking_id so the (non-blocking)
--    payment webhook is idempotent on retries.
--  * Every payment rule, rate and frequency is configurable per referrer. The
--    Imototo commission rate that rule Type 1 pays out of is seeded into the
--    existing pricing_config table and is admin-editable.

CREATE TABLE IF NOT EXISTS referrers (
    id                   VARCHAR(36) PRIMARY KEY,
    name                 VARCHAR(150) NOT NULL,
    email                VARCHAR(150),
    phone                VARCHAR(40),
    referrer_type        VARCHAR(30)  NOT NULL,
    referral_code        VARCHAR(50)  NOT NULL UNIQUE,
    linked_discount_id   VARCHAR(36) REFERENCES discounts(id),
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    notes                TEXT,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT referrers_type_check CHECK (referrer_type IN (
        'SPECIALIST','CUSTOMER','ESTATE','CORPORATE','INFLUENCER'
    ))
);

CREATE INDEX IF NOT EXISTS idx_referrers_email ON referrers(LOWER(email)) WHERE email IS NOT NULL;


CREATE TABLE IF NOT EXISTS referral_attributions (
    id                   VARCHAR(36) PRIMARY KEY,
    referrer_id          VARCHAR(36) NOT NULL REFERENCES referrers(id),
    user_id              VARCHAR(36) NOT NULL UNIQUE REFERENCES users(id),
    registered_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    referral_code_used   VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_attributions_referrer ON referral_attributions(referrer_id);


CREATE TABLE IF NOT EXISTS referral_payment_rules (
    id                            VARCHAR(36) PRIMARY KEY,
    referrer_id                   VARCHAR(36) NOT NULL REFERENCES referrers(id),
    rule_type                     VARCHAR(40) NOT NULL,
    percentage_value              NUMERIC(6,2),
    flat_fee_amount               NUMERIC(12,2),
    booking_number_from           INT,
    booking_number_to             INT,
    days_from_registration_limit  INT,
    volume_threshold              INT,
    milestone_booking_number      INT,
    payment_frequency             VARCHAR(20) NOT NULL,
    milestone_payment_threshold   INT,
    effective_from                DATE        NOT NULL,
    effective_until               DATE,
    is_active                     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_by                    VARCHAR(36),
    created_at                    TIMESTAMP   NOT NULL DEFAULT NOW(),
    notes                         TEXT,
    CONSTRAINT referral_rule_type_check CHECK (rule_type IN (
        'PERCENTAGE_OF_IMOTOTO_COMMISSION',
        'PERCENTAGE_OF_ORDER_VALUE',
        'FLAT_FEE_PER_MILESTONE',
        'FLAT_FEE_PER_PERIOD_VOLUME',
        'MANUAL_OVERRIDE'
    )),
    CONSTRAINT referral_rule_frequency_check CHECK (payment_frequency IN (
        'WEEKLY','FORTNIGHTLY','MONTHLY','QUARTERLY','ON_DEMAND','PER_MILESTONE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_rules_referrer_active ON referral_payment_rules(referrer_id, is_active);


CREATE TABLE IF NOT EXISTS referral_booking_events (
    id                            VARCHAR(36) PRIMARY KEY,
    attribution_id                VARCHAR(36) NOT NULL REFERENCES referral_attributions(id),
    booking_id                    VARCHAR(36) NOT NULL UNIQUE REFERENCES bookings(id),
    booking_number                INT         NOT NULL,
    days_since_registration       INT         NOT NULL,
    imototo_commission_on_order   NUMERIC(12,2),
    order_gross_value             NUMERIC(12,2),
    referrer_commission_earned    NUMERIC(12,2),
    commission_rules_applied      JSONB,
    created_at                    TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_events_attribution ON referral_booking_events(attribution_id);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON referral_booking_events(created_at);


CREATE TABLE IF NOT EXISTS referral_payment_rule_history (
    id              VARCHAR(36) PRIMARY KEY,
    referrer_id     VARCHAR(36) NOT NULL REFERENCES referrers(id),
    changed_by      VARCHAR(36),
    previous_rules  JSONB,
    new_rules       JSONB,
    changed_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    change_reason   TEXT
);

CREATE INDEX IF NOT EXISTS idx_rule_history_referrer ON referral_payment_rule_history(referrer_id, changed_at DESC);


CREATE TABLE IF NOT EXISTS referral_payouts (
    id                          VARCHAR(36) PRIMARY KEY,
    referrer_id                 VARCHAR(36) NOT NULL REFERENCES referrers(id),
    period_from                 DATE,
    period_to                   DATE,
    calculation_breakdown       JSONB,
    total_commission_calculated NUMERIC(12,2),
    manual_adjustment_amount    NUMERIC(12,2),
    manual_adjustment_reason    TEXT,
    final_payout_amount         NUMERIC(12,2),
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    generated_at                TIMESTAMP   NOT NULL DEFAULT NOW(),
    generated_by                VARCHAR(36),
    approved_by                 VARCHAR(36),
    approved_at                 TIMESTAMP,
    paid_at                     TIMESTAMP,
    paid_by                     VARCHAR(36),
    payment_reference           TEXT,
    notes                       TEXT,
    CONSTRAINT referral_payout_status_check CHECK (status IN (
        'PENDING','APPROVED','PAID','DISPUTED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_payouts_referrer ON referral_payouts(referrer_id, generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_payouts_status ON referral_payouts(status);


-- Imototo's commission rate: the share of the laundry/service revenue (items
-- subtotal + express surcharge, after any welcome discount; delivery excluded)
-- that Imototo keeps and out of which referral commission is paid. Stored as a
-- decimal fraction. Admin-editable from the pricing config; nothing hardcoded.
INSERT INTO pricing_config (config_key, config_value, effective_from, created_at)
SELECT 'IMOTOTO_COMMISSION_RATE', '0.30', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM pricing_config WHERE config_key = 'IMOTOTO_COMMISSION_RATE'
);
