package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lets go of bookings that were waiting for their pickup window.
 *
 * <p>A scheduled booking is created and paid for like any other, then simply not
 * offered to laundry partners. Nothing else in the system knows it is waiting --
 * dispatch, the reconciler and the assignment sweeps all begin at the laundry
 * offer -- so this is the only thing standing between a held booking and the
 * normal flow. If it stops running, scheduled bookings silently never happen,
 * which is why a failure to release even one of them is logged at error rather
 * than swallowed with the batch.
 *
 * <p>Releasing is idempotent through {@code pickupReleasedAt}: the sweep can run
 * twice, overlap itself, or restart mid-batch without a booking being offered
 * to two laundry partners.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledPickupReleaser {

    private final BookingRepository bookingRepository;
    private final LaundryAssignmentService laundryAssignmentService;
    private final PickupSchedulingService pickupSchedulingService;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.pickup.release-sweep-ms:120000}")
    @Transactional
    public void releaseDueBookings() {
        List<Booking> held = bookingRepository.findAwaitingScheduledPickup();
        if (held.isEmpty()) {
            return;
        }

        for (Booking booking : held) {
            try {
                releaseIfDue(booking);
            } catch (Exception e) {
                // One booking with a bad address must not strand the rest of the
                // morning's pickups behind it.
                log.error("Could not release scheduled booking {} for its {} window: {}",
                        booking.getId(),
                        booking.getPickupSlot() == null ? "?" : booking.getPickupSlot().getLabel(),
                        e.getMessage(), e);
            }
        }
    }

    private void releaseIfDue(Booking booking) {
        if (booking.getPickupSlot() == null || booking.getScheduledPickupDate() == null) {
            // Half a schedule cannot be timed, and leaving it held means it never
            // happens at all. Release it rather than lose it.
            log.warn("Booking {} is held with an incomplete schedule -- releasing now", booking.getId());
        } else if (!pickupSchedulingService.isDueForRelease(
                booking.getScheduledPickupDate(), booking.getPickupSlot())) {
            return;
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            // Cancelled while it waited. Mark it released so it stops being
            // scanned, but do not offer it to anyone.
            booking.setPickupReleasedAt(LocalDateTime.now());
            bookingRepository.save(booking);
            return;
        }

        booking.setPickupReleasedAt(LocalDateTime.now());
        bookingRepository.save(booking);
        laundryAssignmentService.createLaundryOffers(booking);

        // Only for scheduled bookings. createLaundryOffers notifies the laundry
        // agent, not the customer -- fine when the customer just pressed Book,
        // but this one chose a window days ago and has heard nothing since.
        // This is the message that says the promise is being kept.
        if (booking.getUser() != null && booking.getUser().getEmail() != null) {
            notificationService.notifyUserBookingStatusChange(
                    booking.getUser().getEmail(), booking.getId(),
                    BookingStatus.CREATED.name(), booking.getStatus().name());
        }

        log.info("Released booking {} for its {} pickup window on {}",
                booking.getId(),
                booking.getPickupSlot() == null ? "?" : booking.getPickupSlot().getLabel(),
                booking.getScheduledPickupDate());
    }
}
