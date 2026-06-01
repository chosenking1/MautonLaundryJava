package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.ReferralPayout;
import com.work.mautonlaundry.data.model.enums.ReferralPayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReferralPayoutRepository extends JpaRepository<ReferralPayout, String> {

    Page<ReferralPayout> findByReferrerIdOrderByGeneratedAtDesc(String referrerId, Pageable pageable);

    List<ReferralPayout> findByReferrerIdOrderByGeneratedAtDesc(String referrerId);

    List<ReferralPayout> findByStatusOrderByGeneratedAtAsc(ReferralPayoutStatus status);

    boolean existsByReferrerIdAndPeriodFromAndPeriodTo(String referrerId, LocalDate periodFrom, LocalDate periodTo);

    /** Sum of final payout amounts for a referrer filtered by status. */
    @Query("SELECT COALESCE(SUM(p.finalPayoutAmount), 0) FROM ReferralPayout p " +
            "WHERE p.referrerId = :referrerId AND p.status = :status")
    BigDecimal sumFinalAmountByReferrerAndStatus(@Param("referrerId") String referrerId,
                                                 @Param("status") ReferralPayoutStatus status);

    /** Sum of final payout amounts for a referrer across the given statuses (e.g. pending balance). */
    @Query("SELECT COALESCE(SUM(p.finalPayoutAmount), 0) FROM ReferralPayout p " +
            "WHERE p.referrerId = :referrerId AND p.status IN :statuses")
    BigDecimal sumFinalAmountByReferrerAndStatusIn(@Param("referrerId") String referrerId,
                                                   @Param("statuses") List<ReferralPayoutStatus> statuses);
}
