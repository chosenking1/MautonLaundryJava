package com.work.mautonlaundry.data.model.enums;

/** Lifecycle of a maker-checker request (Permission Architecture V2, spec §2.3). */
public enum MakerCheckerStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Auto-expired after maker_checker_expiry_days with no decision. */
    EXPIRED
}
