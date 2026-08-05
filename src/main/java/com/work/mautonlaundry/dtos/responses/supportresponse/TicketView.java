package com.work.mautonlaundry.dtos.responses.supportresponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketView {
    private String id;
    private String bookingId;
    private String category;
    private String subject;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;

    /** Who raised it. Populated for staff views; null on a customer's own list. */
    private String customerName;
    private String customerPhone;

    /** Null in list views, populated when a single ticket is opened. */
    private List<TicketMessageView> messages;
}
