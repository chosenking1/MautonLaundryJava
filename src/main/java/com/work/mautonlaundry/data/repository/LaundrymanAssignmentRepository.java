package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface LaundrymanAssignmentRepository extends JpaRepository<LaundrymanAssignment, Long> {

    // Active laundrymen = distinct laundrymen assigned an order since :since (e.g. last 30 days).
    @Query("SELECT COUNT(DISTINCT la.laundryman) FROM LaundrymanAssignment la WHERE la.createdAt >= :since")
    long countActiveLaundrymenSince(@Param("since") LocalDateTime since);

    // Laundryman assigned to each of a customer's bookings (latest first). Row = [bookingId, laundrymanName].
    @Query("SELECT la.booking.id, la.laundryman.full_name FROM LaundrymanAssignment la " +
            "WHERE la.booking.user.id = :userId ORDER BY la.createdAt DESC")
    List<Object[]> findLaundrymanNamesByUser(@Param("userId") String userId);

    List<LaundrymanAssignment> findByLaundryman(AppUser laundryman);
    List<LaundrymanAssignment> findByStatus(LaundrymanAssignmentStatus status);
    List<LaundrymanAssignment> findByBooking(Booking booking);
    Optional<LaundrymanAssignment> findFirstByBookingAndStatusInOrderByCreatedAtDesc(
            Booking booking,
            List<LaundrymanAssignmentStatus> statuses
    );
    Optional<LaundrymanAssignment> findByBookingAndLaundryman(Booking booking, AppUser laundryman);
    long countByLaundrymanAndStatusIn(AppUser laundryman, List<LaundrymanAssignmentStatus> statuses);
    List<LaundrymanAssignment> findByStatusAndCreatedAtBefore(LaundrymanAssignmentStatus status, LocalDateTime before);
}
