-- Re-apply V17's role-default data scopes on environments that baselined past it.
--
-- Staging was recovered with SPRING_FLYWAY_BASELINE_VERSION=24 (see V25), which
-- marks V1-V24 as applied WITHOUT running them. V17__seed_user_scope.sql was one
-- of them, so no admin ever got a user_scope row. ScopeFilterService is
-- closed-by-default, so every admin except the bootstrap one (which
-- DataInitializationService seeds at startup) saw an empty booking list --
-- GET /api/v1/admin/bookings returned totalElements: 0 with data present.
--
-- Prod will be cut over the same way, so it needs this too.
--
-- Same rules as V17, for the same reasons (see its header): ADMIN -> NATIONAL,
-- LAUNDRY_AGENT -> SPECIALIST(self), riders and customers deliberately left
-- unseeded. ON CONFLICT DO NOTHING keeps any scope an admin has set by hand, so
-- this is a no-op wherever V17 actually ran.

-- ADMIN -> NATIONAL (the wildcard: no target columns set)
INSERT INTO user_scope (user_id, scope_level, assigned_at)
SELECT u.id, 'NATIONAL', NOW()
FROM users u
JOIN roles r ON r.id = u.role_id
WHERE r.name = 'ADMIN'
  AND COALESCE(u.deleted, FALSE) = FALSE
ON CONFLICT (user_id) DO NOTHING;

-- LAUNDRY_AGENT -> SPECIALIST, pointed at themselves
INSERT INTO user_scope (user_id, scope_level, specialist_user_id, assigned_at)
SELECT u.id, 'SPECIALIST', u.id, NOW()
FROM users u
JOIN roles r ON r.id = u.role_id
WHERE r.name = 'LAUNDRY_AGENT'
  AND COALESCE(u.deleted, FALSE) = FALSE
ON CONFLICT (user_id) DO NOTHING;
