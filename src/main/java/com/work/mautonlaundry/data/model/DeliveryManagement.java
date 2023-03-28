package com.work.mautonlaundry.data.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.Email;
import java.time.LocalDateTime;
@Entity
@Getter
@Setter
@NoArgsConstructor
public class DeliveryManagement {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
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
}
