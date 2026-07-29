package com.work.mautonlaundry.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Set;

@Entity
@Table(name = "permissions")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "roles")
@ToString(exclude = "roles")
public class Permission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name; // Permission name like USER_READ, USER_CREATE, etc. The V2 spec's permission_key.

    // resource/action/endpoint/method describe the legacy endpoint-binding model
    // that DynamicPermissionService matches a request URI+verb against. V2
    // permissions are atomic actions (PAYOUT_SETTLE has no URI and no verb), so
    // all four are nullable as of V10__permission_core.sql.
    @Column
    private String resource; // Resource type like USER, BOOKING, etc.

    @Column
    private String action; // Action type like CREATE, READ, UPDATE, DELETE

    @Column
    private String endpoint;

    @Column
    private String method; // GET, POST, PUT, DELETE, etc.

    @Column
    private String description;

    @Column(length = 100)
    private String category; // V2 spec §2.7 grouping; backfilled from resource
    
    @ManyToMany(mappedBy = "permissions")
    @JsonIgnore
    private Set<Role> roles;
    
    @Column(nullable = false)
    private Boolean active = true;
}
