package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.BookingResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingResourceRepository extends JpaRepository<BookingResource, String> {
    Optional<BookingResource> findByBooking_Id(String bookingId);
    Optional<BookingResource> findByLaundryAgentId(String laundryAgentId);

    Optional<BookingResource> findByPickupAgentId(String deliveryAgentId);
    Optional<BookingResource> findByReturnAgentId(String deliveryAgentId);
}
