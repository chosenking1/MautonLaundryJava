package com.work.mautonlaundry.dtos.requests.deliverymanagementrequests;

import com.work.mautonlaundry.data.model.UrgencyType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.validation.constraints.Email;
import java.time.LocalDateTime;
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PickupRequest {

    private String email;

    private Long id;

    private LocalDateTime date_booked;

    private String address;

    private UrgencyType urgency;
}
