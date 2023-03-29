package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.Services;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByEmail(String email);
    Optional<Booking> deleteByEmail(String email);

}
