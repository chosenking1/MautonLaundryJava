package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.AccountDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {

    @Query("SELECT r FROM AccountDeletionRequest r "
            + "WHERE r.user.id = :userId AND r.cancelledAt IS NULL AND r.completedAt IS NULL")
    Optional<AccountDeletionRequest> findPendingForUser(@Param("userId") String userId);

    /** Checked on every authenticated request, so it is a single indexed EXISTS by email. */
    @Query("SELECT COUNT(r) > 0 FROM AccountDeletionRequest r "
            + "WHERE LOWER(r.user.email) = LOWER(:email) AND r.cancelledAt IS NULL AND r.completedAt IS NULL")
    boolean isPendingForEmail(@Param("email") String email);

    @Query("SELECT r FROM AccountDeletionRequest r JOIN FETCH r.user "
            + "WHERE r.scheduledFor <= :now AND r.cancelledAt IS NULL AND r.completedAt IS NULL")
    List<AccountDeletionRequest> findDue(@Param("now") LocalDateTime now);
}
