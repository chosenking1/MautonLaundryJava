package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.PickupSlot;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sweep that lets held bookings go.
 *
 * <p>This is the only thing between a scheduled booking and the rest of the
 * system -- dispatch, the reconciler and the assignment sweeps all begin at the
 * laundry offer, which a held booking has not had. If it silently does nothing,
 * the booking never happens and the customer finds out by waiting in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduledPickupReleaserTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private LaundryAssignmentService laundryAssignmentService;
    @Mock private NotificationService notificationService;
    @Mock private PickupSchedulingService pickupSchedulingService;

    private ScheduledPickupReleaser releaser;
    private PickupSlot morning;

    @BeforeEach
    void setUp() {
        releaser = new ScheduledPickupReleaser(bookingRepository, laundryAssignmentService,
                pickupSchedulingService, notificationService);
        morning = new PickupSlot();
        morning.setId("m");
        morning.setLabel("Morning");
        morning.setStartTime(LocalTime.of(8, 0));
        morning.setEndTime(LocalTime.of(12, 0));
    }

    private Booking held(String id, BookingStatus status) {
        AppUser user = new AppUser();
        user.setEmail("ada@example.com");
        Booking b = new Booking();
        b.setId(id);
        b.setUser(user);
        b.setStatus(status);
        b.setScheduledPickupDate(LocalDate.of(2026, 8, 6));
        b.setPickupSlot(morning);
        return b;
    }

    private void due(boolean isDue) {
        when(pickupSchedulingService.isDueForRelease(any(), any())).thenReturn(isDue);
    }

    @Test
    void aDueBookingIsOfferedToLaundryPartners() {
        Booking booking = held("b1", BookingStatus.CREATED);
        when(bookingRepository.findAwaitingScheduledPickup()).thenReturn(List.of(booking));
        due(true);

        releaser.releaseDueBookings();

        verify(laundryAssignmentService).createLaundryOffers(booking);
        assertThat(booking.getPickupReleasedAt()).isNotNull();
    }

    @Test
    void aBookingWhoseWindowIsStillAwayIsLeftAlone() {
        Booking booking = held("b2", BookingStatus.CREATED);
        when(bookingRepository.findAwaitingScheduledPickup()).thenReturn(List.of(booking));
        due(false);

        releaser.releaseDueBookings();

        verify(laundryAssignmentService, never()).createLaundryOffers(any());
        assertThat(booking.getPickupReleasedAt()).isNull();
    }

    @Test
    void releasingIsIdempotentThroughTheTimestamp() {
        // The query is what enforces this, so pin that a released booking is no
        // longer returned by it -- otherwise a restart mid-sweep double-offers.
        Booking booking = held("b3", BookingStatus.CREATED);
        when(bookingRepository.findAwaitingScheduledPickup()).thenReturn(List.of(booking));
        due(true);

        releaser.releaseDueBookings();
        assertThat(booking.isAwaitingScheduledPickup()).isFalse();
    }

    @Test
    void aBookingCancelledWhileWaitingIsClosedNotOffered() {
        Booking booking = held("b4", BookingStatus.CANCELLED);
        when(bookingRepository.findAwaitingScheduledPickup()).thenReturn(List.of(booking));
        due(true);

        releaser.releaseDueBookings();

        verify(laundryAssignmentService, never()).createLaundryOffers(any());
        // Still marked, so it stops being scanned every two minutes forever.
        assertThat(booking.getPickupReleasedAt()).isNotNull();
    }

    @Test
    void theCustomerIsToldWhenTheirScheduledPickupStarts() {
        // They chose this window days ago and have heard nothing since;
        // createLaundryOffers notifies the agent, not them.
        Booking booking = held("b5", BookingStatus.CREATED);
        when(bookingRepository.findAwaitingScheduledPickup()).thenReturn(List.of(booking));
        due(true);

        releaser.releaseDueBookings();

        verify(notificationService).notifyUserBookingStatusChange(
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void oneBadBookingDoesNotStrandTheRestOfTheMorning() {
        Booking broken = held("bad", BookingStatus.CREATED);
        Booking fine = held("good", BookingStatus.CREATED);
        when(bookingRepository.findAwaitingScheduledPickup()).thenReturn(List.of(broken, fine));
        due(true);
        doThrow(new IllegalStateException("Pickup address has no coordinates"))
                .when(laundryAssignmentService).createLaundryOffers(broken);

        releaser.releaseDueBookings();

        verify(laundryAssignmentService).createLaundryOffers(fine);
    }

    @Test
    void anIncompleteScheduleIsReleasedRatherThanLost() {
        // Half a schedule cannot be timed, and leaving it held means it never
        // happens at all.
        Booking booking = held("b6", BookingStatus.CREATED);
        booking.setPickupSlot(null);
        when(bookingRepository.findAwaitingScheduledPickup()).thenReturn(List.of(booking));

        releaser.releaseDueBookings();

        verify(laundryAssignmentService).createLaundryOffers(booking);
    }

    @Test
    void anEmptyQueueTouchesNothing() {
        when(bookingRepository.findAwaitingScheduledPickup()).thenReturn(List.of());

        releaser.releaseDueBookings();

        verify(laundryAssignmentService, never()).createLaundryOffers(any());
        verify(bookingRepository, never()).save(any());
    }
}
