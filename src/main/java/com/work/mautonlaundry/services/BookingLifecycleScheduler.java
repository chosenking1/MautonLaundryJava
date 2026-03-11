package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.LaundrymanAssignment;
import com.work.mautonlaundry.data.model.enums.LaundrymanAssignmentStatus;
import com.work.mautonlaundry.data.repository.LaundrymanAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingLifecycleScheduler {
    private final LaundrymanAssignmentRepository laundrymanAssignmentRepository;
    private final LaundryAssignmentService laundryAssignmentService;

    @Value("${app.assignment.laundry.acceptance-ttl-minutes:60}")
    private long laundryAcceptanceTtlMinutes;

    @Scheduled(fixedDelayString = "${app.assignment.scheduler.fixed-delay-ms:60000}")
    @Transactional
    public void autoAcceptExpiredLaundryAssignments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(laundryAcceptanceTtlMinutes);
        List<LaundrymanAssignment> expiredOffers = laundrymanAssignmentRepository.findByStatusAndCreatedAtBefore(
                LaundrymanAssignmentStatus.OFFERED,
                threshold
        );
        Set<String> processedBookings = new HashSet<>();
        for (LaundrymanAssignment offer : expiredOffers) {
            String bookingId = offer.getBooking().getId();
            if (processedBookings.contains(bookingId)) {
                continue;
            }
            processedBookings.add(bookingId);
            laundryAssignmentService.autoAcceptExpiredOffer(offer);
            log.info("Auto-accepted laundry offer for booking {}", bookingId);
        }
    }
}
