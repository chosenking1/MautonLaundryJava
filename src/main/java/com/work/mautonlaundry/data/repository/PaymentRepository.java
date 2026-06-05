package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.enums.PaymentMethod;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
import com.work.mautonlaundry.data.model.enums.ReferrerType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBooking_Id(String bookingId);
    Optional<Payment> findByTransactionId(String transactionId);
    
    // Analytics methods
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") PaymentStatus status);
    
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.paymentDate >= :date")
    BigDecimal sumAmountByStatusAndPaymentDateAfter(@Param("status") PaymentStatus status,
                                                    @Param("date") LocalDateTime date);
    
    @Query("SELECT p.paymentMethod, COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status GROUP BY p.paymentMethod")
    List<Object[]> sumAmountByStatusGroupByPaymentMethod(@Param("status") PaymentStatus status);
    
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.paymentDate BETWEEN :start AND :end")
    BigDecimal sumAmountByStatusAndDateBetween(@Param("status") PaymentStatus status,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);
    
    long countByStatus(PaymentStatus status);

    long countByStatusAndPaymentDateAfter(PaymentStatus status, LocalDateTime date);

    // Lifetime spend for one customer (completed payments).
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p JOIN p.booking b " +
            "WHERE b.user.id = :userId AND p.status = :status")
    BigDecimal sumSpendForUser(@Param("userId") String userId, @Param("status") PaymentStatus status);

    // Spend for one customer within a half-open period [start, end).
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p JOIN p.booking b " +
            "WHERE b.user.id = :userId AND p.status = :status AND p.paymentDate >= :start AND p.paymentDate < :end")
    BigDecimal sumSpendForUserInPeriod(@Param("userId") String userId,
                                       @Param("status") PaymentStatus status,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    // Spend across a set of customers within a half-open period [start, end). Guard against empty userIds in the caller.
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p JOIN p.booking b " +
            "WHERE b.user.id IN :userIds AND p.status = :status AND p.paymentDate >= :start AND p.paymentDate < :end")
    BigDecimal sumSpendForUsersInPeriod(@Param("userIds") java.util.Collection<String> userIds,
                                        @Param("status") PaymentStatus status,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    // Per-customer lifetime spend (completed payments). Row = [userId, totalSpend].
    @Query("SELECT b.user.id, COALESCE(SUM(p.amount), 0) FROM Payment p JOIN p.booking b " +
            "WHERE p.status = :status GROUP BY b.user.id")
    List<Object[]> sumSpendPerCustomer(@Param("status") PaymentStatus status);

    // Revenue over a half-open period [start, end) — clean, non-overlapping boundaries.
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.status = :status AND p.paymentDate >= :start AND p.paymentDate < :end")
    BigDecimal sumRevenueInPeriod(@Param("status") PaymentStatus status,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    // Top customers by lifetime spend. Row = [userId, fullName, totalSpend].
    @Query("SELECT b.user.id, b.user.full_name, COALESCE(SUM(p.amount), 0) FROM Payment p JOIN p.booking b " +
            "WHERE p.status = :status " +
            "GROUP BY b.user.id, b.user.full_name ORDER BY SUM(p.amount) DESC")
    List<Object[]> topCustomersByLifetimeSpend(@Param("status") PaymentStatus status, Pageable pageable);

    // Top customers by spend within a period. Row = [userId, fullName, totalSpend].
    @Query("SELECT b.user.id, b.user.full_name, COALESCE(SUM(p.amount), 0) FROM Payment p JOIN p.booking b " +
            "WHERE p.status = :status AND p.paymentDate >= :start AND p.paymentDate < :end " +
            "GROUP BY b.user.id, b.user.full_name ORDER BY SUM(p.amount) DESC")
    List<Object[]> topCustomersBySpendInPeriod(@Param("status") PaymentStatus status,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               Pageable pageable);

    // Top CAS by revenue generated from their referred customers within a period.
    // Row = [referrerId, referrerName, totalRevenue].
    @Query("SELECT r.id, r.name, COALESCE(SUM(p.amount), 0) FROM Payment p JOIN p.booking b, " +
            "ReferralAttribution a, Referrer r " +
            "WHERE b.user.id = a.userId AND a.referrerId = r.id AND r.referrerType = :type " +
            "AND p.status = :status AND p.paymentDate >= :start AND p.paymentDate < :end " +
            "GROUP BY r.id, r.name ORDER BY SUM(p.amount) DESC")
    List<Object[]> topCasByReferredRevenueInPeriod(@Param("type") ReferrerType type,
                                                   @Param("status") PaymentStatus status,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end,
                                                   Pageable pageable);
}
