package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {
    List<DeliveryAssignment> findByDeliveryAgent(AppUser deliveryAgent);
    List<DeliveryAssignment> findByStatus(DeliveryAssignmentStatus status);
    List<DeliveryAssignment> findByDeliveryAgentAndStatusIn(AppUser deliveryAgent, List<DeliveryAssignmentStatus> statuses);
    List<DeliveryAssignment> findByBookingAndPhase(Booking booking, DeliveryAssignmentPhase phase);
    List<DeliveryAssignment> findByBookingOrderByCreatedAtDesc(Booking booking);
    List<DeliveryAssignment> findByBookingAndPhaseAndStatus(Booking booking, DeliveryAssignmentPhase phase, DeliveryAssignmentStatus status);
    Optional<DeliveryAssignment> findByBookingAndPhaseAndDeliveryAgent(Booking booking, DeliveryAssignmentPhase phase, AppUser deliveryAgent);
    Optional<DeliveryAssignment> findByBookingAndDeliveryAgent(Booking booking, AppUser deliveryAgent);
    long countByDeliveryAgentAndStatusIn(AppUser deliveryAgent, List<DeliveryAssignmentStatus> statuses);
    
    Optional<DeliveryAssignment> findTopByBookingAndStatusInOrderByCreatedAtDesc(Booking booking, List<DeliveryAssignmentStatus> statuses);
}
