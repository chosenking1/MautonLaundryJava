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

    @Email
    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private Long booking_id;

    @Column
    private LocalDateTime pick_up;

    @Column
    private LocalDateTime return_date;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column
    private UrgencyType urgency;

    @Enumerated(EnumType.STRING)
    @Column
    private DeliveryStatus deliveryStatus;

    @Column
    private Boolean deleted;
}
