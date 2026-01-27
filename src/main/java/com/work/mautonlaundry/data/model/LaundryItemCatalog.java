package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "laundry_item_catalog")
@Getter
@Setter
@NoArgsConstructor
public class LaundryItemCatalog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String unit; // "pair", "item", "kg"

    @Column(name = "base_price_colored", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePriceColored;

    @Column(name = "base_price_white", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePriceWhite;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}