package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, String> {
    
    List<Booking> findByUserAndDeletedFalse(AppUser user);
    
    Page<Booking> findByUserAndDeletedFalse(AppUser user, Pageable pageable);
    
    @Query("SELECT la.booking FROM LaundrymanAssignment la WHERE la.laundryman = :laundryman " +
            "AND la.status IN ('ACCEPTED','AUTO_ACCEPTED') AND la.booking.deleted = false")
    Page<Booking> findAssignedBookingsForLaundryman(@Param("laundryman") AppUser laundryman, Pageable pageable);
    
    Page<Booking> findByDeletedFalse(Pageable pageable);
    Page<Booking> findByStatusAndDeletedFalse(BookingStatus status, Pageable pageable);

    Page<Booking> findByUserAndStatusInAndDeletedFalse(AppUser user, List<BookingStatus> statuses, Pageable pageable);
    
    Optional<Booking> findByIdAndDeletedFalse(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id AND b.deleted = false")
    Optional<Booking> findByIdAndDeletedFalseForUpdate(@Param("id") String id);
    
    Optional<Booking> findByTrackingNumber(String trackingNumber);
    
    List<Booking> findByStatusAndDeletedFalse(BookingStatus status);
    
    @Query("SELECT b FROM Booking b WHERE b.user = :user AND b.status = :status AND b.deleted = false")
    List<Booking> findByUserAndStatus(@Param("user") AppUser user, @Param("status") BookingStatus status);

    List<Booking> findByStatusAndAssignmentTimestampBeforeAndDeletedFalse(
            BookingStatus status,
            LocalDateTime assignmentTimestamp
    );

    List<Booking> findByStatusAndDeliveryBroadcastAtBeforeAndDeletedFalse(
            BookingStatus status,
            LocalDateTime deliveryBroadcastAt
    );
    
    // Analytics methods
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.createdAt > :date AND b.deleted = false")
    Long countByCreatedAtAfter(@Param("date") LocalDateTime date);
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.deleted = false")
    Long countActiveBookings();
    
    @Query("SELECT SUM(b.totalPrice) FROM Booking b WHERE b.createdAt > :date AND b.deleted = false")
    Double sumTotalPriceByCreatedAtAfter(@Param("date") LocalDateTime date);
    
    @Query("SELECT b.status, COUNT(b) FROM Booking b WHERE b.deleted = false GROUP BY b.status")
    List<Object[]> countGroupByStatus();
    
    @Query("SELECT b.status, COUNT(b) FROM Booking b WHERE b.deleted = false AND b.createdAt BETWEEN :start AND :end GROUP BY b.status")
    List<Object[]> countGroupByStatusBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.deleted = false AND b.returnDate >= b.createdAt AND b.status = 'COMPLETED'")
    Long countOnTimeDeliveries();
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.deleted = false AND b.status = 'COMPLETED'")
    Long countCompletedDeliveries();
    
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (b.updated_at - b.created_at)) / 3600) " +
            "FROM bookings b WHERE b.deleted = false AND b.status = 'COMPLETED'", nativeQuery = true)
    Double avgTurnaroundHours();

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (b.updated_at - b.created_at)) / 3600) " +
            "FROM bookings b WHERE b.deleted = false AND b.status = 'COMPLETED' " +
            "AND b.created_at BETWEEN :start AND :end", nativeQuery = true)
    Double avgTurnaroundHoursBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.deleted = false AND b.createdAt BETWEEN :start AND :end")
    Long countBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
