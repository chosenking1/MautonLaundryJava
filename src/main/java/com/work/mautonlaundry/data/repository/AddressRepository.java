package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.AppUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, String> {
    List<Address> findByUserAndDeletedFalse(AppUser user );

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.user.id = :userId")
    void clearDefaultForUser(@Param("userId") String userId);

    /**
     * Addresses the geo backfill can still act on: never attempted, and holding
     * coordinates to attempt with.
     *
     * <p>Ordered oldest-first so a limited run makes predictable progress rather
     * than revisiting whatever the planner happens to return.
     */
    @Query("""
            SELECT a FROM Address a
             WHERE a.geoResolvedAt IS NULL
               AND a.latitude IS NOT NULL
               AND a.longitude IS NOT NULL
               AND (a.deleted IS NULL OR a.deleted = false)
             ORDER BY a.createdAt ASC
            """)
    List<Address> findUnresolvedWithCoordinates(Pageable pageable);

    default List<Address> findUnresolvedWithCoordinates(int limit) {
        return findUnresolvedWithCoordinates(PageRequest.of(0, limit));
    }

    @Query("""
            SELECT COUNT(a) FROM Address a
             WHERE a.geoResolvedAt IS NULL
               AND a.latitude IS NOT NULL
               AND a.longitude IS NOT NULL
               AND (a.deleted IS NULL OR a.deleted = false)
            """)
    long countUnresolvedWithCoordinates();

    /**
     * The admin queue: attempted, but no zone was determined. These are the rows
     * an alias can rescue -- unlike the never-attempted set above.
     */
    @Query("""
            SELECT a FROM Address a
             WHERE a.geoResolvedAt IS NOT NULL
               AND a.lgaId IS NULL
               AND (a.deleted IS NULL OR a.deleted = false)
             ORDER BY a.createdAt DESC
            """)
    List<Address> findResolvedWithoutLga(Pageable pageable);
}