package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegionRepository extends JpaRepository<Region, String> {

    Optional<Region> findByRegionName(String regionName);

    /** State ids currently in a region. */
    @Query(value = "SELECT state_id FROM region_states WHERE region_id = :regionId", nativeQuery = true)
    List<Integer> findStateIds(@Param("regionId") String regionId);

    /**
     * Adds a state to a region unless it already belongs to one -- a state lives
     * in at most one region (V13's unique index), so a conflicting insert is
     * skipped rather than allowed to move the state silently.
     */
    @Modifying
    @Query(value = """
            INSERT INTO region_states (region_id, state_id)
            VALUES (:regionId, :stateId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addState(@Param("regionId") String regionId, @Param("stateId") Integer stateId);

    @Modifying
    @Query(value = "DELETE FROM region_states WHERE region_id = :regionId AND state_id = :stateId",
            nativeQuery = true)
    void removeState(@Param("regionId") String regionId, @Param("stateId") Integer stateId);

    /** Which region a state already sits in, if any -- so the UI can warn before a move. */
    @Query(value = "SELECT region_id FROM region_states WHERE state_id = :stateId", nativeQuery = true)
    Optional<String> findRegionOfState(@Param("stateId") Integer stateId);

    /**
     * How many access records point at this region: assigned scopes, live
     * temporary grants, and pending upgrade requests. Deleting a region out from
     * under any of them would leave a scope resolving to a region that no longer
     * exists -- a silent loss of data visibility for that user -- so a non-zero
     * count blocks the delete rather than cascading.
     */
    @Query(value = """
            SELECT (SELECT count(*) FROM user_scope WHERE region_id = :regionId)
                 + (SELECT count(*) FROM temporary_scope_grants WHERE temporary_region_id = :regionId)
                 + (SELECT count(*) FROM scope_upgrade_requests WHERE requested_region_id = :regionId)
            """, nativeQuery = true)
    long countScopeReferences(@Param("regionId") String regionId);

    /** region_states rows go with the region (V25 declares ON DELETE CASCADE, but be explicit). */
    @Modifying
    @Query(value = "DELETE FROM region_states WHERE region_id = :regionId", nativeQuery = true)
    void clearStates(@Param("regionId") String regionId);
}
