package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.ReferralAttribution;
import com.work.mautonlaundry.data.model.enums.ReferrerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralAttributionRepository extends JpaRepository<ReferralAttribution, String> {

    Optional<ReferralAttribution> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<ReferralAttribution> findByReferrerId(String referrerId);

    long countByReferrerId(String referrerId);

    // Active CAS = referrers of the given type who have >= 1 referred customer that
    // placed a (non-deleted) order since :since (e.g. last 30 days).
    @Query("SELECT COUNT(DISTINCT a.referrerId) FROM ReferralAttribution a, Booking b, Referrer r " +
            "WHERE b.user.id = a.userId AND r.id = a.referrerId AND r.referrerType = :type " +
            "AND b.deleted = false AND b.createdAt >= :since")
    long countActiveReferrersWithType(@Param("type") ReferrerType type, @Param("since") LocalDateTime since);

    // Acquisition source per referred user. Row = [userId, referrerName]. Users absent here are "Organic".
    @Query("SELECT a.userId, r.name FROM ReferralAttribution a, Referrer r WHERE r.id = a.referrerId")
    List<Object[]> acquisitionSourceByUser();
}
