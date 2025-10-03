package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;

import lombok.*;


import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "booking")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceType type_of_service;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String service = "[]";

    @Column(nullable = false)
    private LocalDateTime date_booked;

    @Enumerated(EnumType.STRING)
    @Column
    private UrgencyType urgency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LaundryStatus laundryStatus;

     @ManyToOne(fetch = FetchType.LAZY)
     @JoinColumn(name = "user_id")
     private User user;

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL)
    private Payment payment;

//    @Column(nullable = false)
//    private Double total_price;

    @Column
    private Boolean deleted;


}
