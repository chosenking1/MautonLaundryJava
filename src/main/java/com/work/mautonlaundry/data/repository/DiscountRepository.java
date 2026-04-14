package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Discount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, String> {
    Optional<Discount> findByCodeIgnoreCase(String code);
    Optional<Discount> findByCodeIgnoreCaseAndIsActiveTrue(String code);
}