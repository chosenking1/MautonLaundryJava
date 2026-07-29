-- Consolidation for environments where the schema was built by Hibernate
-- ddl-auto (Flyway had never run) — staging and prod both.
--
-- On such a database every @Entity table already exists, so V1–V24 are marked
-- applied via a Flyway baseline (baseline-version=24) and NOT replayed — that
-- avoids a bare CREATE TABLE colliding with a ddl-auto table, and avoids
-- replaying the ZONE rework's since-superseded lga_id history against tables
-- that already carry the final zone_id shape.
--
-- What ddl-auto could NOT build, and this migration supplies, idempotently:
--   1. the native-only tables the code reaches through raw SQL (no @Entity), so
--      Hibernate has no model for them — the permission-evaluation UNION and the
--      module/geo tables;
--   2. the permission catalogue and role grants beyond the handful
--      DataInitializationService seeds at boot, so ADMIN (which is granted every
--      permission that exists) actually holds the full set.
--
-- Everything here is IF NOT EXISTS / ON CONFLICT DO NOTHING: a no-op on a
-- database that already has it, correct on one that does not — so it is safe on
-- staging, on prod, and on a future clean environment alike.
--
-- Deliberately NOT here: the 774-LGA geographic reference dataset (old V14) and
-- the LGA aliases that depend on it. Address geo is nullable and best-effort, so
-- it does not block admin or booking; it lands in a later migration when the
-- scoped-region features need reference data.


-- ---------------------------------------------------------------------------
-- 1. Direct per-user grants/denies (old V10). Checked first in evaluation.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_permissions (
    id             VARCHAR(36) PRIMARY KEY,
    user_id        VARCHAR(36) NOT NULL REFERENCES users(id),
    permission_id  BIGINT      NOT NULL REFERENCES permissions(id),
    grant_type     VARCHAR(10) NOT NULL,
    granted_by     VARCHAR(36) NOT NULL REFERENCES users(id),
    granted_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT user_permissions_grant_type_check
        CHECK (grant_type IN ('GRANT','DENY')),
    CONSTRAINT user_permissions_user_permission_unique UNIQUE (user_id, permission_id)
);
CREATE INDEX IF NOT EXISTS idx_user_permissions_user ON user_permissions (user_id);


-- ---------------------------------------------------------------------------
-- 2. Modules — the bundle contents and the assignment/exclusion tables (old
--    V11). `modules` itself is @Entity and already exists; only these do not.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS module_permissions (
    module_id      VARCHAR(36) NOT NULL REFERENCES modules(id),
    permission_id  BIGINT      NOT NULL REFERENCES permissions(id),
    added_by       VARCHAR(36) NOT NULL REFERENCES users(id),
    added_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (module_id, permission_id)
);
CREATE INDEX IF NOT EXISTS idx_module_permissions_permission ON module_permissions (permission_id);

CREATE TABLE IF NOT EXISTS user_module_assignments (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL REFERENCES users(id),
    module_id    VARCHAR(36) NOT NULL REFERENCES modules(id),
    assigned_by  VARCHAR(36) NOT NULL REFERENCES users(id),
    assigned_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT user_module_assignments_unique UNIQUE (user_id, module_id)
);
CREATE INDEX IF NOT EXISTS idx_user_module_assignments_user ON user_module_assignments (user_id);

CREATE TABLE IF NOT EXISTS user_module_exclusions (
    id                          VARCHAR(36) PRIMARY KEY,
    user_module_assignment_id   VARCHAR(36) NOT NULL REFERENCES user_module_assignments(id) ON DELETE CASCADE,
    permission_id               BIGINT      NOT NULL REFERENCES permissions(id),
    excluded_by                 VARCHAR(36) NOT NULL REFERENCES users(id),
    excluded_at                 TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT user_module_exclusions_unique
        UNIQUE (user_module_assignment_id, permission_id)
);

CREATE TABLE IF NOT EXISTS role_module_assignments (
    id           VARCHAR(36) PRIMARY KEY,
    role_id      BIGINT      NOT NULL REFERENCES roles(id),
    module_id    VARCHAR(36) NOT NULL REFERENCES modules(id),
    assigned_by  VARCHAR(36) NOT NULL REFERENCES users(id),
    assigned_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT role_module_assignments_unique UNIQUE (role_id, module_id)
);
CREATE INDEX IF NOT EXISTS idx_role_module_assignments_role ON role_module_assignments (role_id);

CREATE TABLE IF NOT EXISTS role_module_exclusions (
    id                          VARCHAR(36) PRIMARY KEY,
    role_module_assignment_id   VARCHAR(36) NOT NULL REFERENCES role_module_assignments(id) ON DELETE CASCADE,
    permission_id               BIGINT      NOT NULL REFERENCES permissions(id),
    excluded_by                 VARCHAR(36) NOT NULL REFERENCES users(id),
    excluded_at                 TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT role_module_exclusions_unique
        UNIQUE (role_module_assignment_id, permission_id)
);


-- ---------------------------------------------------------------------------
-- 3. Geo/scope join tables that have no @Entity (old V13/V16/V24). The parent
--    tables (regions, states, lgas, zones) are entities and already exist.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS region_states (
    region_id  VARCHAR(36) NOT NULL REFERENCES regions(id) ON DELETE CASCADE,
    state_id   INTEGER     NOT NULL REFERENCES states(id),
    PRIMARY KEY (region_id, state_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_region_states_state ON region_states (state_id);

CREATE TABLE IF NOT EXISTS lga_aliases (
    id                VARCHAR(36)  PRIMARY KEY,
    lga_id            INTEGER      NOT NULL REFERENCES lgas(id) ON DELETE CASCADE,
    state_id          INTEGER      NOT NULL REFERENCES states(id),
    normalized_alias  VARCHAR(120) NOT NULL,
    source            VARCHAR(40)  NOT NULL DEFAULT 'GOOGLE',
    created_by        VARCHAR(36)  REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT lga_aliases_source_check
        CHECK (source IN ('GOOGLE','ADMIN','SEED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_lga_aliases_state_alias ON lga_aliases (state_id, normalized_alias);
CREATE INDEX IF NOT EXISTS idx_lga_aliases_lga ON lga_aliases (lga_id);

CREATE TABLE IF NOT EXISTS zone_lgas (
    zone_id  VARCHAR(36) NOT NULL REFERENCES zones(id) ON DELETE CASCADE,
    lga_id   INTEGER     NOT NULL REFERENCES lgas(id),
    PRIMARY KEY (zone_id, lga_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_zone_lgas_one_zone_per_lga ON zone_lgas (lga_id);


-- ===========================================================================
-- 4. Permission catalogue + role grants (old V18 / V19 / V23), appended below.
--    All INSERTs are ON CONFLICT DO NOTHING, so they never fight the boot-time
--    DataInitializationService seeds — they only fill the gaps it leaves.
-- ===========================================================================


-- The pre-existing permissions table still has the legacy endpoint-binding
-- columns NOT NULL. V10 relaxed them, but V10 is below the baseline so it never
-- ran, and ddl-auto never drops a constraint even though the entity now allows
-- null. The V2 seeds below insert only (name, description, category, active), so
-- relax these first or every insert fails a not-null violation on action.
-- Idempotent: DROP NOT NULL on an already-nullable column is a no-op.
ALTER TABLE permissions ALTER COLUMN endpoint DROP NOT NULL;
ALTER TABLE permissions ALTER COLUMN method   DROP NOT NULL;
ALTER TABLE permissions ALTER COLUMN resource DROP NOT NULL;
ALTER TABLE permissions ALTER COLUMN action   DROP NOT NULL;


-- ===== seeds carried over from V18__seed_endpoint_permissions =====
-- Permission Architecture V2 — permissions for every guarded endpoint.
--
-- Groundwork for replacing the 66 @PreAuthorize annotations (spec §10 step 8,
-- and the Definition of Done's "no hardcoded role names in application code").
-- Seeding alone changes NO behaviour: nothing checks these until the annotations
-- are converted. That is deliberate — the rows must exist and be granted BEFORE
-- any endpoint demands them, or the first request after deploy is a lockout.
--
-- GRANULARITY: verb-level. 14 controllers currently carry a single class-level
-- hasRole('ADMIN') covering 66 endpoints between them (AdminReferralController
-- alone has 20), so each endpoint gets its own permission rather than inheriting
-- a controller-wide one. Names follow spec §3.4 where it names things
-- (PAYOUT_VIEW, PAYOUT_SETTLE, DISCOUNT_CREATE, ORDER_VIEW_ALL, ...).
--
-- GRANTS ARE EXPLICIT HERE, on purpose:
--   * ADMIN is also granted everything by DataInitializationService's
--     adminRole.setPermissions(findAll()) on every boot. Relying on that alone
--     would make access control depend on a CommandLineRunner having run, so the
--     grants are written here too. Belt and braces on a lockout risk.
--   * DELIVERY_AGENT and LAUNDRY_AGENT are NOT re-synced by that code at all —
--     initializeRoles() only creates them `if absent`. Without the explicit
--     grants below, a converted rider endpoint would deny every existing rider.
--     This is the migration's main reason for existing.
--
-- CONSEQUENCE, accepted deliberately: because ADMIN receives findAll(), admins
-- gain the rider permissions too, so converting hasRole('DELIVERY_AGENT') to a
-- permission check admits admins to rider endpoints they are currently excluded
-- from. No naming choice avoids this; only a user-level DENY would.
--
-- ON CONFLICT DO NOTHING throughout: never overwrite a permission or a grant an
-- admin has already adjusted.

-- ---------------------------------------------------------------------------
-- 1. Permission definitions
-- ---------------------------------------------------------------------------
INSERT INTO permissions (name, description, category, active) VALUES
    -- Orders / bookings (admin surface)
    ('ORDER_VIEW_ALL',            'View all orders, subject to data scope',        'ORDER',      TRUE),
    ('ORDER_ASSIGN',              'Assign or force-reassign a laundry agent',      'ORDER',      TRUE),
    ('HANDOFF_CODE_REGENERATE',   'Regenerate a handoff code for an order',        'ORDER',      TRUE),

    -- Audit
    ('AUDIT_LOG_VIEW',            'View the audit log',                            'AUDIT',      TRUE),

    -- Categories
    ('CATEGORY_VIEW',             'View service categories',                       'CATEGORY',   TRUE),
    ('CATEGORY_CREATE',           'Create a service category',                     'CATEGORY',   TRUE),
    ('CATEGORY_EDIT',             'Edit or activate a service category',           'CATEGORY',   TRUE),
    ('CATEGORY_DELETE',           'Delete a service category',                     'CATEGORY',   TRUE),

    -- Geography (canonical state/LGA resolution)
    ('GEO_UNRESOLVED_VIEW',       'View addresses with unresolved geography',      'GEO',        TRUE),
    ('GEO_BACKFILL_RUN',          'Run geography backfill (spends Google quota)',  'GEO',        TRUE),

    -- Payments (admin surface; PAYMENT_READ/CREATE already exist)
    ('PAYMENT_EDIT',              'Amend a payment record',                        'PAYMENT',    TRUE),

    -- Referrers
    ('REFERRAL_VIEW',             'View referrers and their dashboards',           'REFERRAL',   TRUE),
    ('REFERRAL_CREATE',           'Create a referrer',                             'REFERRAL',   TRUE),
    ('REFERRAL_EDIT',             'Edit a referrer',                               'REFERRAL',   TRUE),
    ('REFERRAL_RULE_VIEW',        'View referrer payment rules and history',       'REFERRAL',   TRUE),
    ('REFERRAL_RULE_EDIT',        'Add, change or deactivate payment rules',       'REFERRAL',   TRUE),

    -- Payouts (spec §3.4 names PAYOUT_VIEW / PAYOUT_SETTLE)
    ('PAYOUT_VIEW',               'View payouts and payout previews',              'PAYOUT',     TRUE),
    ('PAYOUT_GENERATE',           'Generate a payout for a period',                'PAYOUT',     TRUE),
    ('PAYOUT_APPROVE',            'Approve a generated payout',                    'PAYOUT',     TRUE),
    ('PAYOUT_SETTLE',             'Mark a payout as paid',                         'PAYOUT',     TRUE),
    ('PAYOUT_ADJUST',             'Record an adjustment against a payout',         'PAYOUT',     TRUE),

    -- Services and their pricing
    ('SERVICE_VIEW',              'View services',                                 'SERVICE',    TRUE),
    ('SERVICE_CREATE',            'Create a service',                              'SERVICE',    TRUE),
    ('SERVICE_EDIT',              'Edit a service',                                'SERVICE',    TRUE),
    ('SERVICE_DELETE',            'Delete a service',                              'SERVICE',    TRUE),
    ('SERVICE_PRICING_VIEW',      'View service pricing',                          'SERVICE',    TRUE),
    ('SERVICE_PRICING_CREATE',    'Create service pricing',                        'SERVICE',    TRUE),
    ('SERVICE_PRICING_EDIT',      'Edit service pricing',                          'SERVICE',    TRUE),
    ('SERVICE_PRICING_DELETE',    'Delete service pricing',                        'SERVICE',    TRUE),

    -- Agents
    ('AGENT_DEACTIVATE',          'Deactivate an agent account',                   'AGENT',      TRUE),
    ('AGENT_HOURS_VIEW',          'View laundry agent operating hours',            'AGENT',      TRUE),
    ('AGENT_HOURS_EDIT',          'Set laundry agent operating hours',             'AGENT',      TRUE),
    ('AGENT_APPLICATION_VIEW',    'View agent applications',                       'AGENT',      TRUE),
    ('AGENT_APPLICATION_INSPECT', 'Record an inspection on an application',        'AGENT',      TRUE),
    ('AGENT_APPLICATION_REJECT',  'Reject an agent application',                   'AGENT',      TRUE),
    ('AGENT_APPLICATION_APPROVE', 'Approve an agent application',                  'AGENT',      TRUE),

    -- Intelligence / reporting
    ('CUSTOMER_VIEW',             'View customer intelligence',                    'ANALYTICS',  TRUE),
    ('CUSTOMER_EXPORT',           'Export customer data to CSV',                   'ANALYTICS',  TRUE),
    ('CAS_PERFORMANCE_VIEW',      'View CAS performance',                          'ANALYTICS',  TRUE),
    ('CAS_PERFORMANCE_EXPORT',    'Export CAS performance to CSV',                 'ANALYTICS',  TRUE),
    ('LEADERBOARD_VIEW',          'View leaderboards',                             'ANALYTICS',  TRUE),
    ('LEADERBOARD_EXPORT',        'Export leaderboards to CSV',                    'ANALYTICS',  TRUE),

    -- Files
    ('FILE_UPLOAD',               'Upload an image',                               'FILE',       TRUE),

    -- Discounts (spec §3.4 names DISCOUNT_CREATE / DISCOUNT_EDIT)
    ('DISCOUNT_VIEW',             'View discounts and assignments',                'DISCOUNT',   TRUE),
    ('DISCOUNT_CREATE',           'Create a discount',                             'DISCOUNT',   TRUE),
    ('DISCOUNT_EDIT',             'Edit a discount',                               'DISCOUNT',   TRUE),
    ('DISCOUNT_APPROVE',          'Approve or reject a discount assignment',       'DISCOUNT',   TRUE),
    ('DISCOUNT_REVOKE',           'Revoke a discount assignment',                  'DISCOUNT',   TRUE),

    -- Dispatch (spec §3.4)
    ('DISPATCH_RIDER_ASSIGN',     'Manually assign a rider to a delivery',         'DISPATCH',   TRUE),

    -- PricingController has read "hasRole('ADMIN') or hasAuthority('PRICING_UPDATE')"
    -- since it was written, but PRICING_UPDATE was never seeded -- so no one could
    -- ever hold it and that or-branch has always been dead code, leaving the
    -- endpoint ADMIN-only in practice. Seeding it makes the delegation the
    -- annotation already advertises actually possible.
    ('PRICING_UPDATE',            'Update pricing configuration',                  'PRICING',    TRUE),

    -- Rider / agent self-service. Granted to DELIVERY_AGENT and LAUNDRY_AGENT
    -- below; these replace the hasRole() checks on their endpoints.
    ('DELIVERY_JOB_VIEW',         'View available and own delivery jobs',          'DELIVERY',   TRUE),
    ('DELIVERY_ACCEPT',           'Accept a delivery job',                         'DELIVERY',   TRUE),
    ('HANDOFF_VERIFY',            'Redeem a handoff code',                         'DELIVERY',   TRUE),
    ('AGENT_PRESENCE_UPDATE',     'Go online or offline as an agent',              'AGENT',      TRUE),
    ('AGENT_LOCATION_UPDATE',     'Update own availability location',              'AGENT',      TRUE),
    ('TRACKING_LOCATION_POST',    'Post own live tracking location',               'DELIVERY',   TRUE)
ON CONFLICT (name) DO NOTHING;


-- ---------------------------------------------------------------------------
-- 2. Grants
-- ---------------------------------------------------------------------------

-- ADMIN gets every permission that exists. This mirrors what
-- DataInitializationService does on each boot, written explicitly so access
-- control does not depend on a runner having executed.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- DELIVERY_AGENT: the endpoints riders actually use. Without this they would be
-- denied the moment their hasRole() checks become permission checks, because
-- initializeRoles() never re-syncs this role.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'DELIVERY_JOB_VIEW',
    'DELIVERY_ACCEPT',
    'HANDOFF_VERIFY',
    'AGENT_PRESENCE_UPDATE',
    'AGENT_LOCATION_UPDATE',
    'TRACKING_LOCATION_POST'
)
WHERE r.name = 'DELIVERY_AGENT'
ON CONFLICT DO NOTHING;

-- LAUNDRY_AGENT: only presence today (their other endpoints already use
-- BOOKING_READ / BOOKING_UPDATE, which they hold).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'AGENT_PRESENCE_UPDATE'
)
WHERE r.name = 'LAUNDRY_AGENT'
ON CONFLICT DO NOTHING;


-- ===== seeds carried over from V19__seed_module_permissions =====
-- Permission Architecture V2 — permissions for managing modules (spec §3.2/§3.4).
--
-- The module tables have existed since V11 and PermissionEvaluationService has
-- resolved them since V10, but nothing could write them: modules were dead
-- schema. These guard the API that makes them writable.
--
-- Spec §3.4 places MODULE_CREATE/MODULE_EDIT in the HQ_CONFIG module and
-- MODULE_ASSIGN in USER_MANAGEMENT; the names here match so those bundles can be
-- built from the portal later without renaming anything.
--
-- MODULE_ASSIGN is deliberately separate from MODULE_CREATE/EDIT. Designing a
-- bundle and handing it to people are different acts: §3.2 lets anyone with
-- MODULE_EDIT change what a module means for every existing holder, which is a
-- heavier power than granting a fixed bundle to one user.
--
-- Note the escalation ceiling that makes these safe to delegate: ModuleService
-- refuses to bundle or assign any permission the actor does not personally hold,
-- so MODULE_CREATE can never mint a permission its holder lacks.

INSERT INTO permissions (name, description, category, active) VALUES
    ('MODULE_CREATE', 'Create a permission module',                       'MODULE', TRUE),
    ('MODULE_EDIT',   'Add or remove permissions from a module',          'MODULE', TRUE),
    ('MODULE_ASSIGN', 'Assign a module to a user or role, with exclusions','MODULE', TRUE),
    ('MODULE_VIEW',   'View modules and their contents',                  'MODULE', TRUE)
ON CONFLICT (name) DO NOTHING;

-- ADMIN holds everything (mirrors DataInitializationService's findAll() sync,
-- written explicitly so access does not depend on a runner having executed).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('MODULE_CREATE', 'MODULE_EDIT', 'MODULE_ASSIGN', 'MODULE_VIEW')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;


-- ===== seeds carried over from V23__access_catalog_and_region_permissions =====
-- Permission Architecture V2 — permissions for the remaining admin pages
-- (§7.1 Permissions Management, §7.3 Role Builder, §7.7 Region Management).
--
-- The write permissions these pages need already exist:
--   PERMISSION_CREATE, ROLE_CREATE, ROLE_PERMISSION_ASSIGN  (seeded by
--   DataInitializationService), MODULE_ASSIGN (V19). What is missing is the
--   read side -- listing the catalogue -- and anything for regions at all.
--
-- REGION over the spec's SYSTEM_CONFIG_EDIT (§7.7). The spec guards region
-- management with SYSTEM_CONFIG_EDIT, but that permission was never seeded, and
-- folding regions in with system config would mean you cannot delegate "manage
-- the map" without also handing over escalation-window config. Regions are their
-- own concern, so they get their own permission.
--
-- REGION_MANAGE is separate from REGION_VIEW because REGIONAL scope keys off the
-- region->state mapping: editing it silently changes what every regional user
-- can see, which is a heavier act than reading the map.

INSERT INTO permissions (name, description, category, active) VALUES
    ('PERMISSION_VIEW', 'View the permission catalogue',           'ACCESS', TRUE),
    ('ROLE_VIEW',       'View roles and their effective sets',     'ACCESS', TRUE),
    ('REGION_VIEW',     'View regions and their states',           'GEO',    TRUE),
    ('REGION_MANAGE',   'Create regions and assign states to them','GEO',    TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('PERMISSION_VIEW', 'ROLE_VIEW', 'REGION_VIEW', 'REGION_MANAGE')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
