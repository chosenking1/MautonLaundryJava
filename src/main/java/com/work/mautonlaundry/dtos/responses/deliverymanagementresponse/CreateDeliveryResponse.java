package com.work.mautonlaundry.dtos.responses.deliverymanagementresponse;

import com.work.mautonlaundry.data.model.DeliveryStatus;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateDeliveryResponse {
    private String message;
    private String userAddress;
    private String urgency;
    private Long bookingId;
    private DeliveryStatus deliveryStatus;
}
