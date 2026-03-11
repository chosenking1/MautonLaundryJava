package com.work.mautonlaundry.dtos.responses.bookingresponse;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BookingDetailsResponse {
    
    private String id;
    private String trackingNumber;
    private String bookingType;
    private String status;
    private BigDecimal totalPrice;
    private BigDecimal deliveryFee;
    private Boolean express;
    private LocalDateTime returnDate;
    private LocalDateTime createdAt;
    private String laundryAgentId;
    private String pickupAgentId;
    private String returnAgentId;
    
    private AddressInfo pickupAddress;
    private List<BookingItemInfo> items;
    
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
}
