-- Permission Architecture V2 — Step 3 of 3: reconcile the role layer (spec §4).
--
-- As with permissions in V10, `roles` and `role_permissions` already exist
-- (Hibernate ddl-auto=update; entity com.work.mautonlaundry.data.model.Role,
-- whose @ManyToMany owns the role_permissions join table). The spec's
-- CREATE TABLEs are applied as ALTERs against what is actually here:
--
--   spec role_key  ->  existing `name` (already UNIQUE NOT NULL); not renamed,
--                      because the entity field is `name` and ddl-auto=update
--                      would just re-add a `name` column beside a rename.
--   spec id UUID   ->  existing BIGSERIAL.
--
-- KEY DECISION -- why Role.permissions stays a @ManyToMany:
-- The spec adds grant_type/granted_by/granted_at to role_permissions, which
-- would normally force the join table to become a first-class entity and ripple
-- through RoleService, RoleAndPermissionService and DataInitializationService.
-- It does not have to. Hibernate's @ManyToMany writes exactly
-- (role_id, permission_id); giving every added column a DEFAULT means those
-- inserts keep working untouched and the schema is still V2-shaped. The entity
-- remodel is deferred until something actually needs role-level DENY or grant
-- provenance -- note that spec §2.6/§8.1 evaluate role_permissions for GRANT
-- only and never consult a role-level DENY, so nothing needs it yet.
--
-- Consequence of that choice, recorded deliberately: granted_by is NULLABLE
-- here where the spec says NOT NULL. Existing rows were seeded by
-- DataInitializationService with no human actor, and its @ManyToMany insert
-- supplies no granter. NULL granted_by therefore means "system-seeded".
-- Also note DataInitializationService calls setPermissions(findAll()) for ADMIN
-- and USER on every boot, which deletes and re-inserts their rows -- so
-- granted_at is refreshed each startup for those two roles until the seeding
-- moves into Flyway (spec §4.1 wants roles to be seeded data, not code).
--
-- No enforcement change: every existing (role_id, permission_id) pair keeps its
-- meaning, and defaults classify them all as GRANT -- which is what they were.

-- 1. Spec §4.2: roles gains display_name / created_by / created_at.
ALTER TABLE roles ADD COLUMN IF NOT EXISTS display_name VARCHAR(200);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_by   VARCHAR(36) REFERENCES users(id);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP NOT NULL DEFAULT NOW();

-- Backfill before enforcing NOT NULL: existing roles (ADMIN, USER,
-- LAUNDRY_AGENT, DELIVERY_AGENT) predate display_name.
UPDATE roles SET display_name = name WHERE display_name IS NULL;
ALTER TABLE roles ALTER COLUMN display_name SET NOT NULL;

-- created_by stays nullable: the seeded roles were created by no one.


-- 2. Spec §4.2: role_permissions gains grant provenance. Every column is
--    DEFAULTed so the existing @ManyToMany insert of (role_id, permission_id)
--    continues to satisfy the table -- see KEY DECISION above.
ALTER TABLE role_permissions
    ADD COLUMN IF NOT EXISTS grant_type VARCHAR(10) NOT NULL DEFAULT 'GRANT';
ALTER TABLE role_permissions
    ADD COLUMN IF NOT EXISTS granted_by VARCHAR(36) REFERENCES users(id);
ALTER TABLE role_permissions
    ADD COLUMN IF NOT EXISTS granted_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE role_permissions DROP CONSTRAINT IF EXISTS role_permissions_grant_type_check;
ALTER TABLE role_permissions ADD CONSTRAINT role_permissions_grant_type_check
    CHECK (grant_type IN ('GRANT','DENY'));
