package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.DeliveryManagement;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.DeliveryRequest;
import com.work.mautonlaundry.dtos.requests.deliverymanagementrequests.PickupStatusUpdateRequest;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.CreateDeliveryResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusResponse;
import com.work.mautonlaundry.dtos.responses.deliverymanagementresponse.PickupStatusUpdateResponse;
import com.work.mautonlaundry.services.DeliveryManagementService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import java.util.Collection;

@RestController
@RequestMapping("/api/v1/delivery")
public class DeliveryController {

    @Autowired
    private DeliveryManagementService deliveryService;

    @PreAuthorize("hasAuthority('DELIVERY_CREATE')")
    @PostMapping("/pickup")
    public CreateDeliveryResponse registerPickup(@RequestBody DeliveryRequest request){
        return deliveryService.createDeliveryDetails(request);
    }

    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    @PostMapping("/{id}/agent-address")
    public ResponseEntity<?> setLaundryAgentAddress(@PathVariable("id") Long id, @RequestParam Long agentAddress) {
        try {
            deliveryService.setLaundryManAddress(id, agentAddress);
            return ResponseEntity.ok("Laundry agent address updated successfully.");
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DELIVERY_READ')")
    public PickupStatusResponse viewDelivery(@PathVariable("id") Long id){
        return deliveryService.findPickupById(id);
    }

    @PreAuthorize("hasAuthority('DELIVERY_READ')")
    @GetMapping
    public Collection<DeliveryManagement> viewAllDelivery() {
        return deliveryService.getRepository().findAll();
    }

    @PreAuthorize("hasAuthority('DELIVERY_UPDATE')")
    @PutMapping
    public PickupStatusUpdateResponse updatePickupStatus(@RequestBody PickupStatusUpdateRequest request)
    {
        return deliveryService.pickupUpdate(request);
    }

    @PreAuthorize("hasAuthority('DELIVERY_DELETE')")
    @DeleteMapping("/{id}")
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
