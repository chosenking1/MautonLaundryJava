package com.work.mautonlaundry.dtos.responses.referralresponse;

/**
 * Progress toward the "100 repeat customers" milestone — repeat = referred
 * customers with 2+ completed bookings.
 */
public record MilestoneProgressResponse(
        long repeatCustomers,
        int target,
        boolean reached
) {
    public static MilestoneProgressResponse of(long repeatCustomers, int target) {
        return new MilestoneProgressResponse(repeatCustomers, target, repeatCustomers >= target);
    }
}
