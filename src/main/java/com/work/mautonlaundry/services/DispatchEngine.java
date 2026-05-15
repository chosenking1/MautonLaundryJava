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
import com.work.mautonlaundry.data.model.Role;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.LaundrymanAssignmentRepository;
import com.work.mautonlaundry.data.repository.RoleRepository;
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
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private static final long STAGE_WAIT_SECONDS = 30;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final AddressRepository addressRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
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

    @Value("${app.dispatch.operating-hours.start:08:00}")
    private String operatingHoursStart;

    @Value("${app.dispatch.operating-hours.end:20:00}")
    private String operatingHoursEnd;

    @Value("${app.dispatch.operating-hours.timezone:Africa/Lagos}")
    private String operatingHoursTimezone;

    @Value("${app.dispatch.radius-stages-km:2,5,10,15}")
    private String radiusStagesConfig;

    @Value("${app.dispatch.fallback-any-online:true}")
    private boolean fallbackAnyOnline;

    private double[] radiusStagesKm;

    private double[] getRadiusStagesKm() {
        if (radiusStagesKm != null) return radiusStagesKm;
        try {
            String[] parts = radiusStagesConfig.split(",");
            double[] parsed = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                parsed[i] = Double.parseDouble(parts[i].trim());
            }
            radiusStagesKm = parsed;
        } catch (NumberFormatException ex) {
            log.warn("Invalid app.dispatch.radius-stages-km '{}' — falling back to defaults", radiusStagesConfig);
            radiusStagesKm = new double[]{2.0, 5.0, 10.0, 15.0};
        }
        return radiusStagesKm;
    }

    public void enqueueDispatchJob(Booking booking, DeliveryAssignmentPhase phase) {
        Address origin = resolveOriginAddress(booking, phase);
        if (origin == null || origin.getLatitude() == null || origin.getLongitude() == null) {
            log.warn("Cannot enqueue dispatch job: missing coordinates for booking {} phase {}",
                    booking.getId(), phase);
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
        log.info("Dispatch job enqueued: bookingId={} phase={} origin=({},{})",
                booking.getId(), phase, origin.getLatitude(), origin.getLongitude());
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

        boolean withinHours = isWithinOperatingHours();
        long nextOpening = withinHours ? -1L : nextOpeningEpochSeconds();
        if (!withinHours) {
            log.info("Outside operating hours ({}–{} {}); deferring {} pending dispatch jobs",
                    operatingHoursStart, operatingHoursEnd, operatingHoursTimezone, size);
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

            if (!withinHours) {
                // Reset createdAt to next opening so the TTL window does not
                // burn down overnight while jobs are frozen.
                DispatchJob deferred = new DispatchJob(
                        job.bookingId(),
                        job.phase(),
                        job.pickupLat(),
                        job.pickupLng(),
                        job.radiusIndex(),
                        nextOpening,
                        nextOpening
                );
                pushJob(deferred);
                continue;
            }

            long now = Instant.now().getEpochSecond();
            if (now - job.createdAt() > jobTtlSeconds) {
                // The booking-side check is authoritative: dispatch only stops when
                // the booking is gone, cancelled, or completed (an accepted offer is
                // already filtered out above by isJobAlreadyAssigned). Otherwise,
                // reset the radius walk and the TTL clock and keep trying.
                if (isBookingTerminal(job.bookingId())) {
                    metricsService.incrementJobsExpired();
                    log.info("Dispatch job stopped for booking {} phase {} — booking inactive",
                            job.bookingId(), job.phase());
                    redisTemplate.delete(DELIVERY_JOB_KEY.formatted(job.bookingId()));
                    continue;
                }
                log.info("Dispatch TTL hit for booking {} phase {} — restarting radius walk",
                        job.bookingId(), job.phase());
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
                continue;
            }
            if (job.nextAttemptAt() > now) {
                pushJob(job);
                continue;
            }

            double[] stages = getRadiusStagesKm();
            int currentIndex = Math.min(job.radiusIndex(), stages.length - 1);
            double radiusKm = stages[currentIndex];
            int notified = findAndNotifyAgents(job, radiusKm);

            // If we've already walked to the largest ring and still notified
            // nobody, low-density agent setups (e.g. one rider for the whole
            // city) will never reach anyone via geo. Fall back to "any online
            // delivery agent" once per radius pass so the offer at least lands.
            boolean atFinalRing = currentIndex >= stages.length - 1;
            if (atFinalRing && notified == 0 && fallbackAnyOnline) {
                fallbackNotifyAnyOnlineAgent(job, radiusKm);
            }

            int nextRadiusIndex = Math.min(job.radiusIndex() + 1, stages.length - 1);
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

    private int findAndNotifyAgents(DispatchJob job, double radiusKm) {
        Circle search = new Circle(new Point(job.pickupLng(), job.pickupLat()), new Distance(radiusKm, Metrics.KILOMETERS));
        var results = redisTemplate.opsForGeo().radius(DELIVERY_AGENTS_GEO_KEY, search);
        int candidateCount = results == null ? 0 : results.getContent().size();
        log.info("Dispatch radius search: bookingId={} phase={} radiusKm={} candidatesInRadius={}",
                job.bookingId(), job.phase(), radiusKm, candidateCount);
        if (results == null || results.getContent().isEmpty()) {
            return 0;
        }

        int notified = 0;
        int skippedOffline = 0;
        for (var geo : results.getContent()) {
            String agentId = geo.getContent().getName();
            if (!isAgentOnline(agentId)) {
                skippedOffline++;
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
            notified++;
        }
        if (notified == 0) {
            log.warn("Dispatch found {} candidate(s) but notified 0: bookingId={} phase={} radiusKm={} skippedOffline={}",
                    candidateCount, job.bookingId(), job.phase(), radiusKm, skippedOffline);
        } else {
            log.info("Dispatch notified {} agent(s): bookingId={} phase={} radiusKm={}",
                    notified, job.bookingId(), job.phase(), radiusKm);
        }
        return notified;
    }

    /**
     * Last-ditch fallback when geo radius walk has exhausted its largest ring
     * with zero notifications. Scans every online delivery agent (regardless
     * of distance) and offers the job to each. Keeps low-density single-agent
     * setups working in cities where the agent is routinely outside the
     * largest ring.
     */
    private void fallbackNotifyAnyOnlineAgent(DispatchJob job, double radiusKm) {
        Optional<Role> deliveryRole = roleRepository.findByName("DELIVERY_AGENT");
        if (deliveryRole.isEmpty()) {
            log.warn("Dispatch fallback skipped: DELIVERY_AGENT role not configured");
            return;
        }
        List<AppUser> agents = userRepository.findByRoleAndDeletedFalse(deliveryRole.get());
        int notified = 0;
        for (AppUser agent : agents) {
            if (!isAgentOnline(agent.getId())) continue;

            AvailableDeliveryJobResponse payload = AvailableDeliveryJobResponse.builder()
                    .bookingId(job.bookingId())
                    .phase(job.phase())
                    .pickupLat(job.pickupLat())
                    .pickupLng(job.pickupLng())
                    .distanceKm(-1.0)
                    .radiusKm(radiusKm)
                    .createdAt(job.createdAt())
                    .build();

            messagingTemplate.convertAndSend("/topic/agent/" + agent.getId() + "/offer", payload);
            notificationService.notifyDeliveryOffer(
                    agent.getEmail(),
                    job.bookingId(),
                    job.phase().name(),
                    radiusKm
            );
            notified++;
        }
        if (notified > 0) {
            log.info("Dispatch fallback notified {} online agent(s): bookingId={} phase={}",
                    notified, job.bookingId(), job.phase());
        } else {
            log.warn("Dispatch fallback found no online agents at all: bookingId={} phase={}",
                    job.bookingId(), job.phase());
        }
    }

    public List<AvailableDeliveryJobResponse> getAvailableJobsForAgent(String agentId) {
        Point agentPoint = getAgentLocation(agentId);
        if (agentPoint == null) {
            return List.of();
        }

        Set<String> keys = findJobKeysUsingScan();
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
            double[] stages = getRadiusStagesKm();
            double radiusKm = stages[Math.min(job.radiusIndex(), stages.length - 1)];
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

    private Set<String> findJobKeysUsingScan() {
        Set<String> keys = new java.util.HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(DELIVERY_JOB_KEY.formatted("*")).count(100).build();
        try (var cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add((String) cursor.next());
            }
        } catch (Exception ex) {
            log.warn("Failed to scan for job keys: {}", ex.getMessage());
        }
        return keys;
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

    private boolean isBookingTerminal(String bookingId) {
        Optional<Booking> bookingOpt = bookingRepository.findByIdAndDeletedFalse(bookingId);
        if (bookingOpt.isEmpty()) {
            return true;
        }
        BookingStatus status = bookingOpt.get().getStatus();
        return status == BookingStatus.CANCELLED || status == BookingStatus.COMPLETED;
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

    private boolean isWithinOperatingHours() {
        ZoneId zone = ZoneId.of(operatingHoursTimezone);
        LocalTime now = LocalTime.now(zone);
        LocalTime start = LocalTime.parse(operatingHoursStart);
        LocalTime end = LocalTime.parse(operatingHoursEnd);
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        // Overnight window (e.g., 22:00–06:00).
        return !now.isBefore(start) || now.isBefore(end);
    }

    private long nextOpeningEpochSeconds() {
        ZoneId zone = ZoneId.of(operatingHoursTimezone);
        LocalTime start = LocalTime.parse(operatingHoursStart);
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime todayStart = now.with(start);
        ZonedDateTime opening = now.isBefore(todayStart) ? todayStart : todayStart.plusDays(1);
        return opening.toEpochSecond();
    }
}
