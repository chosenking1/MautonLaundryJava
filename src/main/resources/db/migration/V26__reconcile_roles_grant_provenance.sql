-- Re-applies V12 (roles / role_permissions reconcile) for ddl-auto-first
-- databases where V12 is below the Flyway baseline and so never ran.
--
-- Why this is needed: the permission-evaluation query filters the role branch by
-- `role_permissions.grant_type = 'GRANT'`. That column is added by V12, not by
-- ddl-auto (role_permissions is Hibernate's plain @ManyToMany join of
-- role_id/permission_id). Without it the query throws, the /me + @PreAuthorize
-- resolver degrades closed, and even ADMIN -- whose permissions come entirely
-- through role_permissions -- resolves to nothing: you can log in but hold no
-- permission. Adding grant_type with DEFAULT 'GRANT' classifies every existing
-- and future @ManyToMany-inserted row as a GRANT, which is what they are.
--
-- Identical to V12 and fully idempotent (ADD COLUMN IF NOT EXISTS, guarded
-- constraint), so it is a no-op anywhere V12 already ran.

-- roles: display_name / created_by / created_at (spec §4.2)
ALTER TABLE roles ADD COLUMN IF NOT EXISTS display_name VARCHAR(200);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_by   VARCHAR(36) REFERENCES users(id);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP NOT NULL DEFAULT NOW();

UPDATE roles SET display_name = name WHERE display_name IS NULL;
ALTER TABLE roles ALTER COLUMN display_name SET NOT NULL;

-- role_permissions: grant provenance. Every column DEFAULTed so Hibernate's
-- (role_id, permission_id) @ManyToMany insert keeps satisfying the table.
ALTER TABLE role_permissions
    ADD COLUMN IF NOT EXISTS grant_type VARCHAR(10) NOT NULL DEFAULT 'GRANT';
ALTER TABLE role_permissions
    ADD COLUMN IF NOT EXISTS granted_by VARCHAR(36) REFERENCES users(id);
ALTER TABLE role_permissions
    ADD COLUMN IF NOT EXISTS granted_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE role_permissions DROP CONSTRAINT IF EXISTS role_permissions_grant_type_check;
ALTER TABLE role_permissions ADD CONSTRAINT role_permissions_grant_type_check
    CHECK (grant_type IN ('GRANT','DENY'));
