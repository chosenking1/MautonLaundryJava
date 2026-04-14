package com.work.mautonlaundry.data.model.enums;

public enum DiscountCheckResult {
    VALID,
    INVALID_CODE,
    INACTIVE,
    EXPIRED,
    NOT_YET_VALID,
    DOMAIN_MISMATCH,
    USAGE_LIMIT_REACHED,
    TOTAL_USAGE_LIMIT_REACHED,
    PENDING_APPROVAL,
    REJECTED,
    REVOKED,
    ORDER_TOO_LOW,
    ORDER_TOO_HIGH
}