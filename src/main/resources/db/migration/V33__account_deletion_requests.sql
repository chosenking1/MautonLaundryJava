-- Customer account deletion (Google Play's account-deletion requirement).
--
-- A request is its own row, not columns on users: a person can ask, change
-- their mind, and ask again, and each of those is a record worth keeping --
-- "when did this customer ask to be forgotten, and did we do it on time" is a
-- question a regulator can ask under the NDPA.
--
-- Lifecycle: requested -> (cancelled_at, by logging back in) or (completed_at,
-- by AccountDeletionJob once scheduled_for passes). The account is locked for
-- the whole grace period.

CREATE TABLE IF NOT EXISTS account_deletion_requests (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        VARCHAR(255) NOT NULL REFERENCES users(id),
    -- Where it was asked for: the in-app button or the public web page.
    source         VARCHAR(10)  NOT NULL,
    requested_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    scheduled_for  TIMESTAMP    NOT NULL,
    cancelled_at   TIMESTAMP,
    completed_at   TIMESTAMP,
    CONSTRAINT account_deletion_source_check CHECK (source IN ('APP', 'WEB')),
    CONSTRAINT account_deletion_one_outcome CHECK (cancelled_at IS NULL OR completed_at IS NULL)
);

-- At most one live request per person.
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_deletion_pending
    ON account_deletion_requests (user_id)
    WHERE cancelled_at IS NULL AND completed_at IS NULL;

-- The daily sweep: pending rows whose grace period has run out.
CREATE INDEX IF NOT EXISTS idx_account_deletion_due
    ON account_deletion_requests (scheduled_for)
    WHERE cancelled_at IS NULL AND completed_at IS NULL;
