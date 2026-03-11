package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingLifecycleScheduler {
    private final BookingRepository bookingRepository;
    private final AssignmentService assignmentService;

    @Value("${app.assignment.laundry.acceptance-ttl-minutes:60}")
    private long laundryAcceptanceTtlMinutes;

    @Scheduled(fixedDelayString = "${app.assignment.scheduler.fixed-delay-ms:60000}")
    @Transactional
    public void autoAcceptExpiredLaundryAssignments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(laundryAcceptanceTtlMinutes);
        List<Booking> expired = bookingRepository.findByStatusAndAssignmentTimestampBeforeAndDeletedFalse(
                BookingStatus.PENDING_LAUNDRYMAN_ACCEPTANCE,
                threshold
        );

        for (Booking booking : expired) {
            booking.setStatus(BookingStatus.LAUNDRYMAN_ACCEPTED);
            booking.setAutoAccepted(true);
            bookingRepository.save(booking);
            assignmentService.notifyNearestDeliveryAgentsForDropOff(booking.getId());
            log.info("Auto-accepted laundry assignment for booking {}", booking.getId());
        }
    }

    @Scheduled(fixedDelayString = "${app.assignment.scheduler.fixed-delay-ms:60000}")
    @Transactional
    public void triggerDueDeliveryBroadcasts() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> due = bookingRepository.findByStatusAndDeliveryBroadcastAtBeforeAndDeletedFalse(
                BookingStatus.READY_FOR_PICKUP,
                now
        );

        for (Booking booking : due) {
            assignmentService.notifyNearestDeliveryAgentsForDropOff(booking.getId());
            booking.setDeliveryBroadcastAt(null);
            bookingRepository.save(booking);
            log.info("Triggered scheduled delivery broadcast for booking {}", booking.getId());
        }
    }
}
