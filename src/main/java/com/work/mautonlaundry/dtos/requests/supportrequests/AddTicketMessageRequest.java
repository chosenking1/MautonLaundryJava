package com.work.mautonlaundry.dtos.requests.supportrequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddTicketMessageRequest {

    @NotBlank(message = "Please type a message")
    @Size(max = 4000, message = "Please keep the message under 4000 characters")
    private String message;
}
