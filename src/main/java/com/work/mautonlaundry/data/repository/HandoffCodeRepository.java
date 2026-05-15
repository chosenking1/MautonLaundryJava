package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.HandoffCode;
import com.work.mautonlaundry.data.model.enums.HandoffCodeStatus;
import com.work.mautonlaundry.data.model.enums.HandoffStage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HandoffCodeRepository extends JpaRepository<HandoffCode, String> {

    Optional<HandoffCode> findByBookingAndStageAndStatus(
            Booking booking, HandoffStage stage, HandoffCodeStatus status);

    List<HandoffCode> findByBookingOrderByIssuedAtDesc(Booking booking);

    /**
     * Pessimistic lock for the redemption flow — guarantees only one rider can
     * redeem a given code even under concurrent attempts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h from HandoffCode h
             where h.booking.id = :bookingId
               and h.code = :code
               and h.status = 'ACTIVE'
            """)
    Optional<HandoffCode> findActiveForRedemption(
            @Param("bookingId") String bookingId,
            @Param("code") String code);
}
