package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.DiscountUserAssignment;
import com.work.mautonlaundry.data.model.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscountUserAssignmentRepository extends JpaRepository<DiscountUserAssignment, String> {
    Optional<DiscountUserAssignment> findByDiscountIdAndUserId(String discountId, String userId);
    Page<DiscountUserAssignment> findByDiscountId(String discountId, Pageable pageable);
    Page<DiscountUserAssignment> findByDiscountIdAndApprovalStatus(String discountId, ApprovalStatus status, Pageable pageable);
    Page<DiscountUserAssignment> findByApprovalStatus(ApprovalStatus status, Pageable pageable);
}