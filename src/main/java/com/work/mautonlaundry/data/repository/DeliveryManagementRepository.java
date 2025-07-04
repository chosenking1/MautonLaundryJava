package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.DeliveryManagement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryManagementRepository extends JpaRepository<DeliveryManagement, Long> {
    Optional<DeliveryManagement> findByBookingId(Long bookingId);
    void deleteByBookingId(Long bookingId);
}
