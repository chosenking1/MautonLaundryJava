package com.work.mautonlaundry.data.model;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Entity
@Getter
@Setter
@Validated
@NoArgsConstructor

public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

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


}
