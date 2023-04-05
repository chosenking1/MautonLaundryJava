package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.DeliveryManagement;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupStatusUpdateRequest;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusUpdateResponse;
import com.work.mautonlaundry.services.DeliveryManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityNotFoundException;
import java.util.Collection;

@RestController
public class DeliveryController {

    @Autowired
    private DeliveryManagementService deliveryService;

    @PostMapping("/registerPickup")
    public PickupResponse registerPickup(@RequestBody PickupRequest request){
        return deliveryService.createPickup(request);
    }

    @GetMapping("/viewDeliveryStatus/{id}")
    public PickupStatusResponse viewDelivery(@PathVariable("id") Long id){

        return deliveryService.findPickupById(id);
    }

    @GetMapping("/viewAllDelivery")
    public Collection<DeliveryManagement> viewAllDelivery() {

        return deliveryService.getRepository().findAll();
    }

    @PutMapping("/updateDelivery")
    public PickupStatusUpdateResponse updatePickupStatus(@RequestBody PickupStatusUpdateRequest request)
    {
        return deliveryService.pickupUpdate(request);
    }

    @DeleteMapping("/delivery/{id}")
    public ResponseEntity<?> deleteDelivery(@PathVariable("id") Long id) {
        try {
            deliveryService.deletePickup(id);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
