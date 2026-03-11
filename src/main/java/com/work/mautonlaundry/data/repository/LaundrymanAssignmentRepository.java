package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LaundrymanAssignmentRepository extends JpaRepository<LaundrymanAssignment, Long> {
    List<LaundrymanAssignment> findByLaundryman(AppUser laundryman);
    List<LaundrymanAssignment> findByStatus(LaundrymanAssignmentStatus status);
    Optional<LaundrymanAssignment> findByBooking(Booking booking);
    long countByLaundrymanAndStatusIn(AppUser laundryman, List<LaundrymanAssignmentStatus> statuses);
}
