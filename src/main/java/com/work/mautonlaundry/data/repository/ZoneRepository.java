package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, String> {

    List<Zone> findByStateIdOrderByName(Integer stateId);

    Optional<Zone> findByStateIdAndNormalizedName(Integer stateId, String normalizedName);

    /**
     * The LGA ids of a zone. This is the ZONE scope target: a zone resolves to
     * this set, and ScopeFilterService filters orders whose address LGA is in it.
     * An empty result denies (an empty zone sees nothing), same as an empty
     * region.
     */
    @Query(value = "SELECT lga_id FROM zone_lgas WHERE zone_id = :zoneId", nativeQuery = true)
    List<Integer> findLgaIds(@Param("zoneId") String zoneId);

    /**
     * Adds an LGA to a zone unless it already belongs to one -- an LGA lives in at
     * most one zone (V24 unique index), so a conflicting insert is skipped rather
     * than silently moving the LGA.
     */
    @Modifying
    @Query(value = """
            INSERT INTO zone_lgas (zone_id, lga_id) VALUES (:zoneId, :lgaId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addLga(@Param("zoneId") String zoneId, @Param("lgaId") Integer lgaId);

    @Modifying
    @Query(value = "DELETE FROM zone_lgas WHERE zone_id = :zoneId AND lga_id = :lgaId", nativeQuery = true)
    void removeLga(@Param("zoneId") String zoneId, @Param("lgaId") Integer lgaId);

    /** Which zone an LGA already sits in, if any -- so the UI can flag a move. */
    @Query(value = "SELECT zone_id FROM zone_lgas WHERE lga_id = :lgaId", nativeQuery = true)
    Optional<String> findZoneOfLga(@Param("lgaId") Integer lgaId);
}
