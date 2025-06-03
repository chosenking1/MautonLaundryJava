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

public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private String id;

    @Email
    @Column(unique = true)
    private String email;

    @Size(min = 6, max = 15)
    private String password;

    @Column
    private String full_name;

    @Column(nullable = false)
    private String address;

    @Column
    private String phone_number;

    @Column
    private Boolean deleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole userRole;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Address> addresses;

    // Method to get the default address
    public Address getDefaultAddress() {
        return addresses.stream()
                .filter(address -> address.getIsDefault() != null && address.getIsDefault())
                .findFirst()
                .orElseGet(() -> getMostRecentlyUsedAddress());
    }
    // Method to get the most recently used address
    public Address getMostRecentlyUsedAddress() {
        return addresses.stream()
                .filter(address -> !address.getDeleted())
                .max((a1, a2) -> a1.getLastUsed().compareTo(a2.getLastUsed()))
                .orElse(null);
    }
}
