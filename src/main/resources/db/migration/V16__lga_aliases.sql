-- Permission Architecture V2 — Step 2: external name aliases for LGAs.
--
-- Google's administrative_area_level_2 does not always spell an LGA the way the
-- official register does. Measured against the live Geocoding API over 10 real
-- Nigerian coordinates, 8 resolved by normalization alone; 2 did not:
--
--   Google 'Abuja Municipal Area Council'  vs register 'Municipal Area Council'
--   Google 'Nassarawa' (Kano)              vs register 'Nasarawa'
--
-- Normalization already absorbs the easy variance -- 'Oshodi/Isolo' reaches
-- 'Oshodi-Isolo', 'Port-Harcourt' reaches 'Port Harcourt', 'Ibadan North East'
-- reaches 'Ibadan North-East'. The residue is genuine naming disagreement, not
-- punctuation, and no normalization rule can absorb it safely: stripping an
-- "Area Council" suffix would turn the FCT's real 'Municipal Area Council' into
-- 'municipal' and break rows that currently resolve.
--
-- So canonical names stay exactly as the register has them, and alternates live
-- here. The resolver tries the canonical normalized name, then this table, and
-- only then gives up to the unresolved queue. When an address lands in that
-- queue an admin resolves it once and adds an alias, so the system learns rather
-- than accumulating hand-tuned regexes.

CREATE TABLE lga_aliases (
    id                VARCHAR(36)  PRIMARY KEY,
    lga_id            INTEGER      NOT NULL REFERENCES lgas(id) ON DELETE CASCADE,

    -- Denormalized from lgas.state_id. Postgres cannot reach another table from
    -- an index predicate, and the uniqueness rule below is per state, so the
    -- state is carried here. Kept in step by the resolver/admin write path,
    -- which always derives it from the parent LGA.
    state_id          INTEGER      NOT NULL REFERENCES states(id),

    -- Normalized by the same routine as lgas.normalized_name
    -- (GeoNormalizer.normalize). Never store a display form here.
    normalized_alias  VARCHAR(120) NOT NULL,

    -- Where the alias came from, so a bad one can be traced and revoked.
    source            VARCHAR(40)  NOT NULL DEFAULT 'GOOGLE',
    created_by        VARCHAR(36)  REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT lga_aliases_source_check
        CHECK (source IN ('GOOGLE','ADMIN','SEED'))
);

-- An alias must be unambiguous within its state, exactly as the canonical name
-- is: two LGAs in one state cannot answer to the same alias. Across states they
-- may -- 'surulere' is legitimately both Lagos and Oyo.
CREATE UNIQUE INDEX idx_lga_aliases_state_alias
    ON lga_aliases (state_id, normalized_alias);

CREATE INDEX idx_lga_aliases_lga ON lga_aliases (lga_id);


-- The two misses measured against the live API. state_id is taken from the
-- parent LGA rather than restated, so it cannot drift.
INSERT INTO lga_aliases (id, lga_id, state_id, normalized_alias, source)
SELECT 'alias-fct-amac', l.id, l.state_id, 'abujamunicipalareacouncil', 'SEED'
FROM lgas l JOIN states s ON s.id = l.state_id
WHERE s.normalized_name = 'federalcapitalterritory'
  AND l.normalized_name = 'municipalareacouncil';

INSERT INTO lga_aliases (id, lga_id, state_id, normalized_alias, source)
SELECT 'alias-kano-nassarawa', l.id, l.state_id, 'nassarawa', 'SEED'
FROM lgas l JOIN states s ON s.id = l.state_id
WHERE s.normalized_name = 'kano'
  AND l.normalized_name = 'nasarawa';

-- 'AMAC' is the everyday name for the FCT's Municipal Area Council and appears
-- in user-entered addresses.
INSERT INTO lga_aliases (id, lga_id, state_id, normalized_alias, source)
SELECT 'alias-fct-amac-short', l.id, l.state_id, 'amac', 'SEED'
FROM lgas l JOIN states s ON s.id = l.state_id
WHERE s.normalized_name = 'federalcapitalterritory'
  AND l.normalized_name = 'municipalareacouncil';
