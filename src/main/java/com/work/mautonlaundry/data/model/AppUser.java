package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;


@Entity
@Getter
@Setter
@Validated
@NoArgsConstructor
@Table(name = "users") // Changed table name back to "users"
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private String id;

    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @Size(min = 6, max = 120) // Increased max size for hashed passwords
    @Column(nullable = false)
    private String password;

    @Column
    private String full_name;

    @Column
    private String phone_number;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean deleted = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean emailVerified = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean isFirstLogin = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean online = true;

    @Column(nullable = false, columnDefinition = "double precision default 5.0")
    private Double rating = 5.0;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Address> addresses;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    // Helper method to check for a role
    public boolean hasRole(String roleName) {
        return this.role != null && this.role.getName().equals(roleName);
    }

    // Method to get the default address
    public Address getDefaultAddress() {
        if (addresses == null || addresses.isEmpty()) return null;
        return addresses.stream()
                .filter(address -> address.getIsDefault() != null && address.getIsDefault())
                .findFirst()
                .orElseGet(() -> getMostRecentlyUsedAddress());
    }
    // Method to get the most recently used address
    public Address getMostRecentlyUsedAddress() {
        if (addresses == null || addresses.isEmpty()) return null;
        return addresses.stream()
                .filter(address -> !Boolean.TRUE.equals(address.getDeleted()))
                .max(Comparator.comparing(
                        address -> address.getLastUsed() == null ? LocalDateTime.MIN : address.getLastUsed()
                ))
                .orElse(null);
    }
}
