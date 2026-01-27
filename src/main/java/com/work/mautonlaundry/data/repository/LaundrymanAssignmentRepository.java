package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LaundrymanAssignmentRepository extends JpaRepository<LaundrymanAssignment, Long> {
    List<LaundrymanAssignment> findByLaundryman(AppUser laundryman);
    List<LaundrymanAssignment> findByStatus(LaundrymanAssignment.AssignmentStatus status);
}