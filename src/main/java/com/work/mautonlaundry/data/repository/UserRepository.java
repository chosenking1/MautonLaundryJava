package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findUserByEmail(String email);

    Optional<AppUser> findUserById(String id);

    boolean existsByEmail(String email);
    
    // Pagination support
    Page<AppUser> findByDeletedFalse(Pageable pageable);
    
    // Admin dashboard queries
    Long countByDeletedFalse();
    Long countByDeletedFalseAndOnlineTrue();
    Long countByEmailVerifiedTrue();
    
    Long countByRole(Role role); // Added for AnalyticsService

    List<AppUser> findByRoleAndDeletedFalse(Role role);
    
    // Analytics methods
    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.deleted = false AND u.createdAt >= :date")
    Long countCreatedAfter(@Param("date") LocalDateTime date);
    
    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.deleted = false AND u.createdAt BETWEEN :start AND :end")
    Long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    @Query("SELECT COUNT(DISTINCT b.user) FROM Booking b WHERE b.deleted = false AND b.createdAt >= :date")
    Long countUsersWithBookingsAfter(@Param("date") LocalDateTime date);
    
    @Query("SELECT COUNT(DISTINCT b.user) FROM Booking b WHERE b.deleted = false AND b.createdAt BETWEEN :start AND :end")
    Long countUsersWithBookingsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    @Query("SELECT DISTINCT u FROM AppUser u LEFT JOIN Booking b ON u = b.user AND b.deleted = false " +
           "WHERE b.createdAt BETWEEN :start AND :end AND u.deleted = false")
    List<AppUser> findUsersWithActivityBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
