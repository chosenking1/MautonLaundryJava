package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryRouteStatus;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import com.work.mautonlaundry.data.repository.AddressRepository;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.LaundrymanAssignmentRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.dtos.requests.deliveryrequests.AcceptDeliveryJobRequest;
import com.work.mautonlaundry.dtos.requests.deliveryrequests.UpdateDeliveryStatusRequest;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.AcceptDeliveryJobResponse;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.DeliveryAssignmentSummaryResponse;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.DeliveryNextStop;
import com.work.mautonlaundry.dtos.responses.deliveryresponse.DeliveryStatusUpdateResponse;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.bookingexceptions.BookingNotFoundException;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.mautonlaundry.services.dispatch.DispatchJob;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {
    private static final String DELIVERY_LOCK_KEY = "delivery:lock:%s";
    private static final String IDEMPOTENCY_KEY = "idempotency:delivery:%s";
    private static final String DELIVERY_JOB_KEY = "delivery:job:%s";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final AddressRepository addressRepository;
    private final BookingStateMachine bookingStateMachine;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final BookingService bookingService;

    @Transactional
    public AcceptDeliveryJobResponse acceptDeliveryJob(AcceptDeliveryJobRequest request) {
        log.info("Accept delivery job requested: bookingId={}, agentId={}, phase={}, idempotencyKey={}",
                request.getBookingId(), request.getAgentId(), request.getPhase(), request.getIdempotencyKey());
        String idempotencyKey = IDEMPOTENCY_KEY.formatted(request.getIdempotencyKey());
        String existing = redisTemplate.opsForValue().get(idempotencyKey);
        if (existing != null) {
            log.info("Idempotent delivery accept ignored: bookingId={}, agentId={}, keyOwner={}",
                    request.getBookingId(), request.getAgentId(), existing);
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
            log.info("Delivery accept rejected: bookingId={} phase={} agentId={} reason=lock_unavailable",
                    booking.getId(), phase, agent.getId());
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
        log.info("Delivery assignment accepted: bookingId={} phase={} agentId={} status={}",
                booking.getId(), phase, agent.getId(), nextStatus);

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

    @Transactional
    public DeliveryStatusUpdateResponse updateDeliveryStatus(String bookingId, UpdateDeliveryStatusRequest request) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        AppUser agent = SecurityUtil.getCurrentUser().orElseThrow();

        if (!agent.hasRole("DELIVERY_AGENT")) {
            throw new ForbiddenOperationException("User is not a delivery agent");
        }

        DeliveryAssignmentPhase phase = parsePhase(request.getPhase());
        DeliveryRouteStatus rawStatus = parseRouteStatus(request.getStatus());
        DeliveryRouteStatus normalizedStatus = normalizeRouteStatus(phase, rawStatus);

        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByBookingAndPhaseAndDeliveryAgent(booking, phase, agent)
                .orElseThrow(() -> new ForbiddenOperationException("No delivery assignment found for this booking"));

        validateRouteProgress(phase, assignment.getStatus(), normalizedStatus);

        DeliveryAssignmentStatus nextAssignmentStatus = mapAssignmentStatus(phase, normalizedStatus);
        boolean assignmentChanged = assignment.getStatus() != nextAssignmentStatus;
        if (assignmentChanged) {
            assignment.setStatus(nextAssignmentStatus);
            assignment.setRespondedAt(LocalDateTime.now());
            deliveryAssignmentRepository.save(assignment);
        }

        BookingStatus targetBookingStatus = mapBookingStatus(phase, normalizedStatus);
        boolean bookingStatusChanged = advanceBookingStatusIfNeeded(booking, targetBookingStatus);

        if (assignmentChanged || bookingStatusChanged) {
            notifyDeliveryUpdates(booking, phase, normalizedStatus, bookingStatusChanged);
        }

        DeliveryNextStop nextStop = resolveNextStop(booking, phase, normalizedStatus);
        BookingStatus bookingStatus = bookingRepository.findById(booking.getId())
                .map(Booking::getStatus)
                .orElse(booking.getStatus());

        return DeliveryStatusUpdateResponse.builder()
                .bookingId(booking.getId())
                .phase(phase.name())
                .routeStatus(normalizedStatus.name())
                .assignmentStatus(nextAssignmentStatus.name())
                .bookingStatus(bookingStatus.name())
                .nextStop(nextStop)
                .build();
    }

    @Transactional(readOnly = true)
    public List<DeliveryAssignmentSummaryResponse> getActiveAssignmentsForAgent(String agentId) {
        AppUser agent = userRepository.findUserById(agentId)
                .orElseThrow(() -> new UserNotFoundException("Delivery agent not found"));
        if (!agent.hasRole("DELIVERY_AGENT")) {
            throw new ForbiddenOperationException("User is not a delivery agent");
        }

        List<DeliveryAssignment> assignments = deliveryAssignmentRepository.findByDeliveryAgentAndStatusIn(
                agent,
                DeliveryAssignmentStatus.inProgressStatuses()
        );

        return assignments.stream()
                .sorted(Comparator.comparing(DeliveryAssignment::getCreatedAt).reversed())
                .map(assignment -> {
                    Booking booking = assignment.getBooking();
                    DeliveryRouteStatus routeStatus = resolveCurrentRouteStatus(assignment);
                    DeliveryNextStop nextStop = resolveNextStop(booking, assignment.getPhase(), routeStatus);
                    return DeliveryAssignmentSummaryResponse.builder()
                            .bookingId(booking.getId())
                            .phase(assignment.getPhase().name())
                            .routeStatus(routeStatus.name())
                            .assignmentStatus(assignment.getStatus().name())
                            .bookingStatus(booking.getStatus().name())
                            .nextStop(nextStop)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private DeliveryAssignmentPhase parsePhase(String rawPhase) {
        String phaseStr = rawPhase == null ? null : rawPhase.trim().toUpperCase();
        if (phaseStr == null || phaseStr.isBlank()) {
            throw new IllegalArgumentException("Invalid phase: " + rawPhase);
        }
        try {
            return DeliveryAssignmentPhase.valueOf(phaseStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid phase: " + rawPhase);
        }
    }

    private DeliveryRouteStatus parseRouteStatus(String rawStatus) {
        String statusStr = rawStatus == null ? null : rawStatus.trim().toUpperCase();
        if (statusStr == null || statusStr.isBlank()) {
            throw new IllegalArgumentException("Invalid delivery status: " + rawStatus);
        }
        try {
            return DeliveryRouteStatus.valueOf(statusStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid delivery status: " + rawStatus + ". Allowed values: " +
                            java.util.Arrays.toString(DeliveryRouteStatus.values())
            );
        }
    }

    private DeliveryRouteStatus normalizeRouteStatus(DeliveryAssignmentPhase phase, DeliveryRouteStatus status) {
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            if (status == DeliveryRouteStatus.ROUTE_TO_CUSTOMER) {
                return DeliveryRouteStatus.ROUTE_TO_PICKUP;
            }
            if (status == DeliveryRouteStatus.ARRIVED_AT_CUSTOMER) {
                return DeliveryRouteStatus.ARRIVED_AT_PICKUP;
            }
            return status;
        }
        if (status == DeliveryRouteStatus.ROUTE_TO_LAUNDRY) {
            return DeliveryRouteStatus.ROUTE_TO_PICKUP;
        }
        if (status == DeliveryRouteStatus.ARRIVED_AT_LAUNDRY) {
            return DeliveryRouteStatus.ARRIVED_AT_PICKUP;
        }
        return status;
    }

    private DeliveryRouteStatus resolveCurrentRouteStatus(DeliveryAssignment assignment) {
        DeliveryRouteStatus routeStatus = mapRouteStatus(assignment.getPhase(), assignment.getStatus());
        if (routeStatus != null) {
            return routeStatus;
        }
        return DeliveryRouteStatus.ROUTE_TO_PICKUP;
    }

    private void validateRouteProgress(DeliveryAssignmentPhase phase,
                                       DeliveryAssignmentStatus currentStatus,
                                       DeliveryRouteStatus requestedStatus) {
        if (currentStatus == DeliveryAssignmentStatus.CANCELLED
                || currentStatus == DeliveryAssignmentStatus.DECLINED
                || currentStatus == DeliveryAssignmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Delivery assignment is not active");
        }

        List<DeliveryRouteStatus> sequence = routeSequence(phase);
        int requestedIndex = sequence.indexOf(requestedStatus);
        if (requestedIndex < 0) {
            throw new IllegalArgumentException("Status not valid for this phase: " + requestedStatus);
        }

        DeliveryRouteStatus currentRoute = mapRouteStatus(phase, currentStatus);
        int currentIndex = currentRoute == null ? -1 : sequence.indexOf(currentRoute);
        if (currentIndex > requestedIndex) {
            throw new IllegalArgumentException("Cannot move delivery status backwards");
        }
        if (requestedIndex > currentIndex + 1) {
            throw new IllegalArgumentException("Cannot skip delivery status steps");
        }
    }

    private List<DeliveryRouteStatus> routeSequence(DeliveryAssignmentPhase phase) {
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            return List.of(
                    DeliveryRouteStatus.ROUTE_TO_PICKUP,
                    DeliveryRouteStatus.ARRIVED_AT_PICKUP,
                    DeliveryRouteStatus.PICKED_UP,
                    DeliveryRouteStatus.ROUTE_TO_LAUNDRY,
                    DeliveryRouteStatus.ARRIVED_AT_LAUNDRY
            );
        }
        return List.of(
                DeliveryRouteStatus.ROUTE_TO_PICKUP,
                DeliveryRouteStatus.ARRIVED_AT_PICKUP,
                DeliveryRouteStatus.PICKED_UP,
                DeliveryRouteStatus.ROUTE_TO_CUSTOMER,
                DeliveryRouteStatus.ARRIVED_AT_CUSTOMER,
                DeliveryRouteStatus.DELIVERED_TO_CUSTOMER
        );
    }

    private DeliveryRouteStatus mapRouteStatus(DeliveryAssignmentPhase phase, DeliveryAssignmentStatus status) {
        if (status == null) {
            return null;
        }
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            return switch (status) {
                case ENROUTE_TO_CUSTOMER -> DeliveryRouteStatus.ROUTE_TO_PICKUP;
                case ARRIVED_AT_CUSTOMER -> DeliveryRouteStatus.ARRIVED_AT_PICKUP;
                case PICKED_UP_FROM_CUSTOMER -> DeliveryRouteStatus.PICKED_UP;
                case ENROUTE_TO_LAUNDRY -> DeliveryRouteStatus.ROUTE_TO_LAUNDRY;
                case ARRIVED_AT_LAUNDRY, DELIVERED_TO_LAUNDRY -> DeliveryRouteStatus.ARRIVED_AT_LAUNDRY;
                default -> null;
            };
        }
        return switch (status) {
            case ENROUTE_TO_LAUNDRY -> DeliveryRouteStatus.ROUTE_TO_PICKUP;
            case ARRIVED_AT_LAUNDRY -> DeliveryRouteStatus.ARRIVED_AT_PICKUP;
            case PICKED_UP_FROM_LAUNDRY -> DeliveryRouteStatus.PICKED_UP;
            case ENROUTE_FROM_LAUNDRY_TO_CUSTOMER -> DeliveryRouteStatus.ROUTE_TO_CUSTOMER;
            case ARRIVED_AT_CUSTOMER_FOR_DELIVERY -> DeliveryRouteStatus.ARRIVED_AT_CUSTOMER;
            case DELIVERED_TO_CUSTOMER -> DeliveryRouteStatus.DELIVERED_TO_CUSTOMER;
            default -> null;
        };
    }

    private DeliveryAssignmentStatus mapAssignmentStatus(DeliveryAssignmentPhase phase, DeliveryRouteStatus status) {
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            return switch (status) {
                case ROUTE_TO_PICKUP -> DeliveryAssignmentStatus.ENROUTE_TO_CUSTOMER;
                case ARRIVED_AT_PICKUP -> DeliveryAssignmentStatus.ARRIVED_AT_CUSTOMER;
                case PICKED_UP -> DeliveryAssignmentStatus.PICKED_UP_FROM_CUSTOMER;
                case ROUTE_TO_LAUNDRY -> DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY;
                case ARRIVED_AT_LAUNDRY -> DeliveryAssignmentStatus.DELIVERED_TO_LAUNDRY;
                default -> throw new IllegalArgumentException("Status not valid for pickup phase: " + status);
            };
        }
        return switch (status) {
            case ROUTE_TO_PICKUP -> DeliveryAssignmentStatus.ENROUTE_TO_LAUNDRY;
            case ARRIVED_AT_PICKUP -> DeliveryAssignmentStatus.ARRIVED_AT_LAUNDRY;
            case PICKED_UP -> DeliveryAssignmentStatus.PICKED_UP_FROM_LAUNDRY;
            case ROUTE_TO_CUSTOMER -> DeliveryAssignmentStatus.ENROUTE_FROM_LAUNDRY_TO_CUSTOMER;
            case ARRIVED_AT_CUSTOMER -> DeliveryAssignmentStatus.ARRIVED_AT_CUSTOMER_FOR_DELIVERY;
            case DELIVERED_TO_CUSTOMER -> DeliveryAssignmentStatus.DELIVERED_TO_CUSTOMER;
            default -> throw new IllegalArgumentException("Status not valid for return phase: " + status);
        };
    }

    private BookingStatus mapBookingStatus(DeliveryAssignmentPhase phase, DeliveryRouteStatus status) {
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            return switch (status) {
                case PICKED_UP -> BookingStatus.PICKED_UP;
                case ARRIVED_AT_LAUNDRY -> BookingStatus.AT_LAUNDRY;
                default -> null;
            };
        }
        return switch (status) {
            case PICKED_UP, ROUTE_TO_CUSTOMER, ARRIVED_AT_CUSTOMER -> BookingStatus.OUT_FOR_DELIVERY;
            case DELIVERED_TO_CUSTOMER -> BookingStatus.DELIVERED;
            default -> null;
        };
    }

    private boolean advanceBookingStatusIfNeeded(Booking booking, BookingStatus target) {
        if (target == null) {
            return false;
        }
        BookingStatus current = booking.getStatus();
        if (current == target) {
            return false;
        }

        if (target == BookingStatus.AT_LAUNDRY && current == BookingStatus.PICKUP_AGENT_ASSIGNED) {
            bookingService.updateBookingStatus(booking.getId(), BookingStatus.PICKED_UP);
            current = BookingStatus.PICKED_UP;
        }

        if (target == BookingStatus.DELIVERED && current == BookingStatus.DELIVERY_AGENT_ASSIGNED) {
            bookingService.updateBookingStatus(booking.getId(), BookingStatus.OUT_FOR_DELIVERY);
            current = BookingStatus.OUT_FOR_DELIVERY;
        }

        if (current != target) {
            bookingService.updateBookingStatus(booking.getId(), target);
            return true;
        }
        return false;
    }

    private void notifyDeliveryUpdates(Booking booking,
                                       DeliveryAssignmentPhase phase,
                                       DeliveryRouteStatus status,
                                       boolean bookingStatusChanged) {
        String customerEmail = booking.getUser().getEmail();
        Optional<AppUser> laundryAgent = findLaundryAgent(booking);

        if (!bookingStatusChanged && customerEmail != null && !customerEmail.isBlank()) {
            String message = customerMessage(phase, status);
            if (message != null) {
                notificationService.notifyDeliveryProgress(customerEmail, booking.getId(), message);
            }
        }

        String laundryMessage = laundryMessage(phase, status);
        if (laundryMessage != null && laundryAgent.isPresent()) {
            notificationService.notifyLaundryProgress(laundryAgent.get().getEmail(), booking.getId(), laundryMessage);
        }
    }

    private String customerMessage(DeliveryAssignmentPhase phase, DeliveryRouteStatus status) {
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            return switch (status) {
                case ROUTE_TO_PICKUP -> "Driver is on the way to pick up your laundry";
                case ARRIVED_AT_PICKUP -> "Driver has arrived for pickup";
                case ROUTE_TO_LAUNDRY -> "Driver is on the way to the laundry";
                case ARRIVED_AT_LAUNDRY -> "Laundry has arrived at the laundry";
                default -> null;
            };
        }
        return switch (status) {
            case ROUTE_TO_PICKUP -> "Driver is on the way to pick up your laundry from the laundry";
            case ARRIVED_AT_PICKUP -> "Driver has arrived at the laundry to pick up your items";
            case ARRIVED_AT_CUSTOMER -> "Driver has arrived at your location";
            default -> null;
        };
    }

    private String laundryMessage(DeliveryAssignmentPhase phase, DeliveryRouteStatus status) {
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            return switch (status) {
                case ROUTE_TO_LAUNDRY -> "Driver is on the way to your laundry location";
                case ARRIVED_AT_LAUNDRY -> "Driver has arrived with customer items";
                default -> null;
            };
        }
        return switch (status) {
            case ROUTE_TO_PICKUP -> "Driver is on the way to pick up cleaned items";
            case ARRIVED_AT_PICKUP -> "Driver has arrived to pick up cleaned items";
            case PICKED_UP -> "Driver has picked up cleaned items for delivery";
            default -> null;
        };
    }

    private Optional<AppUser> findLaundryAgent(Booking booking) {
        Optional<LaundrymanAssignment> accepted = laundrymanAssignmentRepository
                .findFirstByBookingAndStatusInOrderByCreatedAtDesc(
                        booking,
                        List.of(LaundrymanAssignmentStatus.ACCEPTED, LaundrymanAssignmentStatus.AUTO_ACCEPTED)
                );
        return accepted.map(LaundrymanAssignment::getLaundryman);
    }

    private DeliveryNextStop resolveNextStop(Booking booking,
                                             DeliveryAssignmentPhase phase,
                                             DeliveryRouteStatus status) {
        Address pickup = booking.getPickupAddress();
        Address laundry = resolveLaundryAddress(booking);

        Address next = null;
        String label = null;
        if (phase == DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER) {
            if (status == DeliveryRouteStatus.ROUTE_TO_PICKUP || status == DeliveryRouteStatus.ARRIVED_AT_PICKUP) {
                next = pickup;
                label = "customer";
            } else if (status == DeliveryRouteStatus.PICKED_UP || status == DeliveryRouteStatus.ROUTE_TO_LAUNDRY) {
                next = laundry;
                label = "laundry";
            }
        } else {
            if (status == DeliveryRouteStatus.ROUTE_TO_PICKUP || status == DeliveryRouteStatus.ARRIVED_AT_PICKUP) {
                next = laundry;
                label = "laundry";
            } else if (status == DeliveryRouteStatus.PICKED_UP || status == DeliveryRouteStatus.ROUTE_TO_CUSTOMER) {
                next = pickup;
                label = "customer";
            }
        }

        if (next == null || next.getLatitude() == null || next.getLongitude() == null) {
            return null;
        }

        return DeliveryNextStop.builder()
                .label(label)
                .lat(next.getLatitude())
                .lng(next.getLongitude())
                .addressLine(formatAddressLine(next))
                .build();
    }

    private Address resolveLaundryAddress(Booking booking) {
        Optional<AppUser> laundryAgent = findLaundryAgent(booking);
        if (laundryAgent.isEmpty()) {
            return booking.getPickupAddress();
        }
        return addressRepository.findByUserAndDeletedFalse(laundryAgent.get()).stream()
                .filter(addr -> addr.getLatitude() != null && addr.getLongitude() != null)
                .findFirst()
                .orElse(booking.getPickupAddress());
    }

    private String formatAddressLine(Address address) {
        if (address == null) {
            return null;
        }
        String line1 = address.getStreet() == null ? "" : address.getStreet();
        String number = address.getStreet_number() == null ? "" : address.getStreet_number().toString();
        String city = address.getCity() == null ? "" : address.getCity();
        String combined = (line1 + " " + number).trim();
        if (!city.isBlank()) {
            return (combined + ", " + city).trim();
        }
        return combined.isBlank() ? null : combined;
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
