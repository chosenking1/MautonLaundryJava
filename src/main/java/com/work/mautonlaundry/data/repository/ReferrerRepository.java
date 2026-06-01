package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Referrer;
import com.work.mautonlaundry.data.model.enums.ReferrerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReferrerRepository extends JpaRepository<Referrer, String> {

    Optional<Referrer> findByReferralCodeIgnoreCase(String referralCode);

    boolean existsByReferralCodeIgnoreCase(String referralCode);

    Optional<Referrer> findByEmailIgnoreCaseAndActiveTrue(String email);

    Page<Referrer> findByReferrerType(ReferrerType referrerType, Pageable pageable);

    Page<Referrer> findByActive(boolean active, Pageable pageable);

    Page<Referrer> findByReferrerTypeAndActive(ReferrerType referrerType, boolean active, Pageable pageable);

    @Query("SELECT r FROM Referrer r WHERE " +
            "(LOWER(r.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "OR LOWER(r.referralCode) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<Referrer> search(@Param("term") String term, Pageable pageable);
}
