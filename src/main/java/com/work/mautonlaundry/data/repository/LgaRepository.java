package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Lga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LgaRepository extends JpaRepository<Lga, Integer> {

    Optional<Lga> findByStateIdAndNormalizedName(Integer stateId, String normalizedName);

    /** The zones (LGAs) of one state, for the Zone Management screen. */
    List<Lga> findByStateIdOrderByName(Integer stateId);

    /**
     * Resolves an external LGA name to a canonical lgas.id, within a known state.
     *
     * <p>Canonical name first, then lga_aliases (V16). Google's
     * administrative_area_level_2 mostly matches the official register once
     * normalized, but not always: it returns 'Abuja Municipal Area Council' for
     * the FCT's 'Municipal Area Council', and 'Nassarawa' for Kano's 'Nasarawa'.
     * Those live in lga_aliases rather than being papered over with extra
     * normalization rules, which would break the names that already work.
     *
     * <p>Measured against the live Geocoding API: 8/10 real coordinates resolved
     * canonically, 11/11 once aliases are consulted.
     *
     * <p>Scoped to a state because LGA names are not globally unique -- resolving
     * 'surulere' without a state is ambiguous between Lagos and Oyo.
     *
     * @param normalizedName from GeoNormalizer.normalizeLga()
     */
    @Query(value = """
            SELECT l.id FROM lgas l
             WHERE l.state_id = :stateId AND l.normalized_name = :normalizedName
            UNION ALL
            SELECT al.lga_id FROM lga_aliases al
             WHERE al.state_id = :stateId AND al.normalized_alias = :normalizedName
            LIMIT 1
            """, nativeQuery = true)
    Optional<Integer> resolveIdByStateAndName(@Param("stateId") Integer stateId,
                                              @Param("normalizedName") String normalizedName);
}
