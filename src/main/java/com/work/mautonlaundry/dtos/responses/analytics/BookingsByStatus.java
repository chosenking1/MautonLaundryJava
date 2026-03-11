package com.work.mautonlaundry.dtos.responses.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingsByStatus {
    private String status;
    private Long count;
}
