package com.work.mautonlaundry.dtos.requests.deliveryrequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDeliveryStatusRequest {
    @NotBlank(message = "phase is required")
    private String phase;

    @NotBlank(message = "status is required")
    private String status;

    /**
     * Where the rider was when they reported this.
     *
     * <p>Used to check an "arrived" claim against the stop it refers to. Optional
     * on purpose: an older app build sends neither, and a rider whose GPS is
     * unavailable must still be able to work. A missing fix is recorded as
     * unverified rather than treated as a failure.
     */
    private Double latitude;
    private Double longitude;
}
