package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.UserScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserScopeRepository extends JpaRepository<UserScope, String> {

    /**
     * The states belonging to a region, for REGIONAL scope.
     *
     * <p>Resolved once when the scope is built and cached with it, so REGIONAL
     * collapses to the same "state_id IN (...)" predicate as STATE instead of
     * needing a correlated subquery in every scoped query.
     *
     * <p>Native because region_states is a pure join table with no entity --
     * nothing else needs one.
     */
    @Query(value = "SELECT state_id FROM region_states WHERE region_id = :regionId",
            nativeQuery = true)
    List<Integer> findStateIdsByRegionId(@Param("regionId") String regionId);
}
