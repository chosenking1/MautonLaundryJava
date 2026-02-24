package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    List<Category> findByActiveTrue();
    
    Optional<Category> findByNameAndActiveTrue(String name);
    
    boolean existsByName(String name);
}