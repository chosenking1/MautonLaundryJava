package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Category;
import com.work.mautonlaundry.data.model.Services;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Services, Long> {
    List<Services> findByActiveTrue();
    List<Services> findByDeletedFalseAndActiveTrue();
    Optional<Services> findByIdAndDeletedFalseAndActiveTrue(Long id);
    Optional<Services> findByIdAndActiveTrue(Long id);
    List<Services> findByCategoryAndDeletedFalseAndActiveTrue(Category category);
    List<Services> findByCategoryIdAndActiveTrue(Long categoryId);
    List<Services> findByCategoryIdAndDeletedFalseAndActiveTrue(Long categoryId);
}