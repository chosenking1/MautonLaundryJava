package com.work.mautonlaundry.dtos.requests.bookingrequests;

import com.work.mautonlaundry.data.model.ServiceType;
import com.work.mautonlaundry.data.model.UrgencyType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
public class RegisterBookingRequest {

    private String full_name;

    private String email;

    private ServiceType type_of_service;

    private JSONArray service;


    private LocalDateTime date_booked;

    private String address;

    private UrgencyType urgency;

}
