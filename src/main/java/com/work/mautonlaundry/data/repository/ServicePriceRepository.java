package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.ServicePrice;
import com.work.mautonlaundry.data.model.Services;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServicePriceRepository extends JpaRepository<ServicePrice, Long> {
    ServicePrice findServicePriceByService(Services service);
}
