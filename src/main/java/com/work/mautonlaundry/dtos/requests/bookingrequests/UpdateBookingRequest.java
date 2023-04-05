package com.work.mautonlaundry.dtos.requests.bookingrequests;

import com.work.mautonlaundry.data.model.LaundryStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class UpdateBookingRequest {
    private Long id;
    private LaundryStatus laundryStatus;
}
