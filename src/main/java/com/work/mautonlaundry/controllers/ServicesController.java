package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.dtos.requests.servicerequests.AddServiceRequest;
import com.work.mautonlaundry.dtos.requests.servicerequests.UpdateServiceRequest;
import com.work.mautonlaundry.dtos.responses.serviceresponse.AddServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.UpdateServiceResponse;
import com.work.mautonlaundry.dtos.responses.serviceresponse.ViewServiceResponse;
import com.work.mautonlaundry.services.ServiceOfferedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import java.util.Collection;

@RestController
public class ServicesController {

    private final ServiceOfferedService serviceOffered;

    public ServicesController(ServiceOfferedService serviceOffered) {
        this.serviceOffered = serviceOffered;
    }

//    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/addService")
    public AddServiceResponse addService(@RequestBody AddServiceRequest request){
        return serviceOffered.addService(request);
    }

    @GetMapping("/viewService/{id}")
    public ViewServiceResponse viewService(@PathVariable("id") Long id){
        return serviceOffered.findServiceById(id);
    }

    @GetMapping("/viewAllServices")
    public Collection<Services> viewAllServices() {
        return serviceOffered.getRepository().findAll();
    }

    @PutMapping("/updateService")
    public UpdateServiceResponse updateServiceDetails(@RequestBody UpdateServiceRequest request)
    {
        return serviceOffered.serviceDetailsUpdate(request);
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<?> deleteService(@PathVariable("id") Long id) {
//        try {
//            serviceOffered.deleteService(id);
//            return ResponseEntity.ok().build();
//        } catch (EntityNotFoundException e) {
//            return ResponseEntity.notFound().build();
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
        serviceOffered.deleteService(id);
        return ResponseEntity.ok().build();
    }

}
