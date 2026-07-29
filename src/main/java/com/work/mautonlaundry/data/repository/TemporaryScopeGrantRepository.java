package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.TemporaryScopeGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TemporaryScopeGrantRepository extends JpaRepository<TemporaryScopeGrant, String> {

    /**
     * The user's live upgrade, if any.
     *
     * <p>Checks expires_at as well as is_active, so an upgrade stops applying the
     * moment it lapses even if the revert job has not swept yet. The job makes
     * the state tidy and auditable; this makes it correct. Relying on the job
     * alone would leave a window where expired scope still worked -- exactly the
     * failure §6.1 exists to prevent.
     */
    @Query("""
            SELECT g FROM TemporaryScopeGrant g
             WHERE g.userId = :userId AND g.isActive = true AND g.expiresAt > :now
            """)
    Optional<TemporaryScopeGrant> findActiveFor(@Param("userId") String userId,
                                                @Param("now") LocalDateTime now);

    /** Live grants whose time is up, for the revert sweep (spec §8.3). */
    @Query("""
            SELECT g FROM TemporaryScopeGrant g
             WHERE g.isActive = true AND g.expiresAt <= :now
            """)
    List<TemporaryScopeGrant> findLapsed(@Param("now") LocalDateTime now);
}
