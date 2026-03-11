package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.deliveryrequests.AcceptDeliveryJobRequest;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.AcceptDeliveryJobResponse;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.services.dispatch.DispatchJob;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private static final String DELIVERY_LOCK_KEY = "delivery:lock:%s";
    private static final String IDEMPOTENCY_KEY = "idempotency:delivery:%s";
    private static final String DELIVERY_JOB_KEY = "delivery:job:%s";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final BookingStateMachine bookingStateMachine;
    private final NotificationService notificationService;
    private final MetricsService metricsService;

    @Transactional
    public AcceptDeliveryJobResponse acceptDeliveryJob(AcceptDeliveryJobRequest request) {
        String idempotencyKey = IDEMPOTENCY_KEY.formatted(request.getIdempotencyKey());
        String existing = redisTemplate.opsForValue().get(idempotencyKey);
        if (existing != null) {
            return AcceptDeliveryJobResponse.builder()
                    .accepted(true)
                    .idempotent(true)
                    .message("Request already processed")
                    .build();
        }

        Booking booking = bookingRepository.findByIdAndDeletedFalse(request.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser agent = userRepository.findUserById(request.getAgentId())
                .orElseThrow(() -> new UserNotFoundException("Delivery agent not found"));

        if (!agent.hasRole("DELIVERY_AGENT")) {
            throw new ForbiddenOperationException("User is not a delivery agent");
        }

        DeliveryAssignmentPhase phase = DeliveryAssignmentPhase.valueOf(request.getPhase().trim().toUpperCase());
        String lockKey = DELIVERY_LOCK_KEY.formatted(booking.getId());
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, agent.getId(), Duration.ofSeconds(30));
        if (locked == null || !locked) {
            return AcceptDeliveryJobResponse.builder()
                    .accepted(false)
                    .idempotent(false)
                    .message("Job already assigned")
                    .build();
        }

        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByBookingAndPhaseAndDeliveryAgent(booking, phase, agent)
                .orElseGet(DeliveryAssignment::new);
        assignment.setBooking(booking);
        assignment.setDeliveryAgent(agent);
        assignment.setPhase(phase);
        assignment.setStatus(DeliveryAssignmentStatus.ACCEPTED);
        deliveryAssignmentRepository.save(assignment);

        BookingStatus oldStatus = booking.getStatus();
        BookingStatus nextStatus = phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER
                ? BookingStatus.PICKUP_AGENT_ASSIGNED
                : BookingStatus.DELIVERY_AGENT_ASSIGNED;
        bookingStateMachine.validateTransition(booking.getStatus(), nextStatus);
        booking.setStatus(nextStatus);
        bookingRepository.save(booking);

        notificationService.notifyUserBookingStatusChange(
                booking.getUser().getEmail(),
                booking.getId(),
                oldStatus.name(),
                nextStatus.name()
        );
        notificationService.notifyDriverAssigned(booking.getUser().getEmail(), booking.getId());

        redisTemplate.opsForValue().set(idempotencyKey, agent.getId(), Duration.ofHours(1));
        metricsService.incrementJobsAccepted();
        recordDispatchLatency(booking.getId());
        return AcceptDeliveryJobResponse.builder()
                .accepted(true)
                .idempotent(false)
                .message("Job accepted")
                .build();
    }

    private void recordDispatchLatency(String bookingId) {
        String payload = redisTemplate.opsForValue().get(DELIVERY_JOB_KEY.formatted(bookingId));
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            DispatchJob job = objectMapper.readValue(payload, DispatchJob.class);
            long now = java.time.Instant.now().getEpochSecond();
            metricsService.recordDispatchLatency(Duration.ofSeconds(now - job.createdAt()));
        } catch (Exception ignored) {
        }
    }
}
