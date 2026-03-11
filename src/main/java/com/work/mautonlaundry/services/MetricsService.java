package com.work.mautonlaundry.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MetricsService {
    private final Counter jobsCreated;
    private final Counter jobsAccepted;
    private final Counter jobsExpired;
    private final Timer dispatchLatency;

    public MetricsService(MeterRegistry registry) {
        this.jobsCreated = registry.counter("jobs_created");
        this.jobsAccepted = registry.counter("jobs_accepted");
        this.jobsExpired = registry.counter("jobs_expired");
        this.dispatchLatency = registry.timer("dispatch_latency");
    }

    public void incrementJobsCreated() {
        jobsCreated.increment();
    }

    public void incrementJobsAccepted() {
        jobsAccepted.increment();
    }

    public void incrementJobsExpired() {
        jobsExpired.increment();
    }

    public void recordDispatchLatency(Duration duration) {
        if (duration != null && !duration.isNegative()) {
            dispatchLatency.record(duration);
        }
    }
}
