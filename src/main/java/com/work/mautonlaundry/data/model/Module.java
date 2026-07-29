package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A named bundle of permissions (Permission Architecture V2, spec §3).
 * Maps V11__modules.sql.
 *
 * <p>A module is <b>not</b> an enforcement unit (spec §3.1) -- enforcement always
 * resolves down to individual permissions. Holding a module is shorthand for
 * "grant me every permission in this bundle, minus this assignment's
 * exclusions", and PermissionEvaluationService resolves that at read time.
 *
 * <p>Nothing here is hardcoded: modules are created and edited from the admin
 * portal as the company grows (spec §3.4).
 */
@Entity
@Table(name = "modules")
@Getter
@Setter
@NoArgsConstructor
public class Module {

    /** Application-generated, per the V5 convention for VARCHAR(36) ids. */
    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "module_key", nullable = false, unique = true)
    private String moduleKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column
    private String description;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
