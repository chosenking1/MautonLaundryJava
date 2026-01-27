package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.PricingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PricingConfigRepository extends JpaRepository<PricingConfig, Long> {
    
    Optional<PricingConfig> findByKey(String key);
    
    @Query("SELECT p FROM PricingConfig p WHERE p.key = :key ORDER BY p.effectiveFrom DESC LIMIT 1")
    Optional<PricingConfig> findLatestByKey(@Param("key") String key);
}