package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_laundry_items")
@Getter
@Setter
@NoArgsConstructor
public class BookingLaundryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    private LaundryItemCatalog item;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_type", nullable = false)
    private ColorType colorType;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice; // Snapshot price at booking time

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice; // quantity * unitPrice

    public enum ColorType {
        WHITE, COLORED
    }
}