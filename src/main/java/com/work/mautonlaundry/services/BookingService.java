package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.BookingType;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingItemRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.CreateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingDetailsResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.CreateBookingResponse;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.addressexception.AddressNotFoundException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {
    
    private final BookingRepository bookingRepository;
    private final AddressRepository addressRepository;
    private final ServiceRepository serviceRepository;
    private final ServicePricingRepository servicePricingRepository;
    private final BookingResourceRepository bookingResourceRepository;
    private final AuditService auditService;
    private final AssignmentService assignmentService;
    private final NotificationService notificationService;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest request) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        Address pickupAddress = addressRepository.findById(request.getPickupAddressId())
                .orElseThrow(() -> new AddressNotFoundException("Address not found"));
        
        // Calculate pricing using service-based pricing
        BigDecimal totalPrice = calculateServiceBasedPrice(request.getItems(), request.getExpress());
        
        // Create booking with items
        Booking booking = new Booking();
        booking.setUser(currentUser);
        booking.setBookingType(BookingType.LAUNDRY);
        booking.setPickupAddress(pickupAddress);
        booking.setExpress(request.getExpress());
        booking.setTotalPrice(totalPrice);
        booking.setTrackingNumber(generateTrackingNumber());
        booking.setReturnDate(calculateReturnDate(request.getExpress()));
        booking.setStatus(BookingStatus.PENDING_ASSIGNMENT);
        
        // Create and add booking items
        createBookingItems(booking, request.getItems());
        
        // Save everything at once
        Booking savedBooking = bookingRepository.save(booking);
        assignmentService.assignAgents(savedBooking);
        savedBooking = bookingRepository.findById(savedBooking.getId()).orElse(savedBooking);
        notificationService.notifyBookingCreated(savedBooking.getUser().getEmail(), savedBooking.getId());
        
        auditService.logAction("CREATE", "BOOKING", savedBooking.getId());
        
        // Map to response DTO
        CreateBookingResponse response = new CreateBookingResponse();
        response.setId(savedBooking.getId());
        response.setTrackingNumber(savedBooking.getTrackingNumber());
        response.setTotalPrice(savedBooking.getTotalPrice());
        response.setReturnDate(savedBooking.getReturnDate());
        response.setStatus(savedBooking.getStatus().name());
        
        return response;
    }

    public BookingDetailsResponse getBookingDetails(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        
        // Check if user can access this booking
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        boolean isOwner = booking.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.hasRole("ADMIN");
        boolean isLaundryAgent = currentUser.hasRole("LAUNDRY_AGENT");
        boolean isDeliveryAgent = currentUser.hasRole("DELIVERY_AGENT");
        
        boolean hasAccess = isOwner || isAdmin;
        
        // Check if laundry agent is assigned to this booking
        if (isLaundryAgent) {
            var assignment = laundrymanAssignmentRepository.findByBooking(booking);
            if (assignment.isPresent() && assignment.get().getLaundryman().getId().equals(currentUser.getId())) {
                hasAccess = true;
            }
        }
        
        // Check if delivery agent is assigned to this booking
        if (isDeliveryAgent) {
            var deliveryAssignments = deliveryAssignmentRepository.findByBookingOrderByCreatedAtDesc(booking);
            boolean isAssigned = deliveryAssignments.stream()
                    .anyMatch(da -> da.getDeliveryAgent().getId().equals(currentUser.getId()));
            if (isAssigned) {
                hasAccess = true;
            }
        }
        
        if (!hasAccess) {
            throw new ForbiddenOperationException("Access denied");
        }
        
        BookingDetailsResponse response = new BookingDetailsResponse();
        response.setId(booking.getId());
        response.setTrackingNumber(booking.getTrackingNumber());
        response.setBookingType(booking.getBookingType().name());
        response.setStatus(booking.getStatus().name());
        response.setTotalPrice(booking.getTotalPrice());
        response.setDeliveryFee(booking.getDeliveryFee());
        response.setExpress(booking.getExpress());
        response.setReturnDate(booking.getReturnDate());
        response.setCreatedAt(booking.getCreatedAt());
        bookingResourceRepository.findByBooking_Id(booking.getId()).ifPresent(resource -> {
            response.setLaundryAgentId(resource.getLaundryAgentId());
            response.setPickupAgentId(resource.getPickupAgentId());
            response.setReturnAgentId(resource.getReturnAgentId());
        });
        
        // Map address
        BookingDetailsResponse.AddressInfo addressInfo = new BookingDetailsResponse.AddressInfo();
        addressInfo.setLabel("Address");
        addressInfo.setLine1(booking.getPickupAddress().getStreet() + " " + booking.getPickupAddress().getStreet_number());
        addressInfo.setCity(booking.getPickupAddress().getCity());
        response.setPickupAddress(addressInfo);
        
        return response;
    }

    public Page<BookingDetailsResponse> getUserBookings(Pageable pageable) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        Page<Booking> bookings;
        if (currentUser.hasRole("LAUNDRY_AGENT")) {
            bookings = bookingRepository.findAssignedBookingsForLaundryman(currentUser, pageable);
        } else {
            bookings = bookingRepository.findByUserAndDeletedFalse(currentUser, pageable);
        }
        
        return bookings.map(booking -> {
            BookingDetailsResponse response = new BookingDetailsResponse();
            response.setId(booking.getId());
            response.setTrackingNumber(booking.getTrackingNumber());
            response.setBookingType(booking.getBookingType().name());
            response.setStatus(booking.getStatus().name());
            response.setTotalPrice(booking.getTotalPrice());
            response.setReturnDate(booking.getReturnDate());
            response.setCreatedAt(booking.getCreatedAt());
            return response;
        });
    }

    public Page<BookingDetailsResponse> getAllBookings(Pageable pageable) {
        Page<Booking> bookings = bookingRepository.findByDeletedFalse(pageable);
        return mapBookingsToDetails(bookings);
    }

    public Page<BookingDetailsResponse> getAllBookingsByStatus(Pageable pageable, BookingStatus status) {
        Page<Booking> bookings = bookingRepository.findByStatusAndDeletedFalse(status, pageable);
        return mapBookingsToDetails(bookings);
    }

    private Page<BookingDetailsResponse> mapBookingsToDetails(Page<Booking> bookings) {
        return bookings.map(booking -> {
            BookingDetailsResponse response = new BookingDetailsResponse();
            response.setId(booking.getId());
            response.setTrackingNumber(booking.getTrackingNumber());
            response.setBookingType(booking.getBookingType().name());
            response.setStatus(booking.getStatus().name());
            response.setTotalPrice(booking.getTotalPrice());
            response.setReturnDate(booking.getReturnDate());
            response.setCreatedAt(booking.getCreatedAt());
            return response;
        });
    }

    @Transactional
    public Booking updateBookingStatus(String bookingId, BookingStatus newStatus) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        
        validateStatusTransition(booking.getStatus(), newStatus);
        
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(newStatus);
        booking.setUpdatedAt(LocalDateTime.now());
        
        Booking updated = bookingRepository.save(booking);

        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                oldStatus.name(),
                newStatus.name()
        );

        if (newStatus == BookingStatus.READY_FOR_DELIVERY) {
            assignmentService.notifyNearestDeliveryAgentsForDropOff(bookingId);
        }
        
        auditService.logAction("UPDATE_STATUS", "BOOKING", 
            String.format("Changed from %s to %s", oldStatus, newStatus));
        
        return updated;
    }

    public boolean canEditBooking(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        return booking.getStatus() == BookingStatus.PENDING_ASSIGNMENT
                || booking.getStatus() == BookingStatus.PENDING_LAUNDRYMAN_ACCEPTANCE
                || booking.getStatus() == BookingStatus.LAUNDRYMAN_ACCEPTED
                || booking.getStatus() == BookingStatus.CREATED
                || booking.getStatus() == BookingStatus.CONFIRMED;
    }

    private void validateStatusTransition(BookingStatus current, BookingStatus next) {
        if ((current == BookingStatus.DELIVERED || current == BookingStatus.COMPLETED)
                && next != current) {
            throw new RuntimeException("Cannot change status after delivery");
        }
    }

    private BigDecimal calculateServiceBasedPrice(List<BookingItemRequest> items, boolean express) {
        BigDecimal total = BigDecimal.ZERO;
        
        for (BookingItemRequest item : items) {
            Services service = serviceRepository.findByIdAndActiveTrue(item.getServiceId())
                    .orElseThrow(() -> new ServiceNotFoundException("Service with ID " + item.getServiceId() + " not found or inactive"));
            
            ServicePricing pricing = servicePricingRepository
                    .findByServiceAndItemTypeAndActiveTrue(service, item.getItemType())
                    .orElseThrow(() -> new ServiceNotFoundException("Pricing not found for service " + service.getName() + " and item type " + item.getItemType()));
            
            BigDecimal itemTotal = pricing.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(itemTotal);
        }
        
        if (express) {
            total = total.multiply(BigDecimal.valueOf(1.5));
        }
        
        return total;
    }

    private void createBookingItems(Booking booking, List<BookingItemRequest> items) {
        for (BookingItemRequest itemData : items) {
            Services service = serviceRepository.findByIdAndActiveTrue(itemData.getServiceId())
                    .orElseThrow(() -> new ServiceNotFoundException("Service with ID " + itemData.getServiceId() + " not found or inactive"));
            
            ServicePricing pricing = servicePricingRepository
                    .findByServiceAndItemTypeAndActiveTrue(service, itemData.getItemType())
                    .orElseThrow(() -> new ServiceNotFoundException("Pricing not found for service " + service.getName() + " and item type " + itemData.getItemType()));
            
            BookingLaundryItem bookingItem = new BookingLaundryItem();
            bookingItem.setBooking(booking);
            bookingItem.setService(service);
            bookingItem.setItemType(itemData.getItemType());
            bookingItem.setQuantity(itemData.getQuantity());
            bookingItem.setUnitPrice(pricing.getPrice());
            bookingItem.setTotalPrice(pricing.getPrice().multiply(BigDecimal.valueOf(itemData.getQuantity())));
            
            // Add to booking's items list
            if (booking.getItems() == null) {
                booking.setItems(new ArrayList<>());
            }
            booking.getItems().add(bookingItem);
        }
    }

    private String generateTrackingNumber() {
        return "TRK" + System.currentTimeMillis();
    }

    private LocalDateTime calculateReturnDate(boolean express) {
        int days = express ? 3 : 7;
        return LocalDateTime.now().plusDays(days);
    }

    @Transactional
    public boolean acceptDeliveryOffer(String bookingId) {
        return assignmentService.acceptDeliveryOffer(bookingId);
    }

    @Transactional
    public boolean declineDeliveryOffer(String bookingId) {
        return assignmentService.declineDeliveryOffer(bookingId);
    }

    @Transactional
    public void assignLaundryAgentByAdmin(String bookingId, String laundryAgentId) {
        assignmentService.assignLaundryAgentByAdmin(bookingId, laundryAgentId);
    }

    @Transactional
    public void markLaundryReceived(String bookingId) {
        assignmentService.markLaundryReceived(bookingId);
    }

    @Transactional
    public void markLaundryCompleted(String bookingId) {
        assignmentService.markLaundryCompleted(bookingId);
    }

    @Transactional
    public void acceptLaundryAssignment(String bookingId) {
        assignmentService.acceptLaundryAssignment(bookingId);
    }

    @Transactional
    public void respondToEarlyDeliveryOption(String bookingId, boolean acceptTomorrow) {
        Booking booking = bookingRepository.findByIdAndDeletedFalseForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        boolean isOwner = booking.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.hasRole("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new ForbiddenOperationException("Only booking owner or admin can set early delivery preference");
        }

        if (booking.getStatus() != BookingStatus.READY_FOR_PICKUP) {
            throw new IllegalArgumentException("Early delivery preference is only available when booking is READY_FOR_PICKUP");
        }

        if (booking.getReturnDate() == null) {
            throw new IllegalArgumentException("Booking has no scheduled return date");
        }

        booking.setCustomerEarlyDeliveryOptIn(acceptTomorrow);
        booking.setCustomerEarlyDeliveryDecisionAt(LocalDateTime.now());

        if (acceptTomorrow) {
            booking.setDeliveryBroadcastAt(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(8, 0)));
        } else {
            LocalDateTime now = LocalDateTime.now();
            booking.setDeliveryBroadcastAt(booking.getReturnDate().isAfter(now) ? booking.getReturnDate() : now);
        }
        bookingRepository.save(booking);
    }

    @Transactional
    public void cancelBooking(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        if (!booking.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("You can only cancel your own bookings");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Booking is already cancelled");
        }

        if (booking.getStatus() == BookingStatus.PICKED_UP ||
            booking.getStatus() == BookingStatus.ENROUTE_FOR_COLLECTION ||
            booking.getStatus() == BookingStatus.COLLECTED_FROM_CUSTOMER ||
            booking.getStatus() == BookingStatus.ENROUTE_TO_CUSTOMER ||
            booking.getStatus() == BookingStatus.OUT_FOR_DELIVERY ||
            booking.getStatus() == BookingStatus.DELIVERED ||
            booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot cancel booking that has been picked up by delivery agent");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        auditService.logAction("CANCEL", "BOOKING", bookingId);
    }
}
