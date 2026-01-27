package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.LaundryItemCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LaundryItemCatalogRepository extends JpaRepository<LaundryItemCatalog, Long> {
    
    List<LaundryItemCatalog> findByIsActiveTrue();
    
    Optional<LaundryItemCatalog> findByIdAndIsActiveTrue(Long id);
    
    List<LaundryItemCatalog> findByNameContainingIgnoreCaseAndIsActiveTrue(String name);
}