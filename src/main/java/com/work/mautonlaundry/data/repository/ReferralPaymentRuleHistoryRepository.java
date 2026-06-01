package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.ReferralPaymentRuleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferralPaymentRuleHistoryRepository extends JpaRepository<ReferralPaymentRuleHistory, String> {

    List<ReferralPaymentRuleHistory> findByReferrerIdOrderByChangedAtDesc(String referrerId);
}
