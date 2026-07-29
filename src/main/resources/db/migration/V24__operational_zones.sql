-- Permission Architecture V2 — operational zones (§5, ZONE scope level).
--
-- CORRECTS THE ZONE MODEL. Earlier migrations treated ZONE as a single LGA:
-- user_scope, temporary_scope_grants and scope_upgrade_requests each pointed at
-- one lga_id. That is wrong for the business. A zone is an operational unit made
-- of one or more LGAs -- a small state might run three zones covering all its
-- LGAs, a dense one like Lagos twenty-one, some single-LGA and some grouped. An
-- LGA belongs to at most one zone.
--
-- So a zone becomes a first-class entity, exactly parallel to a region but one
-- level down: a region groups states, a zone groups LGAs. ZONE scope now targets
-- a zone, which resolves to its set of LGAs, filtered with IN -- the same shape
-- as REGIONAL over states.
--
-- Migrations are immutable, so this is additive: V15/V22 stay as written and
-- this converts the ZONE columns they created. Safe because no ZONE-scoped rows
-- exist -- V17 seeds only ADMIN(NATIONAL) and LAUNDRY_AGENT(SPECIALIST), and the
-- temporary-scope tables are new and empty.

CREATE TABLE zones (
    id               VARCHAR(36)  PRIMARY KEY,
    state_id         INTEGER      NOT NULL REFERENCES states(id),
    name             VARCHAR(150) NOT NULL,
    normalized_name  VARCHAR(150) NOT NULL,
    created_by       VARCHAR(36)  REFERENCES users(id),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT zones_state_normalized_name_unique UNIQUE (state_id, normalized_name)
);

CREATE INDEX idx_zones_state ON zones (state_id);


-- The zone -> LGA membership. An LGA is in at most one zone (the unique index),
-- so assigning it elsewhere is an explicit remove-then-add, never a silent move.
CREATE TABLE zone_lgas (
    zone_id  VARCHAR(36) NOT NULL REFERENCES zones(id) ON DELETE CASCADE,
    lga_id   INTEGER     NOT NULL REFERENCES lgas(id),
    PRIMARY KEY (zone_id, lga_id)
);

CREATE UNIQUE INDEX idx_zone_lgas_one_zone_per_lga ON zone_lgas (lga_id);


-- ---------------------------------------------------------------------------
-- Re-point ZONE scope from a bare LGA to a zone, on all three scope tables.
-- ---------------------------------------------------------------------------

-- user_scope (was V15). Add zone_id, drop the lga_id target, rebuild the CHECK.
ALTER TABLE user_scope ADD COLUMN zone_id VARCHAR(36) REFERENCES zones(id);
DROP INDEX IF EXISTS idx_user_scope_lga;
ALTER TABLE user_scope DROP CONSTRAINT IF EXISTS user_scope_level_target_check;
ALTER TABLE user_scope DROP COLUMN IF EXISTS lga_id;
ALTER TABLE user_scope ADD CONSTRAINT user_scope_level_target_check CHECK (
       (scope_level = 'NATIONAL'
        AND region_id IS NULL AND state_id IS NULL AND zone_id IS NULL AND specialist_user_id IS NULL)
    OR (scope_level = 'REGIONAL'
        AND region_id IS NOT NULL AND state_id IS NULL AND zone_id IS NULL AND specialist_user_id IS NULL)
    OR (scope_level = 'STATE'
        AND state_id IS NOT NULL AND region_id IS NULL AND zone_id IS NULL AND specialist_user_id IS NULL)
    OR (scope_level = 'ZONE'
        AND zone_id IS NOT NULL AND region_id IS NULL AND state_id IS NULL AND specialist_user_id IS NULL)
    OR (scope_level = 'SPECIALIST'
        AND specialist_user_id IS NOT NULL AND region_id IS NULL AND state_id IS NULL AND zone_id IS NULL)
);
CREATE INDEX idx_user_scope_zone ON user_scope (zone_id) WHERE zone_id IS NOT NULL;


-- temporary_scope_grants (was V22).
ALTER TABLE temporary_scope_grants ADD COLUMN temporary_zone_id VARCHAR(36) REFERENCES zones(id);
ALTER TABLE temporary_scope_grants DROP CONSTRAINT IF EXISTS temp_scope_target_check;
ALTER TABLE temporary_scope_grants DROP COLUMN IF EXISTS temporary_lga_id;
ALTER TABLE temporary_scope_grants ADD CONSTRAINT temp_scope_target_check CHECK (
       (temporary_scope_level = 'NATIONAL' AND temporary_region_id IS NULL
        AND temporary_state_id IS NULL AND temporary_zone_id IS NULL)
    OR (temporary_scope_level = 'REGIONAL' AND temporary_region_id IS NOT NULL)
    OR (temporary_scope_level = 'STATE'    AND temporary_state_id IS NOT NULL)
    OR (temporary_scope_level = 'ZONE'     AND temporary_zone_id IS NOT NULL)
);


-- scope_upgrade_requests (was V22).
ALTER TABLE scope_upgrade_requests ADD COLUMN requested_zone_id VARCHAR(36) REFERENCES zones(id);
ALTER TABLE scope_upgrade_requests DROP CONSTRAINT IF EXISTS scope_upgrade_target_check;
ALTER TABLE scope_upgrade_requests DROP COLUMN IF EXISTS requested_lga_id;
ALTER TABLE scope_upgrade_requests ADD CONSTRAINT scope_upgrade_target_check CHECK (
       (requested_scope_level = 'NATIONAL' AND requested_region_id IS NULL
        AND requested_state_id IS NULL AND requested_zone_id IS NULL)
    OR (requested_scope_level = 'REGIONAL' AND requested_region_id IS NOT NULL
        AND requested_state_id IS NULL AND requested_zone_id IS NULL)
    OR (requested_scope_level = 'STATE'    AND requested_state_id IS NOT NULL
        AND requested_region_id IS NULL AND requested_zone_id IS NULL)
    OR (requested_scope_level = 'ZONE'     AND requested_zone_id IS NOT NULL
        AND requested_region_id IS NULL AND requested_state_id IS NULL)
);
