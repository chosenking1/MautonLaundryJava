package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.BookingType;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingEstimateRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingItemRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.CreateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingDetailsResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingEstimateResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.CreateBookingResponse;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.addressexception.AddressNotFoundException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {
    
    private final BookingRepository bookingRepository;
    private final AddressRepository addressRepository;
    private final ServiceRepository serviceRepository;
    private final ServicePricingRepository servicePricingRepository;
    private final AuditService auditService;
    private final LaundryAssignmentService laundryAssignmentService;
    private final DispatchEngine dispatchEngine;
    private final NotificationService notificationService;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final BookingStateMachine bookingStateMachine;
    private final StringRedisTemplate redisTemplate;
    private final PricingEngine pricingEngine;

    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest request, String idempotencyKey) {
        String bookingIdKey = resolveIdempotencyKey(idempotencyKey);
        if (bookingIdKey != null) {
            String existingId = redisTemplate.opsForValue().get(bookingIdKey);
            if (existingId != null && !existingId.isBlank()) {
                Booking existing = bookingRepository.findById(existingId).orElse(null);
                if (existing != null) {
                    return mapCreateBookingResponse(existing);
                }
            }
        }
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        Address pickupAddress = addressRepository.findById(request.getPickupAddressId())
                .orElseThrow(() -> new AddressNotFoundException("Address not found"));
        
        // Calculate pricing using service-based pricing + delivery fee + express fee (config-based)
        BigDecimal itemsTotal = calculateServiceBasedPrice(request.getItems());
        BigDecimal deliveryFee = pricingEngine.calculateDeliveryFee(itemsTotal);
        BigDecimal expressFee = pricingEngine.calculateExpressFee(Boolean.TRUE.equals(request.getExpress()));
        BigDecimal totalPrice = itemsTotal.add(deliveryFee).add(expressFee);
        
        // Create booking with items
        Booking booking = new Booking();
        booking.setUser(currentUser);
        booking.setBookingType(BookingType.LAUNDRY);
        booking.setPickupAddress(pickupAddress);
        booking.setExpress(request.getExpress());
        booking.setTotalPrice(totalPrice);
        booking.setDeliveryFee(deliveryFee);
        booking.setFinalAmount(totalPrice);
        booking.setTrackingNumber(generateTrackingNumber());
        booking.setReturnDate(calculateReturnDate(request.getExpress()));
        booking.setStatus(BookingStatus.CREATED);
        
        // Create and add booking items
        createBookingItems(booking, request.getItems());
        
        // Save everything at once
        Booking savedBooking = bookingRepository.save(booking);
        laundryAssignmentService.createLaundryOffers(savedBooking);
        savedBooking = bookingRepository.findById(savedBooking.getId()).orElse(savedBooking);
        notificationService.notifyBookingCreated(savedBooking.getUser().getEmail(), savedBooking.getId());
        
        auditService.logAction("CREATE", "BOOKING", savedBooking.getId());
        
        CreateBookingResponse response = mapCreateBookingResponse(savedBooking);
        if (bookingIdKey != null) {
            redisTemplate.opsForValue().set(bookingIdKey, savedBooking.getId(), Duration.ofHours(24));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public BookingEstimateResponse estimateBooking(BookingEstimateRequest request) {
        BigDecimal itemsTotal = calculateServiceBasedPrice(request.getItems());
        BigDecimal deliveryFee = pricingEngine.calculateDeliveryFee(itemsTotal);
        BigDecimal expressFee = pricingEngine.calculateExpressFee(Boolean.TRUE.equals(request.getExpress()));
        BigDecimal total = itemsTotal.add(deliveryFee).add(expressFee);
        return BookingEstimateResponse.builder()
                .itemsTotal(itemsTotal)
                .deliveryFee(deliveryFee)
                .expressFee(expressFee)
                .total(total)
                .freeDelivery(deliveryFee.compareTo(BigDecimal.ZERO) == 0)
                .build();
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
            var assignments = laundrymanAssignmentRepository.findByBooking(booking);
            boolean isAssigned = assignments.stream()
                    .anyMatch(a -> a.getLaundryman().getId().equals(currentUser.getId()));
            if (isAssigned) {
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
        response.setDiscountAmount(booking.getDiscountAmount());
        response.setFinalAmount(booking.getFinalAmount());
        response.setDiscountCode(booking.getDiscountCode());
        response.setDeliveryFee(booking.getDeliveryFee());
        response.setExpress(booking.getExpress());
        response.setReturnDate(booking.getReturnDate());
        response.setCreatedAt(booking.getCreatedAt());

        
        // Map address
        BookingDetailsResponse.AddressInfo addressInfo = new BookingDetailsResponse.AddressInfo();
        addressInfo.setLabel("Address");
        addressInfo.setLine1(booking.getPickupAddress().getStreet() + " " + booking.getPickupAddress().getStreet_number());
        addressInfo.setCity(booking.getPickupAddress().getCity());
        response.setPickupAddress(addressInfo);

        // Map items
        response.setItems(mapBookingItems(booking));

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
        
        return mapBookingsToDetails(bookings);
    }

    public Page<BookingDetailsResponse> getUserBookingsByStatuses(Pageable pageable, List<BookingStatus> statuses) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        Page<Booking> bookings = bookingRepository.findByUserAndStatusInAndDeletedFalse(currentUser, statuses, pageable);
        return mapBookingsToDetails(bookings);
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
            response.setDiscountAmount(booking.getDiscountAmount());
            response.setFinalAmount(booking.getFinalAmount());
            response.setDiscountCode(booking.getDiscountCode());
            response.setReturnDate(booking.getReturnDate());
            response.setCreatedAt(booking.getCreatedAt());
            response.setItems(mapBookingItems(booking));

            if (booking.getPickupAddress() != null) {
                BookingDetailsResponse.AddressInfo addressInfo = new BookingDetailsResponse.AddressInfo();
                addressInfo.setLabel("Address");
                addressInfo.setLine1(booking.getPickupAddress().getStreet() + " " + booking.getPickupAddress().getStreet_number());
                addressInfo.setCity(booking.getPickupAddress().getCity());
                response.setPickupAddress(addressInfo);
            }

            return response;
        });
    }

    private List<BookingDetailsResponse.BookingItemInfo> mapBookingItems(Booking booking) {
        if (booking.getItems() == null) {
            return List.of();
        }
        return booking.getItems().stream().map(item -> {
            BookingDetailsResponse.BookingItemInfo info = new BookingDetailsResponse.BookingItemInfo();
            info.setItemName(item.getService().getName() + " - " + item.getItemType());
            info.setQuantity(item.getQuantity());
            info.setColorType(item.getItemType());
            info.setUnitPrice(item.getUnitPrice());
            info.setTotalPrice(item.getTotalPrice());
            return info;
        }).toList();
    }

    @Transactional
    public Booking updateBookingStatus(String bookingId, BookingStatus newStatus) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        bookingStateMachine.validateTransition(booking.getStatus(), newStatus);

        if (booking.getStatus() == newStatus) {
            return booking;
        }

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
        switch (newStatus) {
            case DELIVERY_AGENT_ASSIGNED -> notificationService.notifyDriverAssigned(booking.getUser().getEmail(), booking.getId());
            case OUT_FOR_DELIVERY -> notificationService.notifyOutForDelivery(booking.getUser().getEmail(), booking.getId());
            case DELIVERED -> notificationService.notifyDelivered(booking.getUser().getEmail(), booking.getId());
            default -> {
            }
        }

        if (newStatus == BookingStatus.PICKUP_DISPATCH_PENDING) {
            dispatchEngine.enqueueDispatchJob(booking, com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER);
        } else if (newStatus == BookingStatus.DELIVERY_DISPATCH_PENDING) {
            dispatchEngine.enqueueDispatchJob(booking, com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase.RETURN_TO_CUSTOMER);
        }
        
        auditService.logAction("UPDATE_STATUS", "BOOKING", 
            String.format("Changed from %s to %s", oldStatus, newStatus));
        
        return updated;
    }

    public boolean canEditBooking(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        return booking.getStatus() == BookingStatus.CREATED
                || booking.getStatus() == BookingStatus.LAUNDRY_ASSIGNMENT_PENDING;
    }

    private BigDecimal calculateServiceBasedPrice(List<BookingItemRequest> items) {
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

    private CreateBookingResponse mapCreateBookingResponse(Booking booking) {
        CreateBookingResponse response = new CreateBookingResponse();
        response.setId(booking.getId());
        response.setTrackingNumber(booking.getTrackingNumber());
        response.setTotalPrice(booking.getTotalPrice());
        response.setDiscountAmount(booking.getDiscountAmount());
        response.setFinalAmount(booking.getFinalAmount());
        response.setDiscountCode(booking.getDiscountCode());
        response.setReturnDate(booking.getReturnDate());
        response.setStatus(booking.getStatus().name());
        return response;
    }

    private String resolveIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        return "idempotency:booking:" + rawKey.trim();
    }

    private String generateTrackingNumber() {
        return "TRK" + System.currentTimeMillis();
    }

    private LocalDateTime calculateReturnDate(boolean express) {
        int days = express ? 3 : 7;
        return LocalDateTime.now().plusDays(days);
    }

    @Transactional
    public void markLaundryReceived(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        bookingStateMachine.validateTransition(booking.getStatus(), BookingStatus.AT_LAUNDRY);
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.AT_LAUNDRY);
        bookingRepository.save(booking);
        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                oldStatus.name(),
                BookingStatus.AT_LAUNDRY.name()
        );
    }

    @Transactional
    public void markLaundryCompleted(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        BookingStatus currentStatus = booking.getStatus();
        
        if (currentStatus == BookingStatus.AT_LAUNDRY) {
            bookingStateMachine.validateTransition(currentStatus, BookingStatus.WASHING);
            booking.setStatus(BookingStatus.WASHING);
            bookingRepository.save(booking);
            notificationService.notifyUserBookingStatusChange(
                    booking.getUser().getEmail(),
                    booking.getId(),
                    currentStatus.name(),
                    BookingStatus.WASHING.name()
            );
            currentStatus = BookingStatus.WASHING;
        }

        bookingStateMachine.validateTransition(currentStatus, BookingStatus.READY_FOR_DELIVERY);
        booking.setStatus(BookingStatus.READY_FOR_DELIVERY);
        bookingRepository.save(booking);
        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                currentStatus.name(),
                BookingStatus.READY_FOR_DELIVERY.name()
        );
        notificationService.notifyLaundryReady(booking.getUser().getEmail(), booking.getId());

        bookingStateMachine.validateTransition(BookingStatus.READY_FOR_DELIVERY, BookingStatus.DELIVERY_DISPATCH_PENDING);
        booking.setStatus(BookingStatus.DELIVERY_DISPATCH_PENDING);
        bookingRepository.save(booking);
        dispatchEngine.enqueueDispatchJob(booking, com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase.RETURN_TO_CUSTOMER);
        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                BookingStatus.READY_FOR_DELIVERY.name(),
                BookingStatus.DELIVERY_DISPATCH_PENDING.name()
        );
    }

    @Transactional
    public void acceptLaundryAssignment(String bookingId) {
        laundryAssignmentService.acceptLaundryOffer(bookingId);
    }

    @Transactional
    public void assignLaundryAgentByAdmin(String bookingId, String laundryAgentId) {
        laundryAssignmentService.assignLaundryAgentByAdmin(bookingId, laundryAgentId);
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

        if (booking.getStatus() == BookingStatus.PICKUP_DISPATCH_PENDING ||
            booking.getStatus() == BookingStatus.PICKUP_AGENT_ASSIGNED ||
            booking.getStatus() == BookingStatus.PICKED_UP ||
            booking.getStatus() == BookingStatus.AT_LAUNDRY ||
            booking.getStatus() == BookingStatus.WASHING ||
            booking.getStatus() == BookingStatus.READY_FOR_DELIVERY ||
            booking.getStatus() == BookingStatus.DELIVERY_DISPATCH_PENDING ||
            booking.getStatus() == BookingStatus.DELIVERY_AGENT_ASSIGNED ||
            booking.getStatus() == BookingStatus.OUT_FOR_DELIVERY ||
            booking.getStatus() == BookingStatus.DELIVERED ||
            booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot cancel booking that has been picked up by delivery agent");
        }

        bookingStateMachine.validateTransition(booking.getStatus(), BookingStatus.CANCELLED);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        auditService.logAction("CANCEL", "BOOKING", bookingId);
    }

    @Transactional
    public void respondToEarlyDelivery(String bookingId, boolean acceptTomorrow) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        if (!booking.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("You can only respond to early delivery for your own bookings");
        }

        booking.setCustomerEarlyDeliveryOptIn(acceptTomorrow);
        booking.setCustomerEarlyDeliveryDecisionAt(LocalDateTime.now());
        bookingRepository.save(booking);

        auditService.logAction("EARLY_DELIVERY_RESPONSE", "BOOKING", 
                acceptTomorrow ? "ACCEPTED" : "DECLINED");
    }

    public java.util.Map<String, Double> getDeliveryCoordinates(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        Address pickupAddress = booking.getPickupAddress();
        if (pickupAddress != null && pickupAddress.getLatitude() != null && pickupAddress.getLongitude() != null) {
            java.util.Map<String, Double> coords = new java.util.HashMap<>();
            coords.put("latitude", pickupAddress.getLatitude().doubleValue());
            coords.put("longitude", pickupAddress.getLongitude().doubleValue());
            return coords;
        }
        return null;
    }
}
