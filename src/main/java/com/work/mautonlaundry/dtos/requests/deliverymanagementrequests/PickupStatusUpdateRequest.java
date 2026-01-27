package com.work.mautonlaundry.dtos.requests.deliverymanagementrequests;

import com.work.mautonlaundry.data.model.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class PickupStatusUpdateRequest {
    private Long id;
    private DeliveryStatus deliveryStatus;
}
