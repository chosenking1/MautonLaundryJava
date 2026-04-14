package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.DiscountUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DiscountUsageLogRepository extends JpaRepository<DiscountUsageLog, String> {
    List<DiscountUsageLog> findByDiscountIdAndAppliedAtBetween(String discountId, LocalDateTime from, LocalDateTime to);
    List<DiscountUsageLog> findByUserIdAndAppliedAtBetween(String userId, LocalDateTime from, LocalDateTime to);
    List<DiscountUsageLog> findByBookingId(String bookingId);
}