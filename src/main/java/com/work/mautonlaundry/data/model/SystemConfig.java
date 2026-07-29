package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Operational knobs (Permission Architecture V2, spec §2.5).
 *
 * <p>Distinct from pricing_config, which holds commercial rates. Spec §5.3 notes
 * system config is not data-scoped: it is globally readable, and editable only
 * with the permission to do so, regardless of where you sit.
 */
@Entity
@Table(name = "system_config")
@Getter
@Setter
@NoArgsConstructor
public class SystemConfig {

    @Id
    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column
    private String description;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
