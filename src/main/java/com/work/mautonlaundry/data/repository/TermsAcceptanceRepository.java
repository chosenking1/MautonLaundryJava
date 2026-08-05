package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.TermsAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, String> {

    boolean existsByUserAndVersion(AppUser user, String version);

    /** Everything this user has ever agreed to, newest first. */
    List<TermsAcceptance> findByUserOrderByAcceptedAtDesc(AppUser user);

    Optional<TermsAcceptance> findFirstByUserOrderByAcceptedAtDesc(AppUser user);
}
