package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByUser_Email(String email);
    Optional<Booking> deleteByUser(User user);

    Booking findBookingById(Long id);
}
