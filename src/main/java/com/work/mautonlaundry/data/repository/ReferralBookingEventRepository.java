package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.ReferralBookingEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralBookingEventRepository extends JpaRepository<ReferralBookingEvent, String> {

    Optional<ReferralBookingEvent> findByBookingId(String bookingId);

    boolean existsByBookingId(String bookingId);

    long countByAttributionId(String attributionId);

    List<ReferralBookingEvent> findByAttributionIdOrderByCreatedAtDesc(String attributionId);

    /** Lifetime commission earned across all of a referrer's referred customers. */
    @Query("SELECT COALESCE(SUM(e.referrerCommissionEarned), 0) FROM ReferralBookingEvent e, ReferralAttribution a " +
            "WHERE e.attributionId = a.id AND a.referrerId = :referrerId")
    BigDecimal sumCommissionEarnedForReferrer(@Param("referrerId") String referrerId);

    /** Commission earned for a referrer within a period (by event creation time). */
    @Query("SELECT COALESCE(SUM(e.referrerCommissionEarned), 0) FROM ReferralBookingEvent e, ReferralAttribution a " +
            "WHERE e.attributionId = a.id AND a.referrerId = :referrerId " +
            "AND e.createdAt >= :from AND e.createdAt < :to")
    BigDecimal sumCommissionEarnedForReferrerInPeriod(@Param("referrerId") String referrerId,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to);

    /** Number of orders placed by a referrer's referred customers within a period. */
    @Query("SELECT COUNT(e) FROM ReferralBookingEvent e, ReferralAttribution a " +
            "WHERE e.attributionId = a.id AND a.referrerId = :referrerId " +
            "AND e.createdAt >= :from AND e.createdAt < :to")
    long countOrdersForReferrerInPeriod(@Param("referrerId") String referrerId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /** Booking events for a referrer within a period, most recent first. */
    @Query("SELECT e FROM ReferralBookingEvent e, ReferralAttribution a " +
            "WHERE e.attributionId = a.id AND a.referrerId = :referrerId " +
            "AND e.createdAt >= :from AND e.createdAt < :to ORDER BY e.createdAt DESC")
    List<ReferralBookingEvent> findEventsForReferrerInPeriod(@Param("referrerId") String referrerId,
                                                             @Param("from") LocalDateTime from,
                                                             @Param("to") LocalDateTime to);

    /** Recent activity feed for a referrer's in-app dashboard. */
    @Query("SELECT e FROM ReferralBookingEvent e, ReferralAttribution a " +
            "WHERE e.attributionId = a.id AND a.referrerId = :referrerId ORDER BY e.createdAt DESC")
    List<ReferralBookingEvent> findRecentForReferrer(@Param("referrerId") String referrerId, Pageable pageable);

    /**
     * Attribution ids of a referrer's referred customers who have placed 2+
     * orders. The caller takes {@code .size()} for the repeat-customer /
     * milestone count.
     */
    @Query("SELECT e.attributionId FROM ReferralBookingEvent e, ReferralAttribution a " +
            "WHERE e.attributionId = a.id AND a.referrerId = :referrerId " +
            "GROUP BY e.attributionId HAVING COUNT(e) >= 2")
    List<String> findRepeatAttributionIds(@Param("referrerId") String referrerId);
}
