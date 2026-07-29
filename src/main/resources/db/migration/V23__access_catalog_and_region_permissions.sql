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
