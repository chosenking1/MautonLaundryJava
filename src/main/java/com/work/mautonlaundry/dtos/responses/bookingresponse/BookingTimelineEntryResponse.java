package com.work.mautonlaundry.dtos.responses.bookingresponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingTimelineEntryResponse {
    private Instant changedAt;
    private String fromStatus;
    private String toStatus;
    private String triggerType;
    private String actorEmail;
    private String triggerCodeStage;
}
