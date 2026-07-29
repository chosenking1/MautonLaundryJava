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
