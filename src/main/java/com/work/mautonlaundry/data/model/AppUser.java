package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

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

    @Column(nullable = false)
    private String address; // Note: This might be redundant with the addresses list, but keeping for now

    @Column
    private String phone_number;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean deleted = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean emailVerified = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Address> addresses;

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
                .filter(address -> !address.getDeleted())
                .max((a1, a2) -> a1.getLastUsed().compareTo(a2.getLastUsed()))
                .orElse(null);
    }
}
