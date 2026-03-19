package com.work.mautonlaundry.dtos.responses.deliveryresponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryNextStop {
    private String label;
    private Double lat;
    private Double lng;
    private String addressLine;
}
