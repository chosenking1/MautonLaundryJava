package com.work.mautonlaundry.dtos.responses.supportresponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessageView {
    private String id;
    /** First name only -- enough to see who replied, not a staff directory. */
    private String authorName;
    private boolean fromStaff;
    private String body;
    private LocalDateTime createdAt;
}
