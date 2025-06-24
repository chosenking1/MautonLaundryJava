package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


import java.time.LocalDateTime;


@Entity
@Getter
@Setter
@NoArgsConstructor
@Table
public class DeliveryManagement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false)
    private LocalDateTime pick_up_date;

    @Column(nullable = false)
    private LocalDateTime return_date;

    @Column(nullable = false)
    private String userAddress;

    private String agentAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UrgencyType urgency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean deleted = false;
}
