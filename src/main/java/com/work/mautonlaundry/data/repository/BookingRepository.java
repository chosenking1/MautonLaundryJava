package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, String> {
    
    List<Booking> findByUserAndDeletedFalse(AppUser user);
    
    Page<Booking> findByUserAndDeletedFalse(AppUser user, Pageable pageable);
    
    Page<Booking> findByDeletedFalse(Pageable pageable);
    
    Optional<Booking> findByIdAndDeletedFalse(String id);
    
    Optional<Booking> findByTrackingNumber(String trackingNumber);
    
    List<Booking> findByStatusAndDeletedFalse(Booking.BookingStatus status);
    
    @Query("SELECT b FROM Booking b WHERE b.user = :user AND b.status = :status AND b.deleted = false")
    List<Booking> findByUserAndStatus(@Param("user") AppUser user, @Param("status") Booking.BookingStatus status);
    
    // Analytics methods
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.createdAt > :date AND b.deleted = false")
    Long countByCreatedAtAfter(@Param("date") LocalDateTime date);
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.deleted = false")
    Long countActiveBookings();
    
    @Query("SELECT SUM(b.totalPrice) FROM Booking b WHERE b.createdAt > :date AND b.deleted = false")
    Double sumTotalPriceByCreatedAtAfter(@Param("date") LocalDateTime date);
}
