package com.work.mautonlaundry.dtos.requests.bookingrequests;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.ServiceType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONArray;

import javax.persistence.Entity;
import java.util.List;
@Setter
@Getter
@AllArgsConstructor
public class ServiceDto {
    private String service_name;
    private String service_category;
    private ServiceType type_of_service;
    private Double price;
//    private JSONArray service = new JSONArray();

    ServiceDto serviceDto;
    JSONArray servicesJson = new JSONArray();
//    servicesJson.put(new JSONObject(serviceDto));
    private String services = servicesJson.toString();

}
