package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.BookingType;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.model.enums.HandoffCodeStatus;
import com.work.mautonlaundry.data.model.enums.HandoffStage;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingEstimateRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingItemRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.CreateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingDetailsResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingEstimateResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingTimelineEntryResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.CreateBookingResponse;
import com.work.mautonlaundry.dtos.responses.discount.DiscountApplicationResult;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.addressexception.AddressNotFoundException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
    private final DiscountService discountService;
    private final PaymentRepository paymentRepository;
    private final HandoffCodeRepository handoffCodeRepository;
    private final HandoffCodeService handoffCodeService;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;

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

        // Apply discount (if any) while still inside the booking transaction so the
        // booking's finalAmount/discountAmount are authoritative by the time the
        // client uses the returned CreateBookingResponse to initiate payment.
        applyDiscountIfPresent(savedBooking, request.getDiscountCode(), totalPrice, currentUser.getId());

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
                .freeDelivery(pricingEngine.isFreeDeliveryTierMet(itemsTotal))
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

        response.setActiveCodes(loadActiveCodesForCaller(booking, currentUser, isOwner, isAdmin, isLaundryAgent));

        populateAssignedAgentIds(response, booking);

        return response;
    }

    /**
     * Fills in laundryAgentId / pickupAgentId / returnAgentId on the response
     * from the relevant assignment rows. Without this the admin booking detail
     * page sees `null` for every agent ID even after assignment.
     *
     * Includes OFFERED rows so admin can see in-flight offers before the agent
     * accepts ("we offered this to rider X, waiting"). DECLINED/EXPIRED/
     * REJECTED rows are ignored — those agents never picked the job up.
     * Picks the most recently created matching row per slot, so after a
     * force-reassign the new agent shows up, not the previous one.
     */
    private void populateAssignedAgentIds(BookingDetailsResponse response, Booking booking) {
        laundrymanAssignmentRepository
                .findFirstByBookingAndStatusInOrderByCreatedAtDesc(
                        booking,
                        List.of(LaundrymanAssignmentStatus.ACCEPTED,
                                LaundrymanAssignmentStatus.AUTO_ACCEPTED,
                                LaundrymanAssignmentStatus.OFFERED))
                .ifPresent(a -> response.setLaundryAgentId(a.getLaundryman().getId()));

        List<DeliveryAssignmentStatus> visible = new ArrayList<>(
                DeliveryAssignmentStatus.activeAssignmentStatuses());
        visible.add(DeliveryAssignmentStatus.OFFERED);

        List<DeliveryAssignment> deliveries =
                deliveryAssignmentRepository.findByBookingOrderByCreatedAtDesc(booking);

        deliveries.stream()
                .filter(da -> da.getPhase() == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER)
                .filter(da -> visible.contains(da.getStatus()))
                .findFirst()
                .ifPresent(da -> response.setPickupAgentId(da.getDeliveryAgent().getId()));

        deliveries.stream()
                .filter(da -> da.getPhase() == DeliveryAssignmentPhase.RETURN_TO_CUSTOMER)
                .filter(da -> visible.contains(da.getStatus()))
                .findFirst()
                .ifPresent(da -> response.setReturnAgentId(da.getDeliveryAgent().getId()));
    }

    private List<BookingDetailsResponse.ActiveCodeView> loadActiveCodesForCaller(
            Booking booking, AppUser caller, boolean isOwner, boolean isAdmin, boolean isLaundryAgent) {
        java.util.Set<HandoffStage> visibleStages = new java.util.HashSet<>();
        if (isAdmin) {
            java.util.Collections.addAll(visibleStages, HandoffStage.values());
        } else {
            if (isOwner) {
                visibleStages.add(HandoffStage.CUSTOMER_TO_RIDER_PICKUP);
                visibleStages.add(HandoffStage.RIDER_TO_CUSTOMER_DELIVERY);
            }
            if (isLaundryAgent) {
                boolean isAssignedLaundryman = laundrymanAssignmentRepository.findByBooking(booking).stream()
                        .anyMatch(a -> a.getLaundryman().getId().equals(caller.getId()));
                if (isAssignedLaundryman) {
                    visibleStages.add(HandoffStage.RIDER_TO_LAUNDRY);
                    visibleStages.add(HandoffStage.LAUNDRY_TO_RIDER_RETURN);
                }
            }
        }
        if (visibleStages.isEmpty()) {
            return java.util.List.of();
        }

        // Narrow to only the stage(s) currently in play for the booking so the
        // customer doesn't see e.g. their pickup code AND their delivery code
        // side-by-side mid-flow. Admin still sees everything.
        if (!isAdmin) {
            java.util.Set<HandoffStage> activeForStatus = stagesActiveForBookingStatus(booking.getStatus());
            visibleStages.retainAll(activeForStatus);
            if (visibleStages.isEmpty()) {
                return java.util.List.of();
            }
        }

        return visibleStages.stream()
                .map(stage -> resolveLiveCodeForStage(booking, stage))
                .filter(java.util.Objects::nonNull)
                .map(code -> {
                    BookingDetailsResponse.ActiveCodeView view = new BookingDetailsResponse.ActiveCodeView();
                    view.setStage(code.getStage().name());
                    view.setCode(code.getCode());
                    view.setExpiresAt(code.getExpiresAt());
                    return view;
                })
                .toList();
    }

    /**
     * Maps a booking status to the handoff stage(s) whose codes are currently
     * meaningful for that status. Anything outside this window — e.g. the
     * delivery code while the booking is still at AT_LAUNDRY — should not be
     * surfaced to non-admins; showing both at once confuses the customer and
     * laundry agent UIs.
     */
    private java.util.Set<HandoffStage> stagesActiveForBookingStatus(BookingStatus status) {
        if (status == null) return java.util.Set.of();
        return switch (status) {
            case PICKUP_AGENT_ASSIGNED -> java.util.Set.of(HandoffStage.CUSTOMER_TO_RIDER_PICKUP);
            case PICKED_UP -> java.util.Set.of(HandoffStage.RIDER_TO_LAUNDRY);
            case DELIVERY_AGENT_ASSIGNED -> java.util.Set.of(HandoffStage.LAUNDRY_TO_RIDER_RETURN);
            case OUT_FOR_DELIVERY -> java.util.Set.of(HandoffStage.RIDER_TO_CUSTOMER_DELIVERY);
            default -> java.util.Set.of();
        };
    }

    /**
     * Returns the ACTIVE handoff code for (booking, stage) or rotates a fresh
     * one when the stored code's TTL has lapsed. Without this rotation, the
     * customer's app would happily display a code that's already past
     * {@code expiresAt}; the redeem endpoint would then reject it with
     * "Invalid or expired code", confusing both sides. issueCode invalidates
     * any prior ACTIVE code for this stage and persists a new one in the same
     * transaction.
     */
    private com.work.mautonlaundry.data.model.HandoffCode resolveLiveCodeForStage(
            Booking booking, HandoffStage stage) {
        com.work.mautonlaundry.data.model.HandoffCode existing = handoffCodeRepository
                .findByBookingAndStageAndStatus(booking, stage, HandoffCodeStatus.ACTIVE)
                .orElse(null);
        if (existing == null) return null;
        if (java.time.Instant.now().isBefore(existing.getExpiresAt())) {
            return existing;
        }
        log.info("Rotating expired handoff code on read: bookingId={} stage={} expiredAt={}",
                booking.getId(), stage, existing.getExpiresAt());
        return handoffCodeService.issueCode(booking, stage);
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
        AppUser caller = SecurityUtil.getCurrentUser().orElse(null);
        boolean isAdmin = caller != null && caller.hasRole("ADMIN");
        boolean isLaundryAgent = caller != null && caller.hasRole("LAUNDRY_AGENT");
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

            // Same role-filtered handoff codes the detail endpoint returns,
            // so the laundry-agent app's list/detail views can show the code
            // without an extra round trip per booking.
            if (caller != null) {
                boolean isOwner = booking.getUser() != null
                        && booking.getUser().getId().equals(caller.getId());
                response.setActiveCodes(loadActiveCodesForCaller(
                        booking, caller, isOwner, isAdmin, isLaundryAgent));
            }

            populateAssignedAgentIds(response, booking);

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

    private void applyDiscountIfPresent(Booking booking, String rawCode, BigDecimal orderValue, String userId) {
        String code = rawCode == null ? null : rawCode.trim();

        if (code != null && !code.isEmpty()) {
            try {
                DiscountApplicationResult result = discountService.applyDiscountAtCheckout(
                        code, userId, booking.getId(), orderValue);
                if (result.isApplied()) {
                    return;
                }
                log.warn("Discount rejected at booking creation: code={}, userId={}, result={}",
                        code, userId, result.getResult());
            } catch (RuntimeException ex) {
                // A failing discount must never prevent the booking from being created.
                log.warn("Discount application failed during booking creation: code={}, userId={}, error={}",
                        code, userId, ex.getMessage());
            }
        }

        String autoCode = discountService.findActiveCorporateCodeForUser(userId);
        if (autoCode != null) {
            try {
                DiscountApplicationResult result = discountService.applyDiscountAtCheckout(
                        autoCode, userId, booking.getId(), orderValue);
                if (!result.isApplied()) {
                    log.info("Auto corporate discount did not apply: code={}, userId={}, result={}",
                            autoCode, userId, result.getResult());
                }
            } catch (RuntimeException ex) {
                log.warn("Auto corporate discount application failed: code={}, userId={}, error={}",
                        autoCode, userId, ex.getMessage());
            }
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
        log.info("markLaundryCompleted hit: bookingId={} currentStatus={}", bookingId, currentStatus);

        if (currentStatus == BookingStatus.AT_LAUNDRY) {
            bookingStateMachine.validateTransition(currentStatus, BookingStatus.WASHING);
            booking.setStatus(BookingStatus.WASHING);
            notificationService.notifyUserBookingStatusChange(
                    booking.getUser().getEmail(),
                    booking.getId(),
                    currentStatus.name(),
                    BookingStatus.WASHING.name()
            );
            currentStatus = BookingStatus.WASHING;
        }

        // Mark the moment the laundry agent confirmed "done washing" so the
        // payment-completion hook can later distinguish "just started washing"
        // from "ready to release once paid".
        booking.setLaundryMarkedDoneAt(LocalDateTime.now());
        bookingRepository.save(booking);

        if (!isPaymentCompleted(bookingId)) {
            log.info("markLaundryCompleted held: bookingId={} payment not COMPLETED — waiting on customer", bookingId);
            notificationService.notifyDeliveryProgress(
                    booking.getUser().getEmail(),
                    booking.getId(),
                    "Your laundry is washed and ready. Please complete payment so a rider can be dispatched."
            );
            return;
        }

        log.info("markLaundryCompleted releasing for return dispatch: bookingId={}", bookingId);
        releaseForReturnDispatch(booking);
    }

    /**
     * Called by the payment-completion path. Idempotent — only acts when the
     * booking is in the "washed but unpaid" hold state (status=WASHING and
     * laundry_marked_done_at is set).
     */
    @Transactional
    public void resumeAfterPaymentIfReady(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId).orElse(null);
        if (booking == null) return;
        if (booking.getStatus() != BookingStatus.WASHING) return;
        if (booking.getLaundryMarkedDoneAt() == null) return;
        if (!isPaymentCompleted(bookingId)) return;

        log.info("Payment cleared — releasing booking {} from WASHING hold", bookingId);
        releaseForReturnDispatch(booking);
    }

    private void releaseForReturnDispatch(Booking booking) {
        BookingStatus from = booking.getStatus();
        bookingStateMachine.validateTransition(from, BookingStatus.READY_FOR_DELIVERY);
        booking.setStatus(BookingStatus.READY_FOR_DELIVERY);
        bookingRepository.save(booking);
        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                from.name(),
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

    private boolean isPaymentCompleted(String bookingId) {
        return paymentRepository.findByBooking_Id(bookingId)
                .map(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<BookingTimelineEntryResponse> getStatusTimeline(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        return bookingStatusHistoryRepository.findByBookingOrderByChangedAtAsc(booking).stream()
                .map(h -> BookingTimelineEntryResponse.builder()
                        .changedAt(h.getChangedAt())
                        .fromStatus(h.getFromStatus() == null ? null : h.getFromStatus().name())
                        .toStatus(h.getToStatus().name())
                        .triggerType(h.getTriggerType().name())
                        .actorEmail(h.getActor() == null ? null : h.getActor().getEmail())
                        .triggerCodeStage(h.getTriggerCode() == null ? null : h.getTriggerCode().getStage().name())
                        .build())
                .toList();
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

        // Cancellable only while the laundry is still with the customer. Once
        // the pickup agent has collected it, cancellation is locked.
        if (booking.getStatus() == BookingStatus.PICKED_UP ||
            booking.getStatus() == BookingStatus.AT_LAUNDRY ||
            booking.getStatus() == BookingStatus.WASHING ||
            booking.getStatus() == BookingStatus.READY_FOR_DELIVERY ||
            booking.getStatus() == BookingStatus.DELIVERY_DISPATCH_PENDING ||
            booking.getStatus() == BookingStatus.DELIVERY_AGENT_ASSIGNED ||
            booking.getStatus() == BookingStatus.OUT_FOR_DELIVERY ||
            booking.getStatus() == BookingStatus.DELIVERED ||
            booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot cancel booking once the pickup agent has collected the laundry");
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
