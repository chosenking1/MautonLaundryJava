package com.work.mautonlaundry.data.model.enums;

/** Lifecycle of a temporary scope upgrade request (spec §6.3). */
public enum ScopeUpgradeStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** The granted window lapsed and the scope reverted (spec §6.1). */
    EXPIRED
}
