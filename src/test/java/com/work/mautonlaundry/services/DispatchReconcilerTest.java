package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.DeliveryAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A dispatch job lives in Redis with a TTL. When it drains without finding an
 * online rider, the booking is left at *_DISPATCH_PENDING with nothing to retry
 * it -- it waits forever and the first sign of trouble is a customer asking
 * where their rider is. This sweep is what puts the job back.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispatchReconcilerTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private DeliveryAssignmentRepository deliveryAssignmentRepository;
    @Mock private DispatchEngine dispatchEngine;
    @InjectMocks private DispatchReconciler reconciler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reconciler, "reconcileAfterMinutes", 5L);
        when(bookingRepository.findByStatusAndDeletedFalse(any())).thenReturn(List.of());
    }

    private Booking booking(String id, LocalDateTime updatedAt) {
        Booking b = new Booking();
        b.setId(id);
        b.setUpdatedAt(updatedAt);
        return b;
    }

    @Test
    void aBookingLeftWaitingIsOfferedAgain() {
        Booking stranded = booking("b1", LocalDateTime.now().minusHours(1));
        when(bookingRepository.findByStatusAndDeletedFalse(BookingStatus.PICKUP_DISPATCH_PENDING))
                .thenReturn(List.of(stranded));
        when(deliveryAssignmentRepository.existsByBooking_IdAndPhaseAndStatusIn(
                anyString(), any(), any())).thenReturn(false);

        reconciler.reOfferStrandedBookings();

        verify(dispatchEngine).enqueueDispatchJob(
                stranded, DeliveryAssignmentPhase.PICKUP_FROM_CUSTOMER);
    }

    @Test
    void aRiderWhoAlreadyAcceptedIsNotDisturbed() {
        Booking taken = booking("b2", LocalDateTime.now().minusHours(1));
        when(bookingRepository.findByStatusAndDeletedFalse(BookingStatus.PICKUP_DISPATCH_PENDING))
                .thenReturn(List.of(taken));
        when(deliveryAssignmentRepository.existsByBooking_IdAndPhaseAndStatusIn(
                anyString(), any(), any())).thenReturn(true);

        reconciler.reOfferStrandedBookings();

        verify(dispatchEngine, never()).enqueueDispatchJob(any(), any());
    }

    @Test
    void aLiveAttemptIsGivenTimeBeforeBeingDuplicated() {
        // Just enqueued: re-offering immediately would race the dispatch that is
        // already working on it.
        Booking fresh = booking("b3", LocalDateTime.now().minusSeconds(30));
        when(bookingRepository.findByStatusAndDeletedFalse(BookingStatus.PICKUP_DISPATCH_PENDING))
                .thenReturn(List.of(fresh));

        reconciler.reOfferStrandedBookings();

        verify(dispatchEngine, never()).enqueueDispatchJob(any(), any());
    }

    @Test
    void theReturnLegIsCoveredToo() {
        // The same stranding happens on the way back from the laundry.
        Booking stranded = booking("b4", LocalDateTime.now().minusHours(2));
        when(bookingRepository.findByStatusAndDeletedFalse(BookingStatus.DELIVERY_DISPATCH_PENDING))
                .thenReturn(List.of(stranded));
        when(deliveryAssignmentRepository.existsByBooking_IdAndPhaseAndStatusIn(
                anyString(), any(), any())).thenReturn(false);

        reconciler.reOfferStrandedBookings();

        verify(dispatchEngine).enqueueDispatchJob(
                eq(stranded), eq(DeliveryAssignmentPhase.RETURN_TO_CUSTOMER));
    }
}
