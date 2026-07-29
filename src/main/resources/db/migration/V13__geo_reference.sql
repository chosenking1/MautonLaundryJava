-- Permission Architecture V2 — Step 2: canonical geography (spec §5).
--
-- The scope layer filters data by geography, which makes geography a security
-- boundary. Before this migration the only geographic field in the system was
-- address.state -- free text, not even NOT NULL. A scope filter keyed on free
-- text mis-routes on a typo ("Lagos" / "lagos" / "Lagos State"), and a scope
-- filter that mis-routes is a data leak, not a display bug. Hence canonical
-- reference tables with the address carrying foreign keys to them.
--
-- ZONE = Local Government Area. Nigeria's hierarchy is
--   country -> geopolitical zone -> state -> LGA -> locality
-- mapped onto the spec's scope levels as
--   NATIONAL -> REGIONAL(geopolitical zone) -> STATE -> ZONE(LGA) -> SPECIALIST
-- This reconciles the spec's own examples: its ZONE example "Lagos-Island" is an
-- LGA, and its REGIONAL example "South-West" is a geopolitical zone. A locality
-- such as Igbogbo sits inside Ikorodu LGA and is below the lowest scope level,
-- so it stays address detail rather than becoming a scope target.
--
-- Id types: states and lgas are fixed reference data seeded by migration, so
-- they take compact SERIALs. regions are created and edited by admins at runtime
-- (spec §5.2: "Regions are not hardcoded"), so they take VARCHAR(36) like every
-- other app-managed entity (V5 convention).
--
-- normalized_name exists because nothing in the wild spells these consistently.
-- Google reverse geocoding returns "Oshodi/Isolo" where the official register
-- says "Oshodi-Isolo"; both normalize to "oshodiisolo". The resolver matches on
-- the normalized form and never on the display name.

CREATE TABLE states (
    id               SERIAL       PRIMARY KEY,
    name             VARCHAR(100) NOT NULL UNIQUE,
    -- lowercase, alphanumerics only. Must be produced by the same routine the
    -- resolver uses, or lookups silently miss.
    normalized_name  VARCHAR(100) NOT NULL UNIQUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);


-- LGA names are NOT globally unique, and this is not a data defect: Surulere is
-- an LGA of both Lagos and Oyo; Obi of both Benue and Nasarawa; Nasarawa of both
-- Kano and Nasarawa; Bassa of both Kogi and Plateau; Ifelodun and Irepodun of
-- both Osun and Kwara. The key is therefore (state_id, normalized_name).
-- A UNIQUE(normalized_name) here would reject the real seed data.
CREATE TABLE lgas (
    id               SERIAL       PRIMARY KEY,
    state_id         INTEGER      NOT NULL REFERENCES states(id),
    name             VARCHAR(120) NOT NULL,
    normalized_name  VARCHAR(120) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT lgas_state_normalized_name_unique UNIQUE (state_id, normalized_name)
);

CREATE INDEX idx_lgas_state ON lgas (state_id);
-- The resolver's hot path: normalized LGA lookup within a known state.
CREATE INDEX idx_lgas_normalized ON lgas (normalized_name);


-- Spec §5.5. A region is a named group of states. Seeded with Nigeria's six
-- geopolitical zones in V14 as a starting point; admins add/edit from the portal
-- as the company expands, so nothing here is a code constant.
CREATE TABLE regions (
    id           VARCHAR(36)  PRIMARY KEY,
    region_name  VARCHAR(100) NOT NULL UNIQUE,
    created_by   VARCHAR(36)  REFERENCES users(id),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Spec §5.5 keys this on state_name text; a real FK is used instead so a region
-- cannot reference a state that does not exist.
CREATE TABLE region_states (
    region_id  VARCHAR(36) NOT NULL REFERENCES regions(id) ON DELETE CASCADE,
    state_id   INTEGER     NOT NULL REFERENCES states(id),
    PRIMARY KEY (region_id, state_id)
);

-- A state belongs to at most one region, so REGIONAL scope can never see the
-- same state twice or resolve ambiguously.
CREATE UNIQUE INDEX idx_region_states_state ON region_states (state_id);


-- The table is `address` (singular) -- entity com.work.mautonlaundry.data.model.Address.
--
-- Both columns are nullable and stay that way. Addresses are created from the
-- Flutter map picker with client-supplied text; the server resolves geography
-- asynchronously and cannot always succeed (Google's admin_area_level_2 coverage
-- in Nigeria is incomplete). An unresolved address must remain a valid address.
ALTER TABLE address ADD COLUMN IF NOT EXISTS state_id INTEGER REFERENCES states(id);
ALTER TABLE address ADD COLUMN IF NOT EXISTS lga_id   INTEGER REFERENCES lgas(id);

-- NULL = never attempted. Distinguishes "not yet resolved" from "resolved and
-- genuinely unresolvable", so the admin queue does not re-try forever.
ALTER TABLE address ADD COLUMN IF NOT EXISTS geo_resolved_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_address_state ON address (state_id);
CREATE INDEX IF NOT EXISTS idx_address_lga   ON address (lga_id);
-- Backs the admin "unresolved addresses" queue.
CREATE INDEX IF NOT EXISTS idx_address_unresolved ON address (id) WHERE lga_id IS NULL;
