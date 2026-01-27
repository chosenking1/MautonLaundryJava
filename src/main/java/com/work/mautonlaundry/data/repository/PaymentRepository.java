package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    // Analytics methods
    @Query("SELECT SUM(p.amount) FROM Payment p")
    Double sumTotalAmount();
    
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.paymentDate > :date")
    Double sumAmountByPaymentDateAfter(@Param("date") java.time.LocalDateTime date);
}
