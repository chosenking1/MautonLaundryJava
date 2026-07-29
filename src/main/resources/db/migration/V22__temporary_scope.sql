-- Permission Architecture V2 — temporary scope upgrades (spec §6, §8.3).
--
-- A Zone Coordinator covering a colleague's leave needs STATE visibility for a
-- fortnight, not forever. §6.1: the approver sets an expiry, and the upgrade
-- reverts automatically when it passes -- "enforced by a scheduled job (not by
-- the user returning the access)".
--
-- WHY THIS WORKS HERE AT ALL: §6.1 also says "the user's JWT contains the
-- temporary scope. On expiry, the next token refresh returns the permanent
-- scope." That is unimplementable in this codebase -- tokens last 7 days and
-- there is no refresh, no revocation, no blacklist, so a 2-hour upgrade would
-- last a week and §8.3's "trigger JWT invalidation" would have nothing to
-- trigger. ScopeFilterService resolves scope from the database per request
-- instead, so an expiry genuinely takes effect: the job flips is_active and the
-- next request reads the permanent scope.
--
-- Temporary scope grants NO new permissions (§6.1) -- only wider visibility. The
-- two dimensions stay independent: this table is never consulted by
-- PermissionEvaluationService.
--
-- Same typed-target shape as user_scope (V15) rather than the spec's free-text
-- scope_value, and for the same reason: a scope that names a state which does
-- not exist should not be expressible.

CREATE TABLE scope_upgrade_requests (
    id                     VARCHAR(36) PRIMARY KEY,
    requester_id           VARCHAR(36) NOT NULL REFERENCES users(id),

    -- What they have now, captured at request time. Denormalised on purpose: the
    -- approver is deciding about a specific delta, and their permanent scope may
    -- change while the request sits.
    current_scope_level    VARCHAR(20) NOT NULL,
    current_scope_value    VARCHAR(200),

    requested_scope_level  VARCHAR(20) NOT NULL,
    requested_region_id    VARCHAR(36) REFERENCES regions(id),
    requested_state_id     INTEGER     REFERENCES states(id),
    requested_lga_id       INTEGER     REFERENCES lgas(id),

    reason                 TEXT,
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    reviewed_by            VARCHAR(36) REFERENCES users(id),
    reviewed_at            TIMESTAMP,
    expiry                 TIMESTAMP,
    rejection_reason       TEXT,

    CONSTRAINT scope_upgrade_status_check
        CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
    CONSTRAINT scope_upgrade_level_check
        CHECK (requested_scope_level IN ('NATIONAL','REGIONAL','STATE','ZONE','SPECIALIST')),

    -- The requested level must name the thing it is asking for. SPECIALIST is
    -- absent deliberately: nobody requests a temporary upgrade *down* to seeing
    -- only their own work.
    CONSTRAINT scope_upgrade_target_check CHECK (
           (requested_scope_level = 'NATIONAL' AND requested_region_id IS NULL
            AND requested_state_id IS NULL AND requested_lga_id IS NULL)
        OR (requested_scope_level = 'REGIONAL' AND requested_region_id IS NOT NULL
            AND requested_state_id IS NULL AND requested_lga_id IS NULL)
        OR (requested_scope_level = 'STATE'    AND requested_state_id IS NOT NULL
            AND requested_region_id IS NULL AND requested_lga_id IS NULL)
        OR (requested_scope_level = 'ZONE'     AND requested_lga_id IS NOT NULL
            AND requested_region_id IS NULL AND requested_state_id IS NULL)
    ),

    -- An approved request must carry an expiry: §6.1 allows no permanent
    -- upgrades through this mechanism, and the database is where that is
    -- guaranteed rather than hoped for.
    CONSTRAINT scope_upgrade_decision_check CHECK (
           (status = 'PENDING'  AND reviewed_by IS NULL AND expiry IS NULL)
        OR (status = 'APPROVED' AND reviewed_by IS NOT NULL AND expiry IS NOT NULL)
        OR (status = 'REJECTED' AND reviewed_by IS NOT NULL)
        OR (status = 'EXPIRED')
    )
);

CREATE INDEX idx_scope_upgrade_pending ON scope_upgrade_requests (status, requested_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_scope_upgrade_requester ON scope_upgrade_requests (requester_id, requested_at DESC);


CREATE TABLE temporary_scope_grants (
    id                      VARCHAR(36) PRIMARY KEY,
    user_id                 VARCHAR(36) NOT NULL REFERENCES users(id),
    scope_upgrade_request_id VARCHAR(36) NOT NULL REFERENCES scope_upgrade_requests(id),

    temporary_scope_level   VARCHAR(20) NOT NULL,
    temporary_region_id     VARCHAR(36) REFERENCES regions(id),
    temporary_state_id      INTEGER     REFERENCES states(id),
    temporary_lga_id        INTEGER     REFERENCES lgas(id),

    granted_by              VARCHAR(36) NOT NULL REFERENCES users(id),
    granted_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMP   NOT NULL,
    reverted_at             TIMESTAMP,
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT temp_scope_level_check
        CHECK (temporary_scope_level IN ('NATIONAL','REGIONAL','STATE','ZONE')),
    CONSTRAINT temp_scope_target_check CHECK (
           (temporary_scope_level = 'NATIONAL' AND temporary_region_id IS NULL
            AND temporary_state_id IS NULL AND temporary_lga_id IS NULL)
        OR (temporary_scope_level = 'REGIONAL' AND temporary_region_id IS NOT NULL)
        OR (temporary_scope_level = 'STATE'    AND temporary_state_id IS NOT NULL)
        OR (temporary_scope_level = 'ZONE'     AND temporary_lga_id IS NOT NULL)
    )
);

-- At most one live upgrade per user: two overlapping temporary scopes would make
-- "what can this person see right now" unanswerable.
CREATE UNIQUE INDEX idx_temp_scope_one_active_per_user
    ON temporary_scope_grants (user_id) WHERE is_active = TRUE;

-- The read path: ScopeFilterService asks "has this user a live upgrade?" on
-- every cache miss, so it must be cheap.
CREATE INDEX idx_temp_scope_active ON temporary_scope_grants (user_id, expires_at)
    WHERE is_active = TRUE;


INSERT INTO permissions (name, description, category, active) VALUES
    ('SCOPE_UPGRADE_REQUEST', 'Request a temporary scope upgrade',        'ACCESS', TRUE),
    ('SCOPE_UPGRADE_REVIEW',  'Approve or reject a scope upgrade request','ACCESS', TRUE)
ON CONFLICT (name) DO NOTHING;

-- SCOPE_UPGRADE_REQUEST goes to every staff role: §6.1 says "any user can
-- request". Eligibility to APPROVE is not a permission -- it is whether your own
-- scope covers what is being asked for (§6.1), which SCOPE_UPGRADE_REVIEW gates
-- entry to but does not by itself satisfy.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'SCOPE_UPGRADE_REQUEST'
WHERE r.name IN ('ADMIN', 'LAUNDRY_AGENT', 'DELIVERY_AGENT')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'SCOPE_UPGRADE_REVIEW'
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
