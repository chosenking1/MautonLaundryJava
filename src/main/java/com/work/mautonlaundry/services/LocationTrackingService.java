package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.LocationTracking;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.LocationTrackingRepository;
import com.work.mautonlaundry.dtos.requests.locationrequests.UpdateCurrentLocationRequest;
import com.work.mautonlaundry.dtos.requests.locationrequests.UpdateLocationRequest;
import com.work.mautonlaundry.dtos.responses.locationresponses.DeliveryLocationResponse;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationTrackingService {

    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final BookingRepository bookingRepository;
    private final GeoLocationService geoLocationService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final List<DeliveryAssignmentStatus> ACTIVE_DELIVERY_STATUSES = 
            List.of(DeliveryAssignmentStatus.ACCEPTED, 
                    DeliveryAssignmentStatus.ENROUTE_TO_CUSTOMER,
                    DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY,
                    DeliveryAssignmentStatus.ENROUTE_FROM_LAUNDRY_TO_CUSTOMER);

    @Transactional
    public void updateDeliveryLocation(UpdateLocationRequest request) {
        AppUser currentUser = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new SecurityException("User not authenticated"));

        Booking booking = bookingRepository.findByIdAndDeletedFalse(request.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByBookingAndPhaseAndDeliveryAgent(booking, 
                        resolveCurrentPhase(booking.getStatus()), currentUser)
                .orElseThrow(() -> new RuntimeException("No active delivery assignment found"));

        if (assignment.getStatus() != DeliveryAssignmentStatus.ACCEPTED && 
            assignment.getStatus() != DeliveryAssignmentStatus.ENROUTE_TO_CUSTOMER &&
            assignment.getStatus() != DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY &&
            assignment.getStatus() != DeliveryAssignmentStatus.ENROUTE_FROM_LAUNDRY_TO_CUSTOMER) {
            throw new RuntimeException("Delivery assignment is not active");
        }

        assignment.setCurrentLatitude(request.getLatitude());
        assignment.setCurrentLongitude(request.getLongitude());
        assignment.setLastLocationUpdate(LocalDateTime.now());
        if (request.getBearing() != null) {
            assignment.setBearing(request.getBearing());
        }
        if (request.getSpeed() != null) {
            assignment.setSpeed(request.getSpeed());
        }
        deliveryAssignmentRepository.save(assignment);

        LocationTracking locationTracking = new LocationTracking();
        locationTracking.setBooking(booking);
        locationTracking.setUser(currentUser);
        locationTracking.setLatitude(request.getLatitude());
        locationTracking.setLongitude(request.getLongitude());
        locationTracking.setRecordedAt(LocalDateTime.now());
        locationTrackingRepository.save(locationTracking);

        DeliveryLocationResponse locationResponse = buildLocationResponse(assignment);
        
        try {
            messagingTemplate.convertAndSendToUser(
                    booking.getUser().getEmail(),
                    "/queue/location/" + booking.getId(),
                    locationResponse
            );
            log.debug("Sent location update to user: {}", booking.getUser().getEmail());
        } catch (Exception e) {
            log.warn("Failed to send real-time location update: {}", e.getMessage());
        }
    }

    @Transactional
    public void updateCurrentLocation(UpdateCurrentLocationRequest request) {
        AppUser currentUser = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new SecurityException("User not authenticated"));

        if (!currentUser.hasRole("DELIVERY_AGENT")) {
            throw new SecurityException("Only delivery agents can update their current location");
        }

        LocationTracking locationTracking = new LocationTracking();
        locationTracking.setUser(currentUser);
        locationTracking.setLatitude(request.getLatitude());
        locationTracking.setLongitude(request.getLongitude());
        locationTracking.setRecordedAt(LocalDateTime.now());
        locationTrackingRepository.save(locationTracking);

        log.debug("Updated current location for delivery agent: {}", currentUser.getEmail());
    }

    public DeliveryLocationResponse getDeliveryLocation(String bookingId, String userEmail) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        if (!booking.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new SecurityException("You are not authorized to view this delivery location");
        }

        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findTopByBookingAndStatusInOrderByCreatedAtDesc(booking, ACTIVE_DELIVERY_STATUSES)
                .orElse(null);

        if (assignment == null) {
            return null;
        }

        return buildLocationResponse(assignment);
    }

    private DeliveryLocationResponse buildLocationResponse(DeliveryAssignment assignment) {
        Booking booking = assignment.getBooking();
        
        Double destLat;
        Double destLon;
        
        if (assignment.getPhase() == com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            destLat = booking.getPickupAddress().getLatitude();
            destLon = booking.getPickupAddress().getLongitude();
        } else {
            destLat = booking.getPickupAddress().getLatitude();
            destLon = booking.getPickupAddress().getLongitude();
        }

        double distanceKm = 0;
        if (assignment.getCurrentLatitude() != null && assignment.getCurrentLongitude() != null && destLat != null && destLon != null) {
            distanceKm = geoLocationService.calculateDistance(
                    assignment.getCurrentLatitude().doubleValue(),
                    assignment.getCurrentLongitude().doubleValue(),
                    destLat,
                    destLon
            );
        }

        String estimatedArrival = calculateEstimatedArrival(distanceKm, assignment.getSpeed());

        return DeliveryLocationResponse.builder()
                .bookingId(booking.getId())
                .deliveryAgentId(assignment.getDeliveryAgent().getId())
                .deliveryAgentName(assignment.getDeliveryAgent().getFull_name())
                .deliveryAgentPhone(assignment.getDeliveryAgent().getPhone_number())
                .latitude(assignment.getCurrentLatitude())
                .longitude(assignment.getCurrentLongitude())
                .lastUpdate(assignment.getLastLocationUpdate())
                .bearing(assignment.getBearing())
                .speed(assignment.getSpeed())
                .distanceToCustomerKm(Math.round(distanceKm * 100.0) / 100.0)
                .estimatedArrival(estimatedArrival)
                .status(assignment.getStatus().name())
                .phase(assignment.getPhase().name())
                .build();
    }

    private String calculateEstimatedArrival(double distanceKm, Double speed) {
        if (speed == null || speed <= 0) {
            speed = 30.0;
        }
        
        double hours = distanceKm / speed;
        int minutes = (int) Math.ceil(hours * 60);
        
        if (minutes < 1) {
            return "Arriving now";
        } else if (minutes == 1) {
            return "1 minute";
        } else if (minutes < 60) {
            return minutes + " minutes";
        } else {
            int hrs = minutes / 60;
            int mins = minutes % 60;
            if (mins == 0) {
                return hrs + " hour" + (hrs > 1 ? "s" : "");
            }
            return hrs + "h " + mins + "m";
        }
    }

    private com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase resolveCurrentPhase(BookingStatus status) {
        if (status == BookingStatus.ENROUTE_FOR_COLLECTION || 
            status == BookingStatus.COLLECTED_FROM_CUSTOMER) {
            return com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER;
        }
        return com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase.DELIVER_TO_CUSTOMER;
    }
}
