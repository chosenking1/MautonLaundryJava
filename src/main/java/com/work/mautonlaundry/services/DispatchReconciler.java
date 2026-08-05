package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Re-offers bookings that are waiting for a rider but have nothing left to
 * offer them.
 *
 * <p>A dispatch job lives in a Redis list with a TTL. If it drains without
 * finding an online agent -- nobody on shift, everyone out of radius, an app
 * closed at the wrong moment -- the booking stays at PICKUP_DISPATCH_PENDING
 * with no job behind it and nothing to retry. It waits forever, silently, and
 * the first anyone knows is a customer asking where their rider is. That is
 * exactly what happened to a booking assigned at 09:39 whose job had expired by
 * the time a rider came online an hour later.
 *
 * <p>This sweep closes the gap: any booking that has been waiting longer than
 * the grace period, and still has no active assignment, gets its job put back on
 * the queue. Dispatch itself is unchanged -- this only ensures there is always
 * something for it to work on.
 *
 * <p>Re-enqueueing is safe to repeat. DispatchEngine skips a job whose booking
 * already has an active assignment, so a rider who accepts between sweeps is not
 * disturbed, and a booking that genuinely cannot be filled is simply offered
 * again next time rather than being lost.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchReconciler {

    private final BookingRepository bookingRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DispatchEngine dispatchEngine;

    /**
     * How long a booking may sit unassigned before it is re-offered. Long enough
     * that a live dispatch attempt is not duplicated, short enough that a
     * customer is not left wondering.
     */
    @Value("${app.dispatch.reconcile-after-minutes:5}")
    private long reconcileAfterMinutes;

    @Scheduled(fixedDelayString = "${app.dispatch.reconcile-delay-ms:120000}")
    @Transactional
    public void reOfferStrandedBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMinutes(reconcileAfterMinutes));

        reOffer(BookingStatus.PICKUP_DISPATCH_PENDING,
                DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER, cutoff);
        reOffer(BookingStatus.DELIVERY_DISPATCH_PENDING,
                DeliveryAssignmentPhase.RETURN_TO_CUSTOMER, cutoff);
    }

    private void reOffer(BookingStatus status, DeliveryAssignmentPhase phase, LocalDateTime cutoff) {
        List<Booking> waiting = bookingRepository.findByStatusAndDeletedFalse(status);
        for (Booking booking : waiting) {
            if (booking.getUpdatedAt() != null && booking.getUpdatedAt().isAfter(cutoff)) {
                // Still inside the grace period; a live attempt may be in flight.
                continue;
            }
            if (hasActiveAssignment(booking, phase)) {
                continue;
            }
            log.warn("Booking {} has waited in {} with no rider assigned -- re-offering {}",
                    booking.getId(), status, phase);
            dispatchEngine.enqueueDispatchJob(booking, phase);
        }
    }

    private boolean hasActiveAssignment(Booking booking, DeliveryAssignmentPhase phase) {
        return deliveryAssignmentRepository.existsByBooking_IdAndPhaseAndStatusIn(
                booking.getId(), phase, DeliveryAssignmentStatus.activeAssignmentStatuses());
    }
}
