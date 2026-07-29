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
