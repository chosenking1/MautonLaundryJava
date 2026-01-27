package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Services;
import com.work.mautonlaundry.data.model.enums.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Services, Long> {
    List<Services> findByDeletedFalse();
    Optional<Services> findByIdAndDeletedFalse(Long id);
    List<Services> findByCategoryAndDeletedFalse(ServiceType category);
}