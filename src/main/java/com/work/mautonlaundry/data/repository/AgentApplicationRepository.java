package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.AgentApplication;
import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.enums.AgentApplicationStatus;
import com.work.mautonlaundry.data.model.enums.AgentApplicationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentApplicationRepository extends JpaRepository<AgentApplication, Long> {
    Optional<AgentApplication> findFirstByUserAndTypeOrderByCreatedAtDesc(AppUser user, AgentApplicationType type);
    List<AgentApplication> findByStatus(AgentApplicationStatus status);
    List<AgentApplication> findByStatusAndType(AgentApplicationStatus status, AgentApplicationType type);
}
