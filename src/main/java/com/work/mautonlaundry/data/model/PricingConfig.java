package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_config")
@Getter
@Setter
@NoArgsConstructor
public class PricingConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true)
    private String key;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String value; // JSON string for flexibility

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ConfigKey {
        EXPRESS_FEE("EXPRESS_FEE"),
        DELIVERY_FEE("DELIVERY_FEE"),
        FREE_DELIVERY_THRESHOLD("FREE_DELIVERY_THRESHOLD");

        private final String value;

        ConfigKey(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}