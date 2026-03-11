package com.work.mautonlaundry.dtos.responses.deliveryresponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRouteResponse {
    private String bookingId;
    private String phase;
    private String status;
    
    private String pickupAddressStreet;
    private String pickupAddressCity;
    private Double pickupLatitude;
    private Double pickupLongitude;
    
    private String deliveryAddressStreet;
    private String deliveryAddressCity;
    private Double deliveryLatitude;
    private Double deliveryLongitude;
    
    private String instructions;
}
