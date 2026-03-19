package com.work.mautonlaundry.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.DeliveryRouteHistory;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.DeliveryRouteHistoryRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.trackingrequests.DeliveryLocationUpdateMessage;
import com.work.mautonlaundry.dtos.responses.trackingresponses.TrackingUpdatePayload;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import com.work.mautonlaundry.services.tracking.LiveLocationSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingService {
    private static final String AGENT_LOCATION_KEY = "agent:location:%s";
    private static final String BOOKING_AGENT_KEY = "booking:agent:%s";
    private static final String TRACKING_KEY = "tracking:%s";
    private static final String BOOKING_LAST_PERSIST_KEY = "booking:lastpersist:%s";
    private static final String DELIVERY_AGENTS_GEO_KEY = "delivery_agents_geo";
    private static final String AGENT_ONLINE_KEY = "agent:online:%s";
    private static final long ROUTE_PERSIST_INTERVAL_SECONDS = 60;

    private static final List<DeliveryAssignmentStatus> ACTIVE_TRACKING_STATUSES =
            List.of(
                    DeliveryAssignmentStatus.ACCEPTED,
                    DeliveryAssignmentStatus.ENROUTE_TO_CUSTOMER,
                    DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY,
                    DeliveryAssignmentStatus.ENROUTE_FROM_LAUNDRY_TO_CUSTOMER,
                    DeliveryAssignmentStatus.ARRIVED_AT_CUSTOMER,
                    DeliveryAssignmentStatus.ARRIVED_AT_LAUNDRY,
                    DeliveryAssignmentStatus.ARRIVED_AT_CUSTOMER_FOR_DELIVERY,
                    DeliveryAssignmentStatus.PICKED_UP_FROM_CUSTOMER,
                    DeliveryAssignmentStatus.PICKED_UP_FROM_LAUNDRY
            );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryRouteHistoryRepository deliveryRouteHistoryRepository;

    @Transactional
    public void handleLocationUpdate(DeliveryLocationUpdateMessage message) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(message.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser agent = userRepository.findUserById(message.getAgentId())
                .orElseThrow(() -> new UserNotFoundException("Delivery agent not found"));

        if (!agent.hasRole("DELIVERY_AGENT")) {
            throw new ForbiddenOperationException("User is not a delivery agent");
        }

        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByBookingAndDeliveryAgent(booking, agent)
                .orElseThrow(() -> new ForbiddenOperationException("No delivery assignment found for this booking"));

        if (!ACTIVE_TRACKING_STATUSES.contains(assignment.getStatus())) {
            throw new ForbiddenOperationException("Delivery assignment is not active for tracking");
        }

        long timestamp = resolveTimestamp(message.getTimestamp());
        LiveLocationSnapshot snapshot = new LiveLocationSnapshot(
                message.getLatitude(),
                message.getLongitude(),
                timestamp
        );

        storeLiveLocation(agent.getId(), booking.getId(), snapshot);
        updateAgentGeoIndex(agent.getId(), snapshot);
        updateAgentPresence(agent.getId());
        broadcastLiveLocation(booking.getId(), snapshot);
        persistRouteHistoryIfDue(booking, agent, snapshot);
    }

    public Optional<LiveLocationSnapshot> getLatestLocationForBooking(String bookingId) {
        String trackingPayload = redisTemplate.opsForValue().get(TRACKING_KEY.formatted(bookingId));
        if (trackingPayload != null && !trackingPayload.isBlank()) {
            try {
                return Optional.of(objectMapper.readValue(trackingPayload, LiveLocationSnapshot.class));
            } catch (Exception ex) {
                log.warn("Failed to parse tracking payload for booking {}: {}", bookingId, ex.getMessage());
            }
        }

        String bookingKey = BOOKING_AGENT_KEY.formatted(bookingId);
        String agentId = redisTemplate.opsForValue().get(bookingKey);
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return getAgentLocation(agentId);
    }

    public Optional<LiveLocationSnapshot> getAgentLocation(String agentId) {
        String agentKey = AGENT_LOCATION_KEY.formatted(agentId);
        String payload = redisTemplate.opsForValue().get(agentKey);
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, LiveLocationSnapshot.class));
        } catch (Exception ex) {
            log.warn("Failed to parse live location for agent {}: {}", agentId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public void handleAvailabilityLocationUpdate(String agentId, double latitude, double longitude, Long timestamp) {
        AppUser agent = userRepository.findUserById(agentId)
                .orElseThrow(() -> new UserNotFoundException("Delivery agent not found"));
        if (!agent.hasRole("DELIVERY_AGENT")) {
            throw new ForbiddenOperationException("User is not a delivery agent");
        }

        long resolvedTimestamp = resolveTimestamp(timestamp);
        LiveLocationSnapshot snapshot = new LiveLocationSnapshot(latitude, longitude, resolvedTimestamp);

        storeAgentLocation(agentId, snapshot);
        updateAgentGeoIndex(agentId, snapshot);
        updateAgentPresence(agentId);

        if (!Boolean.TRUE.equals(agent.getOnline())) {
            agent.setOnline(true);
            userRepository.save(agent);
        }
    }

    @Transactional
    public void markAgentOnline(String agentId) {
        updateAgentPresence(agentId);
        userRepository.findUserById(agentId).ifPresent(user -> {
            user.setOnline(true);
            userRepository.save(user);
        });
    }

    @Transactional
    public void markAgentOffline(String agentId) {
        redisTemplate.delete(AGENT_ONLINE_KEY.formatted(agentId));
        userRepository.findUserById(agentId).ifPresent(user -> {
            user.setOnline(false);
            userRepository.save(user);
        });
    }

    private void storeLiveLocation(String agentId, String bookingId, LiveLocationSnapshot snapshot) {
        try {
            String payload = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(AGENT_LOCATION_KEY.formatted(agentId), payload);
            redisTemplate.opsForValue().set(BOOKING_AGENT_KEY.formatted(bookingId), agentId);
            redisTemplate.opsForValue().set(TRACKING_KEY.formatted(bookingId), payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize live location for agent {}: {}", agentId, ex.getMessage());
        }
    }

    private void storeAgentLocation(String agentId, LiveLocationSnapshot snapshot) {
        try {
            String payload = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(AGENT_LOCATION_KEY.formatted(agentId), payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize availability location for agent {}: {}", agentId, ex.getMessage());
        }
    }

    private void updateAgentGeoIndex(String agentId, LiveLocationSnapshot snapshot) {
        redisTemplate.opsForGeo().add(
                DELIVERY_AGENTS_GEO_KEY,
                new Point(snapshot.lng(), snapshot.lat()),
                agentId
        );
    }

    private void updateAgentPresence(String agentId) {
        redisTemplate.opsForValue().set(AGENT_ONLINE_KEY.formatted(agentId), "1", Duration.ofSeconds(90));
    }

    private void broadcastLiveLocation(String bookingId, LiveLocationSnapshot snapshot) {
        TrackingUpdatePayload payload = TrackingUpdatePayload.builder()
                .lat(snapshot.lat())
                .lng(snapshot.lng())
                .build();
        messagingTemplate.convertAndSend("/topic/tracking/" + bookingId, payload);
    }

    private void persistRouteHistoryIfDue(Booking booking, AppUser agent, LiveLocationSnapshot snapshot) {
        String key = BOOKING_LAST_PERSIST_KEY.formatted(booking.getId());
        String lastPersistRaw = redisTemplate.opsForValue().get(key);
        Long lastPersist = parseLong(lastPersistRaw);

        if (lastPersist != null) {
            long delta = snapshot.updatedAt() - lastPersist;
            if (delta < ROUTE_PERSIST_INTERVAL_SECONDS) {
                return;
            }
        }

        DeliveryRouteHistory history = new DeliveryRouteHistory();
        history.setBooking(booking);
        history.setAgent(agent);
        history.setLatitude(BigDecimal.valueOf(snapshot.lat()));
        history.setLongitude(BigDecimal.valueOf(snapshot.lng()));
        history.setTimestamp(snapshot.updatedAt());
        deliveryRouteHistoryRepository.save(history);

        redisTemplate.opsForValue().set(key, Long.toString(snapshot.updatedAt()));
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long resolveTimestamp(Long provided) {
        if (provided == null || provided <= 0) {
            return Instant.now().getEpochSecond();
        }
        return provided;
    }
}
