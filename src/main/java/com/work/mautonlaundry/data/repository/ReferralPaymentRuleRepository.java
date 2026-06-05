package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.ReferralPaymentRule;
import com.work.mautonlaundry.data.model.enums.ReferralPaymentFrequency;
import com.work.mautonlaundry.data.model.enums.ReferralRuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReferralPaymentRuleRepository extends JpaRepository<ReferralPaymentRule, String> {

    List<ReferralPaymentRule> findByReferrerId(String referrerId);

    List<ReferralPaymentRule> findByReferrerIdAndActiveTrue(String referrerId);

    /**
     * Active rules for a referrer that are effective on the given date
     * (effective_from on or before the date, and effective_until null or on/after it).
     * Powers correct calculation across mid-period rule changes.
     */
    @Query("SELECT r FROM ReferralPaymentRule r WHERE r.referrerId = :referrerId " +
            "AND r.active = true " +
            "AND r.effectiveFrom <= :onDate " +
            "AND (r.effectiveUntil IS NULL OR r.effectiveUntil >= :onDate)")
    List<ReferralPaymentRule> findActiveRulesEffectiveOn(@Param("referrerId") String referrerId,
                                                         @Param("onDate") LocalDate onDate);

    /** Referrers that have at least one active rule with the given payment frequency. */
    @Query("SELECT DISTINCT r.referrerId FROM ReferralPaymentRule r " +
            "WHERE r.active = true AND r.paymentFrequency = :frequency")
    List<String> findReferrerIdsWithActiveFrequency(@Param("frequency") ReferralPaymentFrequency frequency);

    /** Referrers with at least one active rule of the given type (e.g. MANUAL_OVERRIDE = on salary). */
    @Query("SELECT DISTINCT r.referrerId FROM ReferralPaymentRule r " +
            "WHERE r.active = true AND r.ruleType = :ruleType")
    List<String> findReferrerIdsWithActiveRuleType(@Param("ruleType") ReferralRuleType ruleType);
}
