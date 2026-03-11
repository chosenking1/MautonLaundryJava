package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface LaundrymanAssignmentRepository extends JpaRepository<LaundrymanAssignment, Long> {
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
