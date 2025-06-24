package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.BookingResource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingResourceRepository {
    Optional<BookingResource> findByBookingId(Long bookingId);
    Optional<BookingResource> findByLaundryAgentId(String laundryAgentId);

    Optional <BookingResource>findByPickupDeliveryAgentId(String deliveryAgentId);
    Optional <BookingResource>findByReturnDeliveryAgentId(String deliveryAgentId);
}
