package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Payment;
import com.work.mautonlaundry.data.model.enums.PaymentMethod;
import com.work.mautonlaundry.data.model.enums.PaymentStatus;
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
}
