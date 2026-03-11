package com.work.mautonlaundry.dtos.responses.deliveryresponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AcceptDeliveryJobResponse {
    private boolean accepted;
    private boolean idempotent;
    private String message;
}
