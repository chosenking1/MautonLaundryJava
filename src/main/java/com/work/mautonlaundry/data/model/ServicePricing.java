package com.work.mautonlaundry.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "service_pricing")
@Data
@NoArgsConstructor
public class ServicePricing {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    @JsonIgnore
    private Services service;
    
    @Column(nullable = false)
    private String priceType; // e.g., "PER_ITEM", "PER_KG", "FLAT_RATE"
    
    @Column(nullable = false)
    private String itemType; // e.g., "SHIRT", "PANTS", "DRESS", "GENERAL", "WHITE"
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column
    private String unit; // e.g., "PCS", "KG", "LOAD"
    
    @Column(nullable = false)
    private Boolean active = true;
}