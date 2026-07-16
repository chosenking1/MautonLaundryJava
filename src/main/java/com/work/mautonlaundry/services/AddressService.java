package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.dtos.requests.addressrequests.CreateAddressRequest;
import com.work.mautonlaundry.dtos.requests.addressrequests.UpdateAddressRequest;
import com.work.mautonlaundry.security.util.SecurityUtil;
import com.work.mautonlaundry.services.geo.AddressGeoResolutionRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        Address saved = addressRepository.save(address);
        requestGeoResolution(saved);
        return saved;
    }

    /**
     * Asks for canonical state/LGA to be resolved from the coordinates. The
     * listener is AFTER_COMMIT and asynchronous, so this neither extends the
     * transaction across a network call nor lets a Google outage fail an
     * address. See AddressGeoService.
     *
     * <p>Note the client's free-text `state` is deliberately not used as a
     * shortcut: an address's state and LGA decide which staff can see the
     * resulting order, so they are derived server-side from coordinates, never
     * accepted from the caller.
     */
    private void requestGeoResolution(Address address) {
        if (address.getLatitude() == null || address.getLongitude() == null) {
            return;
        }
        eventPublisher.publishEvent(new AddressGeoResolutionRequested(address.getId()));
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
        Double previousLatitude = address.getLatitude();
        Double previousLongitude = address.getLongitude();

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

        boolean moved = !Objects.equals(previousLatitude, address.getLatitude())
                || !Objects.equals(previousLongitude, address.getLongitude());

        if (moved) {
            // The pin moved, so the stored geography may now be wrong -- and
            // wrong geography silently routes the order to the wrong zone.
            // Clear it and re-resolve rather than leaving the stale values in
            // place while the new ones are pending.
            address.setStateId(null);
            address.setLgaId(null);
            address.setGeoResolvedAt(null);
        }

        Address saved = addressRepository.save(address);

        // Only re-resolve when the coordinates actually changed: every call is
        // billable, and editing a street name tells us nothing new about where
        // the address is.
        if (moved) {
            requestGeoResolution(saved);
        }
        return saved;
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