package com.work.mautonlaundry.dtos.requests.deliverymanagementrequests;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.validation.constraints.Email;
import java.time.LocalDateTime;
@Setter
@Getter
@AllArgsConstructor
public class PickupRequest {

    private String email;

    private Long booking_id;

    private LocalDateTime pick_up;

    private LocalDateTime return_date;

    private String address;
}
