package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.*;
import com.work.mautonlaundry.data.repository.*;
import com.work.mautonlaundry.dtos.requests.bookingrequests.BookingItemRequest;
import com.work.mautonlaundry.dtos.requests.bookingrequests.CreateBookingRequest;
import com.work.mautonlaundry.dtos.responses.bookingresponse.BookingDetailsResponse;
import com.work.mautonlaundry.dtos.responses.bookingresponse.CreateBookingResponse;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {
    
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final LaundryItemCatalogRepository catalogRepository;
    private final PricingEngine pricingEngine;
    private final AuditService auditService;

    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest request) {
        if (!"LAUNDRY".equals(request.getBookingType())) {
            throw new RuntimeException("Booking type not supported yet: " + request.getBookingType());
        }
        
        AppUser currentUser = SecurityUtil.getCurrentUser().orElseThrow();
        
        Address pickupAddress = addressRepository.findById(request.getPickupAddressId())
                .orElseThrow(() -> new RuntimeException("Address not found"));
        
        // Calculate pricing
        BigDecimal totalPrice = pricingEngine.calculateBookingPrice(request.getItems(), request.getExpress(), BigDecimal.ZERO);
        
        // Create booking
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID().toString());
        booking.setUser(currentUser);
        booking.setBookingType(Booking.BookingType.LAUNDRY);
        booking.setPickupAddress(pickupAddress);
        booking.setExpress(request.getExpress());
        booking.setTotalPrice(totalPrice);
        booking.setTrackingNumber(generateTrackingNumber());
        booking.setReturnDate(calculateReturnDate(request.getExpress()));
        
        Booking savedBooking = bookingRepository.save(booking);
        
        // Create booking items
        createBookingItems(savedBooking, request.getItems());
        
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
        addressInfo.setLabel("Address"); // Default label since Address doesn't have label field
        addressInfo.setLine1(booking.getPickupAddress().getStreet() + " " + booking.getPickupAddress().getStreet_number());
        addressInfo.setCity(booking.getPickupAddress().getCity());
        response.setPickupAddress(addressInfo);
        
        return response;
    }

    @Transactional
    public Booking updateBookingStatus(String bookingId, Booking.BookingStatus newStatus) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        // Validate state transition
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
        
        // Customer edits allowed only before PICKED_UP
        return booking.getStatus().ordinal() < Booking.BookingStatus.PICKED_UP.ordinal();
    }

    private void validateStatusTransition(Booking.BookingStatus current, Booking.BookingStatus next) {
        // Implement state transition validation logic
        if (current == Booking.BookingStatus.DELIVERED && next != Booking.BookingStatus.DELIVERED) {
            throw new RuntimeException("Cannot change status after delivery");
        }
    }

    private void createBookingItems(Booking booking, List<BookingItemRequest> items) {
        for (BookingItemRequest itemData : items) {
            LaundryItemCatalog catalogItem = catalogRepository.findByIdAndIsActiveTrue(itemData.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            
            BigDecimal unitPrice = "WHITE".equals(itemData.getColorType()) ? 
                    catalogItem.getBasePriceWhite() : catalogItem.getBasePriceColored();
            
            BookingLaundryItem bookingItem = new BookingLaundryItem();
            bookingItem.setBooking(booking);
            bookingItem.setItem(catalogItem);
            bookingItem.setQuantity(itemData.getQuantity());
            bookingItem.setColorType(BookingLaundryItem.ColorType.valueOf(itemData.getColorType()));
            bookingItem.setUnitPrice(unitPrice);
            bookingItem.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(itemData.getQuantity())));
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