-- Permission Architecture V2 — delegation permissions and audit before/after.
--
-- Until now nothing could grant a user a permission or set their scope through
-- the application at all: user_permissions and user_scope were writable only by
-- hand-written SQL. This seeds the permissions guarding that API.
--
-- NAMING, resolved. The spec calls the delegation gate four different things:
--   §2.2  PERMISSION_ASSIGN   ("Permission delegation is controlled by a single
--                              permission: PERMISSION_ASSIGN")
--   §3.4  PERMISSION_GRANT / PERMISSION_REVOKE  (inside the USER_MANAGEMENT module)
--   §7.1  PERMISSION_MANAGE   (the Permissions Management page)
-- They are one concept. PERMISSION_ASSIGN wins: §2.2 is the only developed
-- treatment, and it is the one the maker-checker rules in §2.3 are written
-- against. Grant and revoke are deliberately NOT split -- §2.2 describes a single
-- gate, and the escalation ceiling (you may only assign what you hold) is what
-- actually bounds the power, not the direction of the change.
--
-- SCOPE_ASSIGN is separate from PERMISSION_ASSIGN because they bound different
-- things: permissions decide what you may do, scope decides whose data you see
-- (spec §5.1). An HR admin who onboards staff may reasonably assign permissions
-- without being able to widen anyone's view to NATIONAL.
--
-- AUDIT (spec §8.5): "Every permission grant, revoke, module assignment, role
-- change, scope change ... is written to an audit_log table. This is not
-- optional." The spec creates a new audit_log with old_value/new_value JSONB;
-- this project already has a working audit_logs with AuditService, AuditAspect
-- and @Auditable, so the columns are added there instead. Two parallel audit
-- tables would be worse than either one.
--
-- Both columns are nullable: the existing audit rows have no before/after, and
-- most audited actions are not state transitions.

INSERT INTO permissions (name, description, category, active) VALUES
    ('PERMISSION_ASSIGN', 'Grant, deny or revoke a permission for a user',  'ACCESS', TRUE),
    ('SCOPE_ASSIGN',      'Set a user''s data scope',                       'ACCESS', TRUE),
    ('ACCESS_VIEW',       'View a user''s effective permissions and scope', 'ACCESS', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('PERMISSION_ASSIGN', 'SCOPE_ASSIGN', 'ACCESS_VIEW')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;


-- Spec §8.5's before/after, added to the audit table that already exists.
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS old_value JSONB;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS new_value JSONB;

-- The audit is only useful if it can be searched by what was touched.
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON audit_logs (resource, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs (timestamp DESC);
