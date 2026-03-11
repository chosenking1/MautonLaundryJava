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
    private String name; // Permission name like USER_READ, USER_CREATE, etc.
    
    @Column(nullable = false)
    private String resource; // Resource type like USER, BOOKING, etc.
    
    @Column(nullable = false)
    private String action; // Action type like CREATE, READ, UPDATE, DELETE
    
    @Column(nullable = false)
    private String endpoint;
    
    @Column(nullable = false)
    private String method; // GET, POST, PUT, DELETE, etc.
    
    @Column
    private String description;
    
    @ManyToMany(mappedBy = "permissions")
    @JsonIgnore
    private Set<Role> roles;
    
    @Column(nullable = false)
    private Boolean active = true;
}
