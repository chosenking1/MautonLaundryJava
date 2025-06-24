package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Table(name = "service_price")
@Data
public class ServicePrice {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private int price;


    private int white;

    private int locationMultiplier;

//    private int express;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Services service;
}
