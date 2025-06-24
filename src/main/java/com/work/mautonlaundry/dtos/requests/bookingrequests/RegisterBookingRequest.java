package com.work.mautonlaundry.dtos.requests.bookingrequests;


import com.work.mautonlaundry.data.model.ServiceType;
import com.work.mautonlaundry.data.model.UrgencyType;
import com.work.mautonlaundry.dtos.requests.servicerequests.ServiceRequestDetail;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@AllArgsConstructor
public class RegisterBookingRequest {

    private String UserId;

    private ServiceType type_of_service;

    private List<ServiceRequestDetail> serviceDetails;
    private Boolean isWhite;

    private LocalDateTime date_booked;

    private String addressId;

    private UrgencyType urgency;



}
