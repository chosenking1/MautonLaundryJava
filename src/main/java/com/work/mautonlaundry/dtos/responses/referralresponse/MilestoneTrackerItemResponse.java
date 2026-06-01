package com.work.mautonlaundry.dtos.responses.referralresponse;

/** Row for the admin milestone tracker: every specialist and their progress. */
public record MilestoneTrackerItemResponse(
        String referrerId,
        String name,
        String referralCode,
        long repeatCustomers,
        int target,
        boolean reached
) {
}
