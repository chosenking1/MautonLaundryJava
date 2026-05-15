package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.BookingStatusHistory;
import com.work.mautonlaundry.data.model.DeliveryAssignment;
import com.work.mautonlaundry.data.model.HandoffCode;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.model.enums.HandoffCodeStatus;
import com.work.mautonlaundry.data.model.enums.HandoffStage;
import com.work.mautonlaundry.data.model.enums.StatusChangeTrigger;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.BookingStatusHistoryRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import com.work.mautonlaundry.data.repository.HandoffCodeRepository;
import com.work.mautonlaundry.exceptions.ConflictException;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * One-time code lifecycle for booking handoffs. Codes gate the four physical
 * handoffs in the delivery flow; a successful redemption is what advances the
 * booking through PICKED_UP / AT_LAUNDRY / OUT_FOR_DELIVERY / DELIVERED.
 *
 * Design notes:
 *  - Generic "invalid code" surface for every bad-code path so the API can't be
 *    used to probe which bookings exist or what stage they're in.
 *  - Pessimistic lock on the candidate row prevents two riders racing to redeem
 *    the same code (one wins, the other gets the generic invalid response).
 *  - The booking-status state machine is the final guard — if anything bypasses
 *    the redeem flow and pokes the booking into an unexpected state, the prior-
 *    status check rejects the redemption and we don't half-update.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HandoffCodeService {

    private static final Duration CODE_TTL = Duration.ofHours(48);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC_INVALID = "Invalid or expired code";

    private final HandoffCodeRepository handoffCodeRepository;
    private final BookingStatusHistoryRepository statusHistoryRepository;
    private final BookingRepository bookingRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final BookingStateMachine bookingStateMachine;
    private final AuditService auditService;

    @Transactional
    public HandoffCode issueCode(Booking booking, HandoffStage stage) {
        handoffCodeRepository.findByBookingAndStageAndStatus(booking, stage, HandoffCodeStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(HandoffCodeStatus.INVALIDATED);
                    handoffCodeRepository.save(existing);
                });

        HandoffCode code = new HandoffCode();
        code.setBooking(booking);
        code.setStage(stage);
        code.setCode(generateNumericCode());
        code.setStatus(HandoffCodeStatus.ACTIVE);
        code.setIssuedAt(Instant.now());
        code.setExpiresAt(Instant.now().plus(CODE_TTL));
        HandoffCode saved = handoffCodeRepository.save(code);
        log.info("Handoff code issued: bookingId={} stage={} expiresAt={}",
                booking.getId(), stage, saved.getExpiresAt());
        return saved;
    }

    @Transactional
    public RedemptionResult redeem(String bookingId, String submittedCode, AppUser redeemer) {
        log.info("Handoff redeem attempt: bookingId={} redeemer={} submittedLen={}",
                bookingId, redeemer == null ? "null" : redeemer.getEmail(),
                submittedCode == null ? 0 : submittedCode.trim().length());

        if (bookingId == null || bookingId.isBlank() || submittedCode == null || submittedCode.isBlank()) {
            log.warn("Handoff redeem rejected: blank bookingId or code");
            throw new ConflictException(GENERIC_INVALID);
        }

        HandoffCode code = handoffCodeRepository
                .findActiveForRedemption(bookingId, submittedCode.trim())
                .orElseThrow(() -> {
                    log.warn("Handoff redeem rejected: no ACTIVE code matching submitted value for bookingId={}",
                            bookingId);
                    return new ConflictException(GENERIC_INVALID);
                });

        if (Instant.now().isAfter(code.getExpiresAt())) {
            log.warn("Handoff redeem rejected: code expired bookingId={} stage={} expiresAt={}",
                    bookingId, code.getStage(), code.getExpiresAt());
            code.setStatus(HandoffCodeStatus.EXPIRED);
            handoffCodeRepository.save(code);
            throw new ConflictException(GENERIC_INVALID);
        }

        if (code.getAttempts() >= MAX_ATTEMPTS) {
            log.warn("Handoff redeem rejected: max attempts exceeded bookingId={} stage={} attempts={}",
                    bookingId, code.getStage(), code.getAttempts());
            code.setStatus(HandoffCodeStatus.INVALIDATED);
            handoffCodeRepository.save(code);
            throw new ConflictException(GENERIC_INVALID);
        }

        Booking booking = code.getBooking();
        DeliveryAssignmentPhase expectedPhase = phaseFor(code.getStage());

        Optional<DeliveryAssignment> assignment = deliveryAssignmentRepository
                .findByBookingAndPhaseAndDeliveryAgent(booking, expectedPhase, redeemer);
        if (assignment.isEmpty()) {
            log.warn("Handoff redeem rejected: rider {} has no assignment in phase {} for bookingId={} stage={} "
                            + "(rider is trying a code for the wrong leg, or assignment record is missing)",
                    redeemer.getEmail(), expectedPhase, bookingId, code.getStage());
            code.setAttempts(code.getAttempts() + 1);
            handoffCodeRepository.save(code);
            throw new ForbiddenOperationException(GENERIC_INVALID);
        }

        BookingStatus expectedFromStatus = expectedPriorBookingStatus(code.getStage());
        if (booking.getStatus() != expectedFromStatus) {
            log.warn("Handoff redeem rejected: bookingId={} stage={} expected prior status {} but was {}",
                    booking.getId(), code.getStage(), expectedFromStatus, booking.getStatus());
            throw new ConflictException("Booking is not at the right stage to redeem this code");
        }

        BookingStatus targetStatus = targetBookingStatus(code.getStage());
        bookingStateMachine.validateTransition(booking.getStatus(), targetStatus);

        BookingStatus from = booking.getStatus();
        booking.setStatus(targetStatus);
        bookingRepository.save(booking);

        // Mirror the booking transition onto the DeliveryAssignment so the rider's
        // active-jobs query reflects reality. Without this the assignment stays at
        // its pre-code status (e.g. ARRIVED_AT_CUSTOMER), the rider's UI keeps
        // offering "Next Step", and the legacy updateDeliveryStatus path is the
        // only thing that ever advances assignment.status — but redeem replaces
        // those calls now. Terminal stages (DELIVERED_TO_LAUNDRY for pickup phase,
        // DELIVERED_TO_CUSTOMER for return phase) immediately collapse to
        // COMPLETED, mirroring DeliveryService.updateDeliveryStatus.
        DeliveryAssignment redeemAssignment = assignment.get();
        DeliveryAssignmentStatus nextAssignmentStatus = assignmentStatusFor(code.getStage());
        boolean isTerminalLeg = code.getStage() == HandoffStage.RIDER_TO_LAUNDRY
                || code.getStage() == HandoffStage.RIDER_TO_CUSTOMER_DELIVERY;
        if (redeemAssignment.getStatus() != nextAssignmentStatus) {
            redeemAssignment.setStatus(nextAssignmentStatus);
            redeemAssignment.setRespondedAt(LocalDateTime.now());
            deliveryAssignmentRepository.save(redeemAssignment);
        }
        if (isTerminalLeg && redeemAssignment.getStatus() != DeliveryAssignmentStatus.COMPLETED) {
            redeemAssignment.setStatus(DeliveryAssignmentStatus.COMPLETED);
            redeemAssignment.setRespondedAt(LocalDateTime.now());
            deliveryAssignmentRepository.save(redeemAssignment);
        }

        code.setStatus(HandoffCodeStatus.REDEEMED);
        code.setRedeemedAt(Instant.now());
        code.setRedeemedBy(redeemer);
        handoffCodeRepository.save(code);

        BookingStatusHistory history = new BookingStatusHistory();
        history.setBooking(booking);
        history.setFromStatus(from);
        history.setToStatus(targetStatus);
        history.setTriggerType(StatusChangeTrigger.CODE_REDEMPTION);
        history.setTriggerCode(code);
        history.setActor(redeemer);
        history.setChangedAt(Instant.now());
        statusHistoryRepository.save(history);

        auditService.logAction(
                "HANDOFF_REDEEM",
                "BOOKING",
                booking.getId(),
                String.format("stage=%s rider=%s %s -> %s",
                        code.getStage(), redeemer.getEmail(), from, targetStatus));

        HandoffStage nextStage = nextStageAfter(code.getStage());
        HandoffCode nextCode = nextStage != null ? issueCode(booking, nextStage) : null;

        // The final stage is RIDER_TO_CUSTOMER_DELIVERY → DELIVERED. The
        // booking journey is over at that point, so chain DELIVERED → COMPLETED
        // automatically. This puts the booking in the laundry agent's "done"
        // tab without needing a separate manual action.
        BookingStatus finalStatus = targetStatus;
        if (code.getStage() == HandoffStage.RIDER_TO_CUSTOMER_DELIVERY
                && booking.getStatus() == BookingStatus.DELIVERED) {
            bookingStateMachine.validateTransition(BookingStatus.DELIVERED, BookingStatus.COMPLETED);
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);

            BookingStatusHistory completion = new BookingStatusHistory();
            completion.setBooking(booking);
            completion.setFromStatus(BookingStatus.DELIVERED);
            completion.setToStatus(BookingStatus.COMPLETED);
            completion.setTriggerType(StatusChangeTrigger.SYSTEM);
            completion.setActor(redeemer);
            completion.setChangedAt(Instant.now());
            statusHistoryRepository.save(completion);
            finalStatus = BookingStatus.COMPLETED;
        }

        log.info("Handoff redeemed: bookingId={} stage={} from={} to={}",
                booking.getId(), code.getStage(), from, finalStatus);

        return new RedemptionResult(booking, code, nextCode, from, finalStatus);
    }

    /**
     * Admin recovery path: invalidate any active code for (booking, stage) and
     * issue a new one. Caller is responsible for permission checks.
     */
    @Transactional
    public HandoffCode regenerate(Booking booking, HandoffStage stage) {
        HandoffCode reissued = issueCode(booking, stage);
        auditService.logAction(
                "HANDOFF_REGENERATE",
                "BOOKING",
                booking.getId(),
                String.format("stage=%s", stage));
        return reissued;
    }

    private DeliveryAssignmentPhase phaseFor(HandoffStage stage) {
        return switch (stage) {
            case CUSTOMER_TO_RIDER_PICKUP, RIDER_TO_LAUNDRY -> DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER;
            case LAUNDRY_TO_RIDER_RETURN, RIDER_TO_CUSTOMER_DELIVERY -> DeliveryAssignmentPhase.RETURN_TO_CUSTOMER;
        };
    }

    private BookingStatus expectedPriorBookingStatus(HandoffStage stage) {
        return switch (stage) {
            case CUSTOMER_TO_RIDER_PICKUP -> BookingStatus.PICKUP_AGENT_ASSIGNED;
            case RIDER_TO_LAUNDRY -> BookingStatus.PICKED_UP;
            case LAUNDRY_TO_RIDER_RETURN -> BookingStatus.DELIVERY_AGENT_ASSIGNED;
            case RIDER_TO_CUSTOMER_DELIVERY -> BookingStatus.OUT_FOR_DELIVERY;
        };
    }

    private BookingStatus targetBookingStatus(HandoffStage stage) {
        return switch (stage) {
            case CUSTOMER_TO_RIDER_PICKUP -> BookingStatus.PICKED_UP;
            case RIDER_TO_LAUNDRY -> BookingStatus.AT_LAUNDRY;
            case LAUNDRY_TO_RIDER_RETURN -> BookingStatus.OUT_FOR_DELIVERY;
            case RIDER_TO_CUSTOMER_DELIVERY -> BookingStatus.DELIVERED;
        };
    }

    private DeliveryAssignmentStatus assignmentStatusFor(HandoffStage stage) {
        return switch (stage) {
            case CUSTOMER_TO_RIDER_PICKUP -> DeliveryAssignmentStatus.PICKED_UP_FROM_CUSTOMER;
            case RIDER_TO_LAUNDRY -> DeliveryAssignmentStatus.DELIVERED_TO_LAUNDRY;
            case LAUNDRY_TO_RIDER_RETURN -> DeliveryAssignmentStatus.PICKED_UP_FROM_LAUNDRY;
            case RIDER_TO_CUSTOMER_DELIVERY -> DeliveryAssignmentStatus.DELIVERED_TO_CUSTOMER;
        };
    }

    private HandoffStage nextStageAfter(HandoffStage stage) {
        return switch (stage) {
            case CUSTOMER_TO_RIDER_PICKUP -> HandoffStage.RIDER_TO_LAUNDRY;
            case LAUNDRY_TO_RIDER_RETURN -> HandoffStage.RIDER_TO_CUSTOMER_DELIVERY;
            default -> null;
        };
    }

    private String generateNumericCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public record RedemptionResult(
            Booking booking,
            HandoffCode redeemedCode,
            HandoffCode nextStageCode,
            BookingStatus fromStatus,
            BookingStatus toStatus
    ) {}
}
