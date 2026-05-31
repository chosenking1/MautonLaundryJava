package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.ReferralAttribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralAttributionRepository extends JpaRepository<ReferralAttribution, String> {

    Optional<ReferralAttribution> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<ReferralAttribution> findByReferrerId(String referrerId);

    long countByReferrerId(String referrerId);
}
