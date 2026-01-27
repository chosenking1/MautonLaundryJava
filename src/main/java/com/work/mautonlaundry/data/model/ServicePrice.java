package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "service_price")
@Getter
@Setter
@NoArgsConstructor
public class ServicePrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Double white;

    @Column(nullable = false)
    private Double locationMultiplier = 1.0;
}