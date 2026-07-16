-- Permission Architecture V2 — Step 1 of 3: the permission core.
--
-- Reconciles the EXISTING permissions table with the V2 spec rather than
-- creating it. The spec's `CREATE TABLE permissions (id UUID, permission_key
-- ...)` cannot be applied literally: the table already exists (Hibernate
-- ddl-auto=update, entity com.work.mautonlaundry.data.model.Permission) with a
-- BIGSERIAL id and a `name` column. Adapting to what is here:
--
--   spec permission_key  ->  existing `name`     (already UNIQUE NOT NULL)
--   spec id UUID         ->  existing BIGSERIAL  (FKs below are BIGINT)
--   spec category        ->  added here, backfilled from `resource`
--
-- Renaming name -> permission_key is deliberately NOT done: the entity field is
-- `name`, so ddl-auto=update would simply re-add a `name` column beside it.
--
-- The endpoint/method/resource/action columns belong to the older
-- endpoint-binding permission model (DynamicPermissionService matches a request
-- URI+verb against them). V2 permissions are atomic actions -- PAYOUT_SETTLE has
-- no URI and no HTTP verb -- but all four columns are NOT NULL today, so such a
-- row physically cannot be inserted. They are relaxed below.
--
-- Side effect: this also fixes an existing latent bug.
-- RoleAndPermissionService.createPermission() sets only name/description/
-- resource/action and never endpoint/method, so POST /api/v1/permissions has
-- always thrown a not-null violation. It works after this migration.
--
-- Nothing here changes enforcement. No existing grant is added or removed.

-- 1. Relax the legacy endpoint-binding columns (idempotent; a no-op if the
--    column is already nullable).
ALTER TABLE permissions ALTER COLUMN endpoint DROP NOT NULL;
ALTER TABLE permissions ALTER COLUMN method   DROP NOT NULL;
ALTER TABLE permissions ALTER COLUMN resource DROP NOT NULL;
ALTER TABLE permissions ALTER COLUMN action   DROP NOT NULL;

-- 2. Spec §2.7: category. The existing `resource` column already carries
--    exactly this grouping (USER, BOOKING, PAYMENT, ...), so seed from it.
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS category VARCHAR(100);
UPDATE permissions SET category = resource WHERE category IS NULL;


-- 3. Spec §2.7: direct per-user grants and denies. Overrides the user's role.
--    Evaluation (spec §2.6) checks DENY here first, then GRANT here, before any
--    role or module is consulted.
CREATE TABLE user_permissions (
    id             VARCHAR(36) PRIMARY KEY,
    user_id        VARCHAR(36) NOT NULL REFERENCES users(id),
    permission_id  BIGINT      NOT NULL REFERENCES permissions(id),
    grant_type     VARCHAR(10) NOT NULL,
    granted_by     VARCHAR(36) NOT NULL REFERENCES users(id),
    granted_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT user_permissions_grant_type_check
        CHECK (grant_type IN ('GRANT','DENY')),
    -- Spec §2.7: one row per (user, permission). A permission is either granted
    -- or denied for a user, never both -- which is what makes "explicit DENY
    -- wins" a lookup rather than a conflict resolution.
    CONSTRAINT user_permissions_user_permission_unique UNIQUE (user_id, permission_id)
);

CREATE INDEX idx_user_permissions_user ON user_permissions (user_id);


-- 4. Spec §2.1/§2.7: when a user's permissions are reduced, grants they
--    previously made that now exceed their reduced set are flagged here.
--    Deliberately NOT auto-revoked -- a human resolves each one.
CREATE TABLE pending_permission_review (
    id                   VARCHAR(36) PRIMARY KEY,
    user_id              VARCHAR(36) NOT NULL REFERENCES users(id),
    permission_id        BIGINT      NOT NULL REFERENCES permissions(id),
    original_grantor_id  VARCHAR(36) NOT NULL REFERENCES users(id),
    flagged_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    reviewed_by          VARCHAR(36) REFERENCES users(id),
    reviewed_at          TIMESTAMP,
    resolution           VARCHAR(20),
    CONSTRAINT pending_permission_review_resolution_check
        CHECK (resolution IN ('KEPT','REVOKED'))
);

-- The review queue only ever reads unresolved rows.
CREATE INDEX idx_pending_permission_review_open
    ON pending_permission_review (user_id) WHERE reviewed_at IS NULL;
