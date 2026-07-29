-- Permission Architecture V2 — seed data scope for existing users (spec §5).
--
-- WHY THIS MUST EXIST BEFORE ScopeFilterService IS WIRED:
-- The filter is closed-by-default. A user with no user_scope row sees NOTHING.
-- Nobody has a row today, so attaching the filter to a repository without this
-- migration would blank every list in the product at once. This is a
-- prerequisite, not a follow-up.
--
-- Seeded by role, because role is the only signal that exists today for who a
-- user is. Each choice below is deliberate; the omissions especially.
--
--   ADMIN         -> NATIONAL
--                    They run the company and the admin portal is the scoped
--                    surface. Anything narrower blanks the dashboards the
--                    moment the filter is wired.
--
--   LAUNDRY_AGENT -> SPECIALIST (themselves)
--                    Matches spec §5.2: "Only their own orders and earnings".
--                    ScopeFilterService's SPECIALIST predicate for Booking
--                    resolves through laundryman_assignments, which is exactly
--                    how a laundry agent is linked to work.
--
-- DELIBERATELY NOT SEEDED:
--
--   DELIVERY_AGENT -> no row (they will be denied by scoped queries)
--                    Riders are linked to work through delivery_assignments,
--                    NOT laundryman_assignments, and SPECIALIST resolves
--                    through the latter. Seeding them SPECIALIST would look
--                    correct and silently return zero bookings. The spec has no
--                    scope level for riders; that gap needs a decision, not a
--                    guess. Their current endpoints do not use scoped queries,
--                    so no row changes nothing today.
--
--   USER (customers) -> no row
--                    Scope governs staff visibility over other people's data.
--                    Customers read their own bookings through user-owned
--                    queries (findByUserAndDeletedFalse), never through a
--                    scoped findAll. Giving every customer a row would add a
--                    row per signup forever to support a query they never make.
--
-- ON CONFLICT DO NOTHING: never overwrite a scope an admin has already set.
-- If this is ever re-run, a hand-assigned scope must win over a role default.
--
-- assigned_by is left NULL, which means "system-seeded" (see V15).
--
-- NOT HANDLED HERE — users created AFTER this migration get no scope row and
-- will be denied. Per spec §7.4 an admin assigns scope from the portal, but the
-- bootstrap admin in DataInitializationService needs one automatically or a
-- freshly bootstrapped environment has an admin who can see nothing.

-- ADMIN -> NATIONAL (the wildcard: no target columns set, per the V15 CHECK)
INSERT INTO user_scope (user_id, scope_level, assigned_at)
SELECT u.id, 'NATIONAL', NOW()
FROM users u
JOIN roles r ON r.id = u.role_id
WHERE r.name = 'ADMIN'
  AND u.deleted = FALSE
ON CONFLICT (user_id) DO NOTHING;

-- LAUNDRY_AGENT -> SPECIALIST, pointed at themselves
INSERT INTO user_scope (user_id, scope_level, specialist_user_id, assigned_at)
SELECT u.id, 'SPECIALIST', u.id, NOW()
FROM users u
JOIN roles r ON r.id = u.role_id
WHERE r.name = 'LAUNDRY_AGENT'
  AND u.deleted = FALSE
ON CONFLICT (user_id) DO NOTHING;
