-- Permission Architecture V2 — maker-checker for assignments beyond your own set
-- (spec §2.3, §2.4, §2.5, §8.4).
--
-- Today UserAccessService and ModuleService reject an assignment when the actor
-- does not personally hold the permission. §2.2 says that is only half the rule:
--
--   holds PERMISSION_ASSIGN? | holds the target permission? | flow
--   no                       | any                          | 403, cannot assign
--   yes                      | yes                          | executes immediately
--   yes                      | no                           | goes to maker-checker
--
-- The point is that a Permission Admin can delegate any permission in the system
-- without personally holding it, while no single person can grant something they
-- cannot themselves evaluate.
--
-- TWO SPEC DEVIATIONS, both forced:
--
-- 1. The fallback approver is a PERMISSION, not a role. §2.3 says "SUPER_ADMIN or
--    CEO serves as the fallback approver" when nobody holds the permission being
--    assigned (bootstrapping a fresh system). Neither role exists here -- the
--    roles are ADMIN, USER, LAUNDRY_AGENT, DELIVERY_AGENT -- and §11 bans
--    hardcoded role names in application code regardless, so referencing them
--    would be unimplementable twice over. MAKER_CHECKER_FALLBACK is granted to
--    ADMIN and can be granted to a CEO account when one exists. The column keeps
--    the spec's name (fallback_to_super_admin) so the schema still reads against
--    §2.4.
--
-- 2. target_user_id is NULLABLE. §2.4 declares it NOT NULL while also declaring
--    target_role_id, but a MODULE_ASSIGN to a role has no target user. The CHECK
--    below requires exactly one recipient instead.

CREATE TABLE IF NOT EXISTS system_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value TEXT         NOT NULL,
    description  VARCHAR(500),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Spec §2.5. Configurable rather than constant: how long an approval may sit
-- before it is chased, and before it is abandoned, is an operational decision
-- that will change with the size of the team.
INSERT INTO system_config (config_key, config_value, description) VALUES
    ('maker_checker_escalation_hours', '24',
     'Hours before an unapproved maker-checker request escalates to fallback approvers'),
    ('maker_checker_expiry_days', '7',
     'Days before an unapproved maker-checker request auto-expires')
ON CONFLICT (config_key) DO NOTHING;


CREATE TABLE maker_checker_requests (
    id                             VARCHAR(36) PRIMARY KEY,
    request_type                   VARCHAR(50) NOT NULL,
    maker_id                       VARCHAR(36) NOT NULL REFERENCES users(id),

    -- Exactly one recipient. See deviation 2 above.
    target_user_id                 VARCHAR(36) REFERENCES users(id),
    target_role_id                 BIGINT      REFERENCES roles(id),

    -- What is being handed over; which one is set depends on request_type.
    target_permission_id           BIGINT      REFERENCES permissions(id),
    target_module_id               VARCHAR(36) REFERENCES modules(id),
    target_assign_role_id          BIGINT      REFERENCES roles(id),

    -- Permission ids to withhold when assigning a module (spec §3.3).
    exclusions                     JSONB,

    -- The permission a checker must personally hold to be eligible (§2.3). This
    -- is what stops two equally uninformed admins rubber-stamping each other.
    required_checker_permission_id BIGINT      REFERENCES permissions(id),

    -- Set when nobody held the required permission at submission time, so the
    -- fallback approvers were notified immediately rather than after the
    -- escalation window (§8.4).
    fallback_to_super_admin        BOOLEAN     NOT NULL DEFAULT FALSE,

    status                         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    checker_id                     VARCHAR(36) REFERENCES users(id),
    checked_at                     TIMESTAMP,
    rejection_reason               TEXT,
    escalated_at                   TIMESTAMP,
    expires_at                     TIMESTAMP   NOT NULL,
    created_at                     TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT maker_checker_status_check
        CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
    CONSTRAINT maker_checker_type_check
        CHECK (request_type IN ('PERMISSION_ASSIGN','MODULE_ASSIGN','ROLE_ASSIGN')),

    -- Exactly one recipient, never both and never neither.
    CONSTRAINT maker_checker_one_recipient_check
        CHECK ((target_user_id IS NOT NULL) <> (target_role_id IS NOT NULL)),

    -- The subject must match the request type, or an approval would not know
    -- what to execute.
    CONSTRAINT maker_checker_subject_check CHECK (
           (request_type = 'PERMISSION_ASSIGN' AND target_permission_id IS NOT NULL
            AND target_module_id IS NULL AND target_assign_role_id IS NULL)
        OR (request_type = 'MODULE_ASSIGN'     AND target_module_id IS NOT NULL
            AND target_permission_id IS NULL AND target_assign_role_id IS NULL)
        OR (request_type = 'ROLE_ASSIGN'       AND target_assign_role_id IS NOT NULL
            AND target_permission_id IS NULL AND target_module_id IS NULL)
    ),

    -- A decided request must record who decided it; a pending one must not.
    CONSTRAINT maker_checker_decision_check CHECK (
           (status = 'PENDING'  AND checker_id IS NULL AND checked_at IS NULL)
        OR (status = 'EXPIRED'  AND checker_id IS NULL)
        OR (status IN ('APPROVED','REJECTED') AND checker_id IS NOT NULL AND checked_at IS NOT NULL)
    )
);

-- The approval queue reads pending requests; the scheduled jobs sweep them by
-- age. Both are hot paths for a table that only ever grows.
CREATE INDEX idx_maker_checker_pending ON maker_checker_requests (status, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_maker_checker_maker ON maker_checker_requests (maker_id, created_at DESC);
-- Drives "which requests am I eligible to approve?" (§7.5).
CREATE INDEX idx_maker_checker_required_perm ON maker_checker_requests (required_checker_permission_id)
    WHERE status = 'PENDING';


INSERT INTO permissions (name, description, category, active) VALUES
    ('MAKER_CHECKER_VIEW',     'View the maker-checker approval queue',             'ACCESS', TRUE),
    ('MAKER_CHECKER_FALLBACK', 'Approve requests no eligible checker exists for',   'ACCESS', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('MAKER_CHECKER_VIEW', 'MAKER_CHECKER_FALLBACK')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
