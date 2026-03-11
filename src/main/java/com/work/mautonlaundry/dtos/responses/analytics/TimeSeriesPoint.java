package com.work.mautonlaundry.dtos.responses.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPoint {
    private String timestamp;
    private Number value;
}
