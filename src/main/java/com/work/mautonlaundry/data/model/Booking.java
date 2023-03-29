package com.work.mautonlaundry.data.model;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.Email;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;


    @Column
    private String full_name;

    @Email
    @Column
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceType type_of_service;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String service = "[]";



    @Column(nullable = false)
    private LocalDateTime date_booked;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column
    private UrgencyType urgency;

    @Column(nullable = false)
    private Double total_price;

    @Column
    private Boolean deleted;


}
