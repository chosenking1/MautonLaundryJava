package com.work.mautonlaundry.dtos.requests.deliverymanagementrequests;

import com.work.mautonlaundry.data.model.UrgencyType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryRequest {

//    private String email;

    private LocalDateTime date_booked;

    private String userAddress;

    private String agentAddress;

    private UrgencyType urgency;

    private Long booking_id;

}
