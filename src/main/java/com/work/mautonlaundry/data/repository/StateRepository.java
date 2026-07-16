package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.State;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StateRepository extends JpaRepository<State, Integer> {

    /**
     * @param normalizedName must come from GeoNormalizer.normalizeState(), not a
     *                       raw display name.
     */
    Optional<State> findByNormalizedName(String normalizedName);
}
