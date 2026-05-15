package com.work.mautonlaundry.dtos.responses.bookingresponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BookingDetailsResponse {

    private String id;
    private String trackingNumber;
    private String bookingType;
    private String status;
    private BigDecimal totalPrice;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String discountCode;
    private BigDecimal deliveryFee;
    private Boolean express;
    private LocalDateTime returnDate;
    private LocalDateTime createdAt;
    private String laundryAgentId;
    private String pickupAgentId;
    private String returnAgentId;

    private AddressInfo pickupAddress;
    private List<BookingItemInfo> items;

    /**
     * Active handoff codes the caller is allowed to see for this booking.
     * Filtered by role: customer sees stages where they hand off to a rider
     * (pickup, delivery); the assigned laundry agent sees the laundry-side
     * stages (drop-off, return-pickup); admin sees all; delivery agents see
     * none (riders enter codes, they don't read them out).
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ActiveCodeView> activeCodes;

    @Data
    public static class AddressInfo {
        private String label;
        private String line1;
        private String city;
    }

    @Data
    public static class BookingItemInfo {
        private String itemName;
        private Integer quantity;
        private String colorType;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }

    @Data
    public static class ActiveCodeView {
        private String stage;
        private String code;
        private Instant expiresAt;
    }
}
