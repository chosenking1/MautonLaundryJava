package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.dtos.requests.addressrequests.CreateAddressRequest;
import com.work.mautonlaundry.dtos.requests.addressrequests.UpdateAddressRequest;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {
    
    private final AddressRepository addressRepository;

    @Transactional
    public Address createAddress(CreateAddressRequest request) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        Address address = new Address();
        address.setUser(currentUser);
        address.setStreet(request.getStreet());
        address.setStreet_number(request.getStreetNumber());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setZip(request.getZip());
        address.setCountry(request.getCountry());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        address.setDeleted(false);
        
        return addressRepository.save(address);
    }

    public List<Address> getCurrentUserAddresses() {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        return addressRepository.findByUserAndDeletedFalse(currentUser);
    }

    public Address getAddress(String addressId) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        
        if (!address.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Access denied");
        }
        
        return address;
    }

    @Transactional
    public Address updateAddress(String addressId, UpdateAddressRequest request) {
        Address address = getAddress(addressId);
        
        if (request.getStreet() != null) {
            address.setStreet(request.getStreet());
        }
        if (request.getStreetNumber() != null) {
            address.setStreet_number(request.getStreetNumber());
        }
        if (request.getCity() != null) {
            address.setCity(request.getCity());
        }
        if (request.getState() != null) {
            address.setState(request.getState());
        }
        if (request.getZip() != null) {
            address.setZip(request.getZip());
        }
        if (request.getCountry() != null) {
            address.setCountry(request.getCountry());
        }
        if (request.getLatitude() != null) {
            address.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            address.setLongitude(request.getLongitude());
        }
        
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(String addressId) {
        Address address = getAddress(addressId);
        address.setDeleted(true);
        addressRepository.save(address);
    }

    @Transactional
    public void setDefaultAddress(String addressId) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        // Clear existing default
        addressRepository.clearDefaultForUser(currentUser.getId());
        
        // Set new default
        Address address = getAddress(addressId);
        address.setIsDefault(true);
        addressRepository.save(address);
    }
}