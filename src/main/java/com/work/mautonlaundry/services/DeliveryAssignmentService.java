package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.LaundrymanAssignmentRepository;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.DeliveryAssignmentResponse;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.DeliveryRouteResponse;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.UnauthorizedException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final BookingRepository bookingRepository;
    private final AssignmentService assignmentService;
    private final NotificationService notificationService;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;

    public List<DeliveryAssignmentResponse> getMyOffers() {
        AppUser currentUser = getCurrentDeliveryAgent();
        return deliveryAssignmentRepository.findByDeliveryAgentAndStatusIn(
                        currentUser,
                        List.of(DeliveryAssignmentStatus.OFFERED))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<DeliveryAssignmentResponse> getMyActiveAssignments() {
        AppUser currentUser = getCurrentDeliveryAgent();
        return deliveryAssignmentRepository.findByDeliveryAgentAndStatusIn(
                        currentUser,
                        List.of(
                                DeliveryAssignmentStatus.ACCEPTED,
                                DeliveryAssignmentStatus.ENROUTE_TO_CUSTOMER,
                                DeliveryAssignmentStatus.ARRIVED_AT_CUSTOMER,
                                DeliveryAssignmentStatus.PICKED_UP_FROM_CUSTOMER,
                                DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY,
                                DeliveryAssignmentStatus.ARRIVED_AT_LAUNDRY,
                                DeliveryAssignmentStatus.DELIVERED_TO_LAUNDRY,
                                DeliveryAssignmentStatus.ENROUTE_FROM_LAUNDRY_TO_CUSTOMER,
                                DeliveryAssignmentStatus.ARRIVED_AT_CUSTOMER_FOR_DELIVERY,
                                DeliveryAssignmentStatus.DELIVERED_TO_CUSTOMER
                        ))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<DeliveryAssignmentResponse> getAssignmentsForBooking(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        return deliveryAssignmentRepository.findByBookingOrderByCreatedAtDesc(booking)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DeliveryRouteResponse getDeliveryRoute(String bookingId) {
        AppUser currentUser = getCurrentDeliveryAgent();
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        
        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByBookingAndDeliveryAgent(booking, currentUser)
                .orElseThrow(() -> new ForbiddenOperationException("No delivery assignment found for this booking"));
        
        DeliveryAssignmentPhase phase = assignment.getPhase();
        
        var pickupAddress = booking.getPickupAddress();
        var deliveryAddress = resolveDeliveryAddress(booking, phase);
        
        String instructions;
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            instructions = "Go to customer's address to pick up laundry items, then deliver to laundry shop";
        } else {
            instructions = "Go to laundry shop to pick up clean clothes, then deliver to customer's address";
        }
        
        return DeliveryRouteResponse.builder()
                .bookingId(bookingId)
                .phase(phase.name())
                .status(assignment.getStatus().name())
                .pickupAddressStreet(pickupAddress.getStreet() + " " + pickupAddress.getStreet_number())
                .pickupAddressCity(pickupAddress.getCity())
                .pickupLatitude(pickupAddress.getLatitude())
                .pickupLongitude(pickupAddress.getLongitude())
                .deliveryAddressStreet(deliveryAddress.getStreet() + " " + deliveryAddress.getStreet_number())
                .deliveryAddressCity(deliveryAddress.getCity())
                .deliveryLatitude(deliveryAddress.getLatitude())
                .deliveryLongitude(deliveryAddress.getLongitude())
                .instructions(instructions)
                .build();
    }

    private com.work.mautonlaundry.data.model.Address resolveDeliveryAddress(Booking booking, DeliveryAssignmentPhase phase) {
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            Optional<LaundrymanAssignment> laundryAssignment = laundrymanAssignmentRepository.findByBooking(booking);
            if (laundryAssignment.isPresent() && laundryAssignment.get().getLaundryman().getDefaultAddress() != null) {
                return laundryAssignment.get().getLaundryman().getDefaultAddress();
            }
            return booking.getPickupAddress();
        } else {
            return booking.getPickupAddress();
        }
    }

    @Transactional
    public boolean acceptOffer(String bookingId) {
        return assignmentService.acceptDeliveryOffer(bookingId);
    }

    @Transactional
    public boolean declineOffer(String bookingId) {
        return assignmentService.declineDeliveryOffer(bookingId);
    }

    @Transactional
    public DeliveryAssignmentResponse updateAssignmentStatus(Long assignmentId, DeliveryAssignmentStatus status) {
        DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ServiceNotFoundException("Delivery assignment not found"));
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

        boolean isAdmin = currentUser.hasRole("ADMIN");
        if (!isAdmin && !assignment.getDeliveryAgent().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Not allowed to update this assignment");
        }

        DeliveryAssignmentStatus currentStatus = assignment.getStatus();
        
        // Validate status transition based on phase
        boolean validTransition = validateStatusTransition(currentStatus, status, assignment.getPhase());
        if (!validTransition) {
            throw new IllegalArgumentException("Invalid status transition from " + currentStatus + " to " + status);
        }
        
        assignment.setStatus(status);
        assignment.setRespondedAt(LocalDateTime.now());
        
        // Update booking status based on delivery status
        updateBookingStatus(assignment, status);
        
        DeliveryAssignment saved = deliveryAssignmentRepository.save(assignment);
        
        // Notify user of status change
        notifyUserOfStatusChange(assignment, status);
        
        return toResponse(saved);
    }

    private boolean validateStatusTransition(DeliveryAssignmentStatus current, DeliveryAssignmentStatus newStatus, DeliveryAssignmentPhase phase) {
        return switch (current) {
            case ACCEPTED -> newStatus == DeliveryAssignmentStatus.ENROUTE_TO_CUSTOMER || newStatus == DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY;
            case ENROUTE_TO_CUSTOMER -> newStatus == DeliveryAssignmentStatus.ARRIVED_AT_CUSTOMER;
            case ARRIVED_AT_CUSTOMER -> newStatus == DeliveryAssignmentStatus.PICKED_UP_FROM_CUSTOMER;
            case PICKED_UP_FROM_CUSTOMER -> newStatus == DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY;
            case ENROUTE_TO_LAUNDRY -> newStatus == DeliveryAssignmentStatus.ARRIVED_AT_LAUNDRY;
            case ARRIVED_AT_LAUNDRY -> newStatus == DeliveryAssignmentStatus.DELIVERED_TO_LAUNDRY;
            case DELIVERED_TO_LAUNDRY -> phase == DeliveryAssignmentPhase.DELIVER_TO_CUSTOMER && newStatus == DeliveryAssignmentStatus.ENROUTE_FROM_LAUNDRY_TO_CUSTOMER;
            case ENROUTE_FROM_LAUNDRY_TO_CUSTOMER -> newStatus == DeliveryAssignmentStatus.ARRIVED_AT_CUSTOMER_FOR_DELIVERY;
            case ARRIVED_AT_CUSTOMER_FOR_DELIVERY -> newStatus == DeliveryAssignmentStatus.DELIVERED_TO_CUSTOMER;
            case DELIVERED_TO_CUSTOMER -> newStatus == DeliveryAssignmentStatus.COMPLETED;
            default -> false;
        };
    }

    private void updateBookingStatus(DeliveryAssignment assignment, DeliveryAssignmentStatus status) {
        Booking booking = assignment.getBooking();
        BookingStatus currentBookingStatus = booking.getStatus();
        BookingStatus newStatus = null;

        switch (status) {
            case ENROUTE_TO_CUSTOMER -> newStatus = BookingStatus.ENROUTE_FOR_COLLECTION;
            case ARRIVED_AT_CUSTOMER -> newStatus = BookingStatus.ENROUTE_FOR_COLLECTION;
            case PICKED_UP_FROM_CUSTOMER -> newStatus = BookingStatus.COLLECTED_FROM_CUSTOMER;
            case DELIVERED_TO_LAUNDRY -> newStatus = BookingStatus.RECEIVED_BY_LAUNDRY;
            case ENROUTE_FROM_LAUNDRY_TO_CUSTOMER -> newStatus = BookingStatus.ENROUTE_TO_CUSTOMER;
            case ARRIVED_AT_CUSTOMER_FOR_DELIVERY -> newStatus = BookingStatus.ENROUTE_TO_CUSTOMER;
            case DELIVERED_TO_CUSTOMER -> newStatus = BookingStatus.DELIVERED;
            case COMPLETED -> newStatus = BookingStatus.COMPLETED;
            default -> {}
        }

        if (newStatus != null && newStatus != currentBookingStatus) {
            booking.setStatus(newStatus);
            bookingRepository.save(booking);
        }
    }

    private void notifyUserOfStatusChange(DeliveryAssignment assignment, DeliveryAssignmentStatus status) {
        Booking booking = assignment.getBooking();
        String message = switch (status) {
            case ENROUTE_TO_CUSTOMER -> "Delivery agent is on their way to pick up your laundry";
            case ARRIVED_AT_CUSTOMER -> "Delivery agent has arrived at your location";
            case PICKED_UP_FROM_CUSTOMER -> "Your laundry has been picked up and is on the way to the laundry";
            case DELIVERED_TO_LAUNDRY -> "Your laundry has been delivered to the laundry shop";
            case ENROUTE_FROM_LAUNDRY_TO_CUSTOMER -> "Your laundry is ready and on the way back to you";
            case DELIVERED_TO_CUSTOMER -> "Your laundry has been delivered!";
            case COMPLETED -> "Your order has been completed. Thank you!";
            default -> null;
        };

        if (message != null) {
            notificationService.notifyUserBookingStatusChange(
                    booking.getUser().getEmail(),
                    booking.getId(),
                    booking.getStatus().name(),
                    status.name()
            );
        }
    }

    private AppUser getCurrentDeliveryAgent() {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
        if (!currentUser.hasRole("DELIVERY_AGENT")) {
            throw new ForbiddenOperationException("Only delivery agents can access this endpoint");
        }
        return currentUser;
    }

    private DeliveryAssignmentResponse toResponse(DeliveryAssignment assignment) {
        DeliveryAssignmentResponse response = new DeliveryAssignmentResponse();
        response.setAssignmentId(assignment.getId());
        response.setBookingId(assignment.getBooking().getId());
        response.setTrackingNumber(assignment.getBooking().getTrackingNumber());
        response.setDeliveryAgentId(assignment.getDeliveryAgent().getId());
        response.setDeliveryAgentName(assignment.getDeliveryAgent().getFull_name());
        response.setPhase(assignment.getPhase().name());
        response.setStatus(assignment.getStatus().name());
        response.setOfferedAt(assignment.getOfferedAt());
        response.setRespondedAt(assignment.getRespondedAt());
        response.setCurrentLatitude(assignment.getCurrentLatitude());
        response.setCurrentLongitude(assignment.getCurrentLongitude());
        response.setLastLocationUpdate(assignment.getLastLocationUpdate());
        return response;
    }
}
