package com.work.mautonlaundry.dtos.requests.supportrequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTicketRequest {

    /** Optional: most complaints are about an order, some are not. */
    private String bookingId;

    /** Free text; an unknown value falls back to OTHER rather than being rejected. */
    private String category;

    @NotBlank(message = "Please give your complaint a short title")
    @Size(max = 160, message = "Keep the title under 160 characters")
    private String subject;

    @NotBlank(message = "Please describe what went wrong")
    @Size(max = 4000, message = "Please keep the description under 4000 characters")
    private String message;
}
