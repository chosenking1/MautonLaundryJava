package com.work.mautonlaundry.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.LaundrymanAssignmentRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.AvailableDeliveryJobResponse;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.services.dispatch.DispatchJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final GeoLocationService geoLocationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.dispatch.fixed-delay-ms:30000}")
    private long dispatchDelayMs;

    @Value("${app.dispatch.job-ttl-seconds:600}")
    private long jobTtlSeconds;

    @Value("${app.dispatch.retry-delay-seconds:120}")
    private long dispatchRetryDelaySeconds;

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

    @Transactional
    public void forceDispatchJob(String bookingId, DeliveryAssignmentPhase phase) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        DeliveryAssignmentPhase resolvedPhase = phase != null ? phase : resolvePhaseFromStatus(booking.getStatus());
        if (resolvedPhase == null) {
            throw new IllegalArgumentException("Booking is not in a dispatchable status: " + booking.getStatus());
        }
        enqueueDispatchJob(booking, resolvedPhase);
        log.info("Forced dispatch job for booking {} phase {}", booking.getId(), resolvedPhase);
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
                log.info("Dispatch job expired for booking {} phase {} (age={}s)",
                        job.bookingId(), job.phase(), now - job.createdAt());
                if (shouldRetry(job)) {
                    DispatchJob retryJob = new DispatchJob(
                            job.bookingId(),
                            job.phase(),
                            job.pickupLat(),
                            job.pickupLng(),
                            0,
                            now + dispatchRetryDelaySeconds,
                            now
                    );
                    pushJob(retryJob);
                } else {
                    redisTemplate.delete(DELIVERY_JOB_KEY.formatted(job.bookingId()));
                }
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
            if (agent.isEmpty()) {
                continue;
            }

            Point agentPoint = getAgentLocation(agentId);
            if (agentPoint == null) {
                continue;
            }

            double distanceKm = geoLocationService.calculateDistance(
                    agentPoint.getY(),
                    agentPoint.getX(),
                    job.pickupLat(),
                    job.pickupLng()
            );

            AvailableDeliveryJobResponse payload = AvailableDeliveryJobResponse.builder()
                    .bookingId(job.bookingId())
                    .phase(job.phase())
                    .pickupLat(job.pickupLat())
                    .pickupLng(job.pickupLng())
                    .distanceKm(distanceKm)
                    .radiusKm(radiusKm)
                    .createdAt(job.createdAt())
                    .build();

            messagingTemplate.convertAndSend("/topic/agent/" + agentId + "/offer", payload);
            notificationService.notifyDeliveryOffer(
                    agent.get().getEmail(),
                    job.bookingId(),
                    job.phase().name(),
                    radiusKm
            );
        }
    }

    public List<AvailableDeliveryJobResponse> getAvailableJobsForAgent(String agentId) {
        Point agentPoint = getAgentLocation(agentId);
        if (agentPoint == null) {
            return List.of();
        }

        Set<String> keys = redisTemplate.keys(DELIVERY_JOB_KEY.formatted("*"));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        long now = Instant.now().getEpochSecond();
        List<AvailableDeliveryJobResponse> responses = new ArrayList<>();
        for (String key : keys) {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                continue;
            }
            DispatchJob job = parseJob(payload);
            if (job == null) {
                continue;
            }
            if (isJobAlreadyAssigned(job)) {
                continue;
            }
            if (now - job.createdAt() > jobTtlSeconds) {
                continue;
            }
            double radiusKm = RADIUS_STAGES_KM[Math.min(job.radiusIndex(), RADIUS_STAGES_KM.length - 1)];
            double distanceKm = geoLocationService.calculateDistance(
                    agentPoint.getY(),
                    agentPoint.getX(),
                    job.pickupLat(),
                    job.pickupLng()
            );
            if (distanceKm > radiusKm) {
                continue;
            }
            responses.add(AvailableDeliveryJobResponse.builder()
                    .bookingId(job.bookingId())
                    .phase(job.phase())
                    .pickupLat(job.pickupLat())
                    .pickupLng(job.pickupLng())
                    .distanceKm(distanceKm)
                    .radiusKm(radiusKm)
                    .createdAt(job.createdAt())
                    .build());
        }

        return responses;
    }

    private boolean isAgentOnline(String agentId) {
        String value = redisTemplate.opsForValue().get(AGENT_ONLINE_KEY.formatted(agentId));
        return value != null;
    }

    private boolean isJobAlreadyAssigned(DispatchJob job) {
        return deliveryAssignmentRepository.existsByBooking_IdAndPhaseAndStatusIn(
                job.bookingId(),
                job.phase(),
                DeliveryAssignmentStatus.activeAssignmentStatuses()
        );
    }

    private Point getAgentLocation(String agentId) {
        var positions = redisTemplate.opsForGeo().position(DELIVERY_AGENTS_GEO_KEY, agentId);
        if (positions == null || positions.isEmpty() || positions.get(0) == null) {
            return null;
        }
        return positions.get(0);
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

    private boolean shouldRetry(DispatchJob job) {
        Optional<Booking> bookingOpt = bookingRepository.findByIdAndDeletedFalse(job.bookingId());
        if (bookingOpt.isEmpty()) {
            return false;
        }
        Booking booking = bookingOpt.get();
        if (job.phase() == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            return booking.getStatus() == BookingStatus.PICKUP_DISPATCH_PENDING;
        }
        return booking.getStatus() == BookingStatus.DELIVERY_DISPATCH_PENDING;
    }

    private DeliveryAssignmentPhase resolvePhaseFromStatus(BookingStatus status) {
        if (status == BookingStatus.PICKUP_DISPATCH_PENDING) {
            return DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER;
        }
        if (status == BookingStatus.DELIVERY_DISPATCH_PENDING) {
            return DeliveryAssignmentPhase.RETURN_TO_CUSTOMER;
        }
        return null;
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
