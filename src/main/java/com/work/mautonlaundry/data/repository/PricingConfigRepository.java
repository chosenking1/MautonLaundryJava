package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.PricingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PricingConfigRepository extends JpaRepository<PricingConfig, Long> {
    
    Optional<PricingConfig> findByKey(String key);

    Optional<PricingConfig> findTopByKeyOrderByEffectiveFromDesc(String key);
}
