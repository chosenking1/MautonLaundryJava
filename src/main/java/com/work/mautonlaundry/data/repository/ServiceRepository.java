package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Services;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Services, Long> {

    Optional<Services> findByService_name(String service);
    Optional<Services> deleteByService_name(String service);

}
