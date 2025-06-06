package com.work.mautonlaundry.data.model;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Table(name = "payment")
@Data
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many payments can belong to one booking
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false) // Foreign key to the Booking table
    private Booking booking;

    @Column(nullable = false)
    private BigDecimal amount; // The amount of this specific payment

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod; // Enum for CARD, CASH, TRANSFER, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status; // Enum for PENDING, SUCCESS, FAILED, REFUNDED

    @Column(unique = true) // Transaction ID from payment gateway, should be unique
    private String transactionId; // Reference to the payment gateway's transaction ID

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime paymentDate; // When the payment was recorded

    // Optional: Add a reference to the user who made the payment, if different from booking user
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "user_id")
    // private User user;
}

