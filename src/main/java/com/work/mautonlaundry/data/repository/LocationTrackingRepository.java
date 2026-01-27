package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.LocationTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationTrackingRepository extends JpaRepository<LocationTracking, Long> {
    
    @Query("SELECT l FROM LocationTracking l WHERE l.booking = :booking ORDER BY l.recordedAt DESC LIMIT 1")
    Optional<LocationTracking> findLatestByBooking(@Param("booking") Booking booking);
    
    List<LocationTracking> findByBookingOrderByRecordedAtDesc(Booking booking);
    
    List<LocationTracking> findByUserOrderByRecordedAtDesc(AppUser user);
}