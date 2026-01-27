package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.dtos.requests.addressrequests.CreateAddressRequest;
import com.work.mautonlaundry.dtos.requests.addressrequests.UpdateAddressRequest;
import com.work.mautonlaundry.services.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {
    
    private final AddressService addressService;

    @PostMapping
    @PreAuthorize("hasAuthority('ADDRESS_CREATE')")
    public ResponseEntity<Address> createAddress(@Valid @RequestBody CreateAddressRequest request) {
        Address address = addressService.createAddress(request);
        return new ResponseEntity<>(address, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADDRESS_READ')")
    public ResponseEntity<List<Address>> getUserAddresses() {
        List<Address> addresses = addressService.getCurrentUserAddresses();
        return ResponseEntity.ok(addresses);
    }

    @GetMapping("/{addressId}")
    @PreAuthorize("hasAuthority('ADDRESS_READ')")
    public ResponseEntity<Address> getAddress(@PathVariable String addressId) {
        Address address = addressService.getAddress(addressId);
        return ResponseEntity.ok(address);
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("hasAuthority('ADDRESS_UPDATE')")
    public ResponseEntity<Address> updateAddress(@PathVariable String addressId, @Valid @RequestBody UpdateAddressRequest request) {
        Address address = addressService.updateAddress(addressId, request);
        return ResponseEntity.ok(address);
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("hasAuthority('ADDRESS_DELETE')")
    public ResponseEntity<Void> deleteAddress(@PathVariable String addressId) {
        addressService.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }
}
