package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingItemRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.CreateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingDetailsResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.CreateBookingResponse;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {
    
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ServiceRepository serviceRepository;
    private final ServicePricingRepository servicePricingRepository;
    private final AuditService auditService;

    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest request) {
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        // Debug: Print what frontend is sending
        System.out.println("=== BOOKING REQUEST DEBUG ===");
        System.out.println("Request: " + request);
        System.out.println("Items count: " + (request.getItems() != null ? request.getItems().size() : "null"));
        if (request.getItems() != null) {
            for (int i = 0; i < request.getItems().size(); i++) {
                BookingItemRequest item = request.getItems().get(i);
                System.out.println("Item " + i + ": serviceId=" + item.getServiceId() + ", itemType=" + item.getItemType() + ", quantity=" + item.getQuantity());
            }
        }
        System.out.println("==============================");
        
        Address pickupAddress = addressRepository.findById(request.getPickupAddressId())
                .orElseThrow(() -> new RuntimeException("Address not found"));
        
        // Calculate pricing using service-based pricing
        BigDecimal totalPrice = calculateServiceBasedPrice(request.getItems(), request.getExpress());
        
        // Create booking with items
        Booking booking = new Booking();
        booking.setUser(currentUser);
        booking.setBookingType(Booking.BookingType.LAUNDRY);
        booking.setPickupAddress(pickupAddress);
        booking.setExpress(request.getExpress());
        booking.setTotalPrice(totalPrice);
        booking.setTrackingNumber(generateTrackingNumber());
        booking.setReturnDate(calculateReturnDate(request.getExpress()));
        
        // Create and add booking items
        createBookingItems(booking, request.getItems());
        
        // Save everything at once
        Booking savedBooking = bookingRepository.save(booking);
        
        auditService.logAction("CREATE", "BOOKING", savedBooking.getId());
        
        System.out.println("Booking created successfully: " + savedBooking.getId()); // Debug
        
        // Map to response DTO
        CreateBookingResponse response = new CreateBookingResponse();
        response.setId(savedBooking.getId());
        response.setTrackingNumber(savedBooking.getTrackingNumber());
        response.setTotalPrice(savedBooking.getTotalPrice());
        response.setReturnDate(savedBooking.getReturnDate());
        response.setStatus(savedBooking.getStatus().name());
        
        System.out.println("Returning response: " + response); // Debug
        
        return response;
    }

    public BookingDetailsResponse getBookingDetails(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        // Check if user can access this booking
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        if (!booking.getUser().getId().equals(currentUser.getId()) && 
            !currentUser.hasRole("ADMIN")) {
            throw new RuntimeException("Access denied");
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
        Page<Booking> bookings = bookingRepository.findByUserAndDeletedFalse(currentUser, pageable);
        
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
    public Booking updateBookingStatus(String bookingId, Booking.BookingStatus newStatus) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        validateStatusTransition(booking.getStatus(), newStatus);
        
        Booking.BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(newStatus);
        booking.setUpdatedAt(LocalDateTime.now());
        
        Booking updated = bookingRepository.save(booking);
        
        auditService.logAction("UPDATE_STATUS", "BOOKING", 
            String.format("Changed from %s to %s", oldStatus, newStatus));
        
        return updated;
    }

    public boolean canEditBooking(String bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        return booking.getStatus().ordinal() < Booking.BookingStatus.PICKED_UP.ordinal();
    }

    private void validateStatusTransition(Booking.BookingStatus current, Booking.BookingStatus next) {
        if (current == Booking.BookingStatus.DELIVERED && next != Booking.BookingStatus.DELIVERED) {
            throw new RuntimeException("Cannot change status after delivery");
        }
    }

    private BigDecimal calculateServiceBasedPrice(List<BookingItemRequest> items, boolean express) {
        BigDecimal total = BigDecimal.ZERO;
        
        for (BookingItemRequest item : items) {
            Services service = serviceRepository.findByIdAndActiveTrue(item.getServiceId())
                    .orElseThrow(() -> new RuntimeException("Service with ID " + item.getServiceId() + " not found or inactive"));
            
            ServicePricing pricing = servicePricingRepository
                    .findByServiceAndItemTypeAndActiveTrue(service, item.getItemType())
                    .orElseThrow(() -> new RuntimeException("Pricing not found for service " + service.getName() + " and item type " + item.getItemType()));
            
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
                    .orElseThrow(() -> new RuntimeException("Service with ID " + itemData.getServiceId() + " not found or inactive"));
            
            ServicePricing pricing = servicePricingRepository
                    .findByServiceAndItemTypeAndActiveTrue(service, itemData.getItemType())
                    .orElseThrow(() -> new RuntimeException("Pricing not found for service " + service.getName() + " and item type " + itemData.getItemType()));
            
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
}