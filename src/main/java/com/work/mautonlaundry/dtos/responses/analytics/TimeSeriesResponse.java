package com.work.mautonlaundry.dtos.responses.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesResponse {
    private String metric;
    private String groupBy;
    private List<TimeSeriesPoint> points;
}
