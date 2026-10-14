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

    /**
     * Who the rider is meeting at this stop, and how to reach them.
     *
     * <p>A pin is only as good as the coordinates the customer dropped, and when
     * those are wrong the rider's only recourse today is to wander or abandon the
     * job. A phone number turns that into a ten-second call.
     *
     * <p>Deliberately narrow: a first name and a number, for the stop this rider
     * is currently assigned to and only while that assignment is live. It is not
     * a directory, and the rest of the contact's profile stays out of it.
     */
    private String contactName;
    private String contactPhone;
}
