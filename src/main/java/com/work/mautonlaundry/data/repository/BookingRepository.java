package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}
