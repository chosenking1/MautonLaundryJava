package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.ScopeUpgradeRequest;
import com.work.mautonlaundry.data.model.enums.ScopeUpgradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScopeUpgradeRequestRepository extends JpaRepository<ScopeUpgradeRequest, String> {

    List<ScopeUpgradeRequest> findByStatusOrderByRequestedAtAsc(ScopeUpgradeStatus status);

    List<ScopeUpgradeRequest> findByRequesterIdOrderByRequestedAtDesc(String requesterId);
}
