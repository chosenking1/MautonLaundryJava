package com.work.mautonlaundry.dtos.requests.bookingrequests;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ViewBookingRequest {
    private Long id;
    private String email;
}
