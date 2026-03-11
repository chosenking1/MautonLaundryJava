package com.work.mautonlaundry.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.LaundrymanAssignmentRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.services.dispatch.DispatchJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchEngine {
    private static final String DELIVERY_JOBS_KEY = "delivery:jobs";
    private static final String DELIVERY_JOB_KEY = "delivery:job:%s";
    private static final String DELIVERY_AGENTS_GEO_KEY = "delivery_agents_geo";
    private static final String AGENT_ONLINE_KEY = "agent:online:%s";
    private static final double[] RADIUS_STAGES_KM = {2.0, 5.0, 10.0, 15.0};
    private static final long STAGE_WAIT_SECONDS = 30;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MetricsService metricsService;

    @Value("${app.dispatch.fixed-delay-ms:30000}")
    private long dispatchDelayMs;

    @Value("${app.dispatch.job-ttl-seconds:600}")
    private long jobTtlSeconds;

    public void enqueueDispatchJob(Booking booking, DeliveryAssignmentPhase phase) {
        Address origin = resolveOriginAddress(booking, phase);
        if (origin == null || origin.getLatitude() == null || origin.getLongitude() == null) {
            log.warn("Cannot enqueue dispatch job: missing coordinates for booking {}", booking.getId());
            return;
        }
        DispatchJob job = new DispatchJob(
                booking.getId(),
                phase,
                origin.getLatitude(),
                origin.getLongitude(),
                0,
                Instant.now().getEpochSecond(),
                Instant.now().getEpochSecond()
        );
        pushJob(job);
        metricsService.incrementJobsCreated();
    }

    @Scheduled(fixedDelayString = "${app.dispatch.fixed-delay-ms:30000}")
    @Transactional
    public void processDispatchJobs() {
        Long size = redisTemplate.opsForList().size(DELIVERY_JOBS_KEY);
        if (size == null || size == 0) {
            return;
        }

        for (int i = 0; i < size; i++) {
            String raw = redisTemplate.opsForList().rightPop(DELIVERY_JOBS_KEY);
            if (raw == null) {
                return;
            }
            DispatchJob job = parseJob(raw);
            if (job == null) {
                continue;
            }

            if (isJobAlreadyAssigned(job)) {
                continue;
            }

            long now = Instant.now().getEpochSecond();
            if (now - job.createdAt() > jobTtlSeconds) {
                metricsService.incrementJobsExpired();
                continue;
            }
            if (job.nextAttemptAt() > now) {
                pushJob(job);
                continue;
            }

            double radiusKm = RADIUS_STAGES_KM[Math.min(job.radiusIndex(), RADIUS_STAGES_KM.length - 1)];
            findAndNotifyAgents(job, radiusKm);

            int nextRadiusIndex = Math.min(job.radiusIndex() + 1, RADIUS_STAGES_KM.length - 1);
            DispatchJob nextJob = new DispatchJob(
                    job.bookingId(),
                    job.phase(),
                    job.pickupLat(),
                    job.pickupLng(),
                    nextRadiusIndex,
                    now + STAGE_WAIT_SECONDS,
                    job.createdAt()
            );
            pushJob(nextJob);
        }
    }

    private void findAndNotifyAgents(DispatchJob job, double radiusKm) {
        Circle search = new Circle(new Point(job.pickupLng(), job.pickupLat()), new Distance(radiusKm, Metrics.KILOMETERS));
        var results = redisTemplate.opsForGeo().radius(DELIVERY_AGENTS_GEO_KEY, search);
        if (results == null || results.getContent().isEmpty()) {
            return;
        }

        for (var geo : results.getContent()) {
            String agentId = geo.getContent().getName();
            if (!isAgentOnline(agentId)) {
                continue;
            }
            Optional<AppUser> agent = userRepository.findUserById(agentId);
            agent.ifPresent(appUser ->
                    notificationService.notifyDeliveryOffer(appUser.getEmail(), job.bookingId(), job.phase().name(), radiusKm)
            );
        }
    }

    private boolean isAgentOnline(String agentId) {
        String value = redisTemplate.opsForValue().get(AGENT_ONLINE_KEY.formatted(agentId));
        return value != null;
    }

    private boolean isJobAlreadyAssigned(DispatchJob job) {
        return deliveryAssignmentRepository.existsByBooking_IdAndPhaseAndStatusIn(
                job.bookingId(),
                job.phase(),
                List.of(DeliveryAssignmentStatus.ACCEPTED)
        );
    }

    private Address resolveOriginAddress(Booking booking, DeliveryAssignmentPhase phase) {
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            return booking.getPickupAddress();
        }

        Optional<LaundrymanAssignment> accepted = laundrymanAssignmentRepository
                .findFirstByBookingAndStatusInOrderByCreatedAtDesc(
                        booking,
                        List.of(LaundrymanAssignmentStatus.ACCEPTED, LaundrymanAssignmentStatus.AUTO_ACCEPTED)
                );
        if (accepted.isEmpty()) {
            return booking.getPickupAddress();
        }
        AppUser laundryman = accepted.get().getLaundryman();
        return addressRepository.findByUserAndDeletedFalse(laundryman).stream()
                .filter(addr -> addr.getLatitude() != null && addr.getLongitude() != null)
                .findFirst()
                .orElse(booking.getPickupAddress());
    }

    private void pushJob(DispatchJob job) {
        try {
            String payload = objectMapper.writeValueAsString(job);
            redisTemplate.opsForList().leftPush(DELIVERY_JOBS_KEY, payload);
            redisTemplate.opsForValue().set(DELIVERY_JOB_KEY.formatted(job.bookingId()), payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to enqueue dispatch job for booking {}: {}", job.bookingId(), ex.getMessage());
        }
    }

    private DispatchJob parseJob(String raw) {
        try {
            return objectMapper.readValue(raw, DispatchJob.class);
        } catch (Exception ex) {
            log.warn("Failed to parse dispatch job: {}", ex.getMessage());
            return null;
        }
    }
}
