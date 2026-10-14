-- Records which version of the terms each user agreed to, and when.
--
-- Nothing captured consent before this, so there was no way to show what any
-- customer had actually agreed to -- which is the whole point of having terms.
--
-- Insert-only by design. When the terms change, a new acceptance is added rather
-- than the old row updated: overwriting would destroy the evidence the record
-- exists to provide.
CREATE TABLE IF NOT EXISTS terms_acceptances (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL REFERENCES users(id),
    version      VARCHAR(40) NOT NULL,
    accepted_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    source       VARCHAR(40),
    ip_address   VARCHAR(64),
    -- Agreeing to the same version twice is not a second consent.
    CONSTRAINT terms_acceptances_user_version_unique UNIQUE (user_id, version)
);

CREATE INDEX IF NOT EXISTS idx_terms_acceptances_user
    ON terms_acceptances (user_id, accepted_at DESC);
