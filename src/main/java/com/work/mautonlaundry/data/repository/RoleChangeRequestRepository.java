package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.RoleChangeRequest;
import com.work.mautonlaundry.data.model.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleChangeRequestRepository extends JpaRepository<RoleChangeRequest, Long> {
    List<RoleChangeRequest> findByStatus(RequestStatus status);
}