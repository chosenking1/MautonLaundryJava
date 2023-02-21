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

    @Column(nullable = false)
    private ServiceType type_of_service;

    @Column(columnDefinition = "json")
    private String service;

    @Column(nullable = false)
    private LocalDateTime date_booked;

    @Column(nullable = false)
    private String address;

    @Column
    private UrgencyType urgency;

    @Column(nullable = false)
    private int total_price;





}
