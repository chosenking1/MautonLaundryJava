package com.work.mautonlaundry.dtos.requests.deliveryrequests;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDeliveryAssignmentStatusRequest {
    @NotBlank
    private String status;
}
