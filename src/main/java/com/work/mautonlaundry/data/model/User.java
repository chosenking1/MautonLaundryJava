package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;




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
}
