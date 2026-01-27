package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {
    List<DeliveryAssignment> findByDeliveryAgent(AppUser deliveryAgent);
    List<DeliveryAssignment> findByStatus(DeliveryAssignment.AssignmentStatus status);
}