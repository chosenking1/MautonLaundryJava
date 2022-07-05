package com.work.mautonlaundry.data.model;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;


    @Column(unique = true)
    private String userName;

//    @Email
    @Column(unique = true)
    private String email;





}
