package com.work.mautonlaundry.data.model.enums;

/**
 * Whether a permission row grants or denies. Applies to user_permissions and
 * role_permissions (Permission Architecture V2, spec §2.7 / §4.2).
 *
 * <p>A DENY is only ever evaluated at the user level: {@code DENY} on
 * user_permissions short-circuits the whole evaluation (spec §2.6 step 1).
 * role_permissions.grant_type exists for schema parity and provenance, but the
 * spec's evaluation order (§2.6 / §8.1) reads roles for GRANT only and never
 * consults a role-level DENY.
 */
public enum GrantType {
    GRANT,
    DENY
}
