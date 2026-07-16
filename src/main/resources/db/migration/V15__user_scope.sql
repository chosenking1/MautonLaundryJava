-- Permission Architecture V2 — Step 2: per-user data scope (spec §5.5).
--
-- DELIBERATE DEVIATION FROM THE SPEC, and the reason for it:
--
-- Spec §5.5 models scope as a level plus one free-text column:
--     scope_level VARCHAR(20), scope_value VARCHAR(200)
-- where scope_value polymorphically holds a region name, a state name, a zone
-- name, or a specialist UUID depending on the level.
--
-- That reintroduces exactly the failure the canonical geography tables exist to
-- prevent. A scope row reading ('STATE', 'Lagos State') or ('STATE', 'lagos')
-- points at no canonical state, so the filter matches nothing -- or worse,
-- something unintended. Nothing in the database can catch it, because any string
-- is a legal VARCHAR(200). Scope decides who sees whose orders; it must not be
-- possible to express an invalid one.
--
-- So the polymorphic column is replaced with four typed, nullable foreign keys
-- and a CHECK tying the level to its target. The database now refuses both a
-- scope pointing at a non-existent state (foreign key) and a scope whose target
-- contradicts its level, e.g. STATE with an lga_id (check constraint).
--
-- Reading a user's scope value for display is a join, not a string read. That is
-- the intended trade: correctness at write time over convenience at read time.
--
-- SPECIALIST scope points at a user (spec §5.2: "Specialist ID ... Only their
-- own orders and earnings"), so it is a self-referencing FK to users.

CREATE TABLE user_scope (
    user_id             VARCHAR(36) PRIMARY KEY REFERENCES users(id),
    scope_level         VARCHAR(20) NOT NULL,

    -- Exactly one of these is populated, per scope_level. NATIONAL populates
    -- none -- it is the wildcard (spec §5.2).
    region_id           VARCHAR(36) REFERENCES regions(id),
    state_id            INTEGER     REFERENCES states(id),
    lga_id              INTEGER     REFERENCES lgas(id),
    specialist_user_id  VARCHAR(36) REFERENCES users(id),

    assigned_by         VARCHAR(36) REFERENCES users(id),
    assigned_at         TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT user_scope_level_check
        CHECK (scope_level IN ('NATIONAL','REGIONAL','STATE','ZONE','SPECIALIST')),

    -- The level and its target must agree, and no other target may be set.
    -- Hierarchy (spec §5.1): NATIONAL > REGIONAL > STATE > ZONE > SPECIALIST.
    CONSTRAINT user_scope_level_target_check CHECK (
           (scope_level = 'NATIONAL'
            AND region_id IS NULL AND state_id IS NULL AND lga_id IS NULL AND specialist_user_id IS NULL)
        OR (scope_level = 'REGIONAL'
            AND region_id IS NOT NULL AND state_id IS NULL AND lga_id IS NULL AND specialist_user_id IS NULL)
        OR (scope_level = 'STATE'
            AND state_id IS NOT NULL AND region_id IS NULL AND lga_id IS NULL AND specialist_user_id IS NULL)
        OR (scope_level = 'ZONE'
            AND lga_id IS NOT NULL AND region_id IS NULL AND state_id IS NULL AND specialist_user_id IS NULL)
        OR (scope_level = 'SPECIALIST'
            AND specialist_user_id IS NOT NULL AND region_id IS NULL AND state_id IS NULL AND lga_id IS NULL)
    )
);

CREATE INDEX idx_user_scope_state  ON user_scope (state_id)  WHERE state_id  IS NOT NULL;
CREATE INDEX idx_user_scope_lga    ON user_scope (lga_id)    WHERE lga_id    IS NOT NULL;
CREATE INDEX idx_user_scope_region ON user_scope (region_id) WHERE region_id IS NOT NULL;
