package com.work.mautonlaundry.dtos.requests.deliverymanagementrequests;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PickupStatusRequest {
    private String bookingId;
    private String status;

}
