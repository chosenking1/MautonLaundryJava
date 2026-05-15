-- Handoff verification codes and per-booking status history.
--
-- handoff_codes: one-time codes that gate the four physical handoffs in the
-- delivery flow (customer→rider pickup, rider→laundry drop, laundry→rider
-- return, rider→customer delivery). The partial unique index enforces "one
-- ACTIVE code per (booking, stage)" — issuing a new one must invalidate the
-- prior ACTIVE row first.
--
-- booking_status_history: append-only timeline of every booking status
-- change, with the trigger (code redemption / payment / manual / system) and
-- a back-reference to the redeemed code when applicable. Powers the admin
-- per-booking timeline and gives us forensics for any future incident.

CREATE TABLE handoff_codes (
    id                   VARCHAR(36) PRIMARY KEY,
    booking_id           VARCHAR(36) NOT NULL REFERENCES bookings(id),
    stage                VARCHAR(64) NOT NULL,
    code                 VARCHAR(8)  NOT NULL,
    status               VARCHAR(32) NOT NULL,
    issued_at            TIMESTAMP   NOT NULL,
    expires_at           TIMESTAMP   NOT NULL,
    redeemed_at          TIMESTAMP,
    redeemed_by_user_id  VARCHAR(36) REFERENCES users(id),
    attempts             INT         NOT NULL DEFAULT 0,
    CONSTRAINT handoff_codes_stage_check CHECK (stage IN (
        'CUSTOMER_TO_RIDER_PICKUP',
        'RIDER_TO_LAUNDRY',
        'LAUNDRY_TO_RIDER_RETURN',
        'RIDER_TO_CUSTOMER_DELIVERY'
    )),
    CONSTRAINT handoff_codes_status_check CHECK (status IN (
        'ACTIVE','REDEEMED','EXPIRED','INVALIDATED'
    ))
);

CREATE UNIQUE INDEX uq_handoff_one_active
    ON handoff_codes(booking_id, stage)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_handoff_lookup
    ON handoff_codes(booking_id, code, status);

CREATE INDEX idx_handoff_expiry
    ON handoff_codes(expires_at)
    WHERE status = 'ACTIVE';


CREATE TABLE booking_status_history (
    id                VARCHAR(36) PRIMARY KEY,
    booking_id        VARCHAR(36) NOT NULL REFERENCES bookings(id),
    from_status       VARCHAR(64),
    to_status         VARCHAR(64) NOT NULL,
    trigger_type      VARCHAR(32) NOT NULL,
    trigger_code_id   VARCHAR(36) REFERENCES handoff_codes(id),
    actor_user_id     VARCHAR(36) REFERENCES users(id),
    changed_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT booking_status_history_trigger_check CHECK (trigger_type IN (
        'CODE_REDEMPTION','MANUAL','PAYMENT','SYSTEM'
    ))
);

CREATE INDEX idx_status_history_booking
    ON booking_status_history(booking_id, changed_at DESC);
