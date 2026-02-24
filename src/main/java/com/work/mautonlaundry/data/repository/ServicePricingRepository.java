package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.ServicePricing;
import com.work.mautonlaundry.data.model.Services;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServicePricingRepository extends JpaRepository<ServicePricing, Long> {
    
    List<ServicePricing> findByServiceAndActiveTrue(Services service);
    
    List<ServicePricing> findByServiceIdAndActiveTrue(Long serviceId);
    
    Optional<ServicePricing> findByServiceAndItemTypeAndActiveTrue(Services service, String itemType);
}