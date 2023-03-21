package com.work.mautonlaundry.dtos.requests.bookingrequests;

import com.work.mautonlaundry.data.model.ServiceType;
import com.work.mautonlaundry.data.model.UrgencyType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.Email;
import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
public class RegisterBookingRequest {

    private String full_name;

    private String email;

    private ServiceType type_of_service;

    private String service;

    private LocalDateTime date_booked;

    private String address;

    private UrgencyType urgency;

    private int total_price;
}
