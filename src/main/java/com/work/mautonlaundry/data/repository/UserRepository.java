package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findUserByEmail(String email);

    Optional<AppUser> findUserById(String id);

    boolean existsByEmail(String email);
    
    // Admin dashboard queries
    Long countByDeletedFalse();
    Long countByEmailVerifiedTrue();
    
    Long countByRole(Role role); // Added for AnalyticsService
}
