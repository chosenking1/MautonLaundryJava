-- Permission Architecture V2 — Step 2 of 3: the module layer (spec §3).
--
-- A module is a named bundle of permissions. It is NOT an enforcement unit
-- (spec §3.1) -- enforcement always resolves down to individual permissions.
-- A module assignment means "grant the holder every permission in this bundle,
-- minus this assignment's exclusions".
--
-- Id types follow the reconciliation in V10: modules are new so they get
-- VARCHAR(36) ids per the V5 convention, while permission_id and role_id are
-- BIGINT because permissions.id and roles.id are pre-existing BIGSERIALs.
--
-- Spec deviation (deliberate): §3.5 declares both `id UUID PRIMARY KEY` and
-- `PRIMARY KEY (assignment_id, permission_id)` on the two exclusion tables --
-- two primary keys on one table, which Postgres rejects. Resolved as a
-- surrogate id PK plus a UNIQUE constraint on the pair, preserving both intents.
--
-- No enforcement change: these tables are created empty. Nothing reads them
-- until PermissionEvaluationService is switched on.

-- Spec §3.5
CREATE TABLE modules (
    id            VARCHAR(36)  PRIMARY KEY,
    module_key    VARCHAR(100) NOT NULL UNIQUE,
    display_name  VARCHAR(200) NOT NULL,
    description   VARCHAR(500),
    created_by    VARCHAR(36)  REFERENCES users(id),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);


-- The bundle contents. Spec §3.2: adding a permission here auto-grants it to
-- every current holder of the module (minus their exclusions); removing it
-- revokes it from holders who have no individual GRANT of their own.
CREATE TABLE module_permissions (
    module_id      VARCHAR(36) NOT NULL REFERENCES modules(id),
    permission_id  BIGINT      NOT NULL REFERENCES permissions(id),
    added_by       VARCHAR(36) NOT NULL REFERENCES users(id),
    added_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (module_id, permission_id)
);

CREATE INDEX idx_module_permissions_permission ON module_permissions (permission_id);


-- Spec §3.3: modules assigned directly to a user.
CREATE TABLE user_module_assignments (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL REFERENCES users(id),
    module_id    VARCHAR(36) NOT NULL REFERENCES modules(id),
    assigned_by  VARCHAR(36) NOT NULL REFERENCES users(id),
    assigned_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT user_module_assignments_unique UNIQUE (user_id, module_id)
);

CREATE INDEX idx_user_module_assignments_user ON user_module_assignments (user_id);


-- Per-assignment carve-outs. Spec §2.1: "exclusion always wins" -- an exclusion
-- here beats the module's grant. Exclusions are specific: they block exactly the
-- named permission and do not block permissions added to the module later.
CREATE TABLE user_module_exclusions (
    id                          VARCHAR(36) PRIMARY KEY,
    user_module_assignment_id   VARCHAR(36) NOT NULL REFERENCES user_module_assignments(id) ON DELETE CASCADE,
    permission_id               BIGINT      NOT NULL REFERENCES permissions(id),
    excluded_by                 VARCHAR(36) NOT NULL REFERENCES users(id),
    excluded_at                 TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT user_module_exclusions_unique
        UNIQUE (user_module_assignment_id, permission_id)
);


-- Spec §3.5: the same module/exclusion pattern, applied to a role.
CREATE TABLE role_module_assignments (
    id           VARCHAR(36) PRIMARY KEY,
    role_id      BIGINT      NOT NULL REFERENCES roles(id),
    module_id    VARCHAR(36) NOT NULL REFERENCES modules(id),
    assigned_by  VARCHAR(36) NOT NULL REFERENCES users(id),
    assigned_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT role_module_assignments_unique UNIQUE (role_id, module_id)
);

CREATE INDEX idx_role_module_assignments_role ON role_module_assignments (role_id);


CREATE TABLE role_module_exclusions (
    id                          VARCHAR(36) PRIMARY KEY,
    role_module_assignment_id   VARCHAR(36) NOT NULL REFERENCES role_module_assignments(id) ON DELETE CASCADE,
    permission_id               BIGINT      NOT NULL REFERENCES permissions(id),
    excluded_by                 VARCHAR(36) NOT NULL REFERENCES users(id),
    excluded_at                 TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT role_module_exclusions_unique
        UNIQUE (role_module_assignment_id, permission_id)
);
