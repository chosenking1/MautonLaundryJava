package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


@Entity
@Getter
@Setter
@NoArgsConstructor
@Table
public class Services {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;


    @Column(unique = true,nullable = false)
    private String service_name;

//    @Column(nullable = false)
//    private String service_category;

    @Column
    private String service_details;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ServiceType type_of_service;

    @Column(nullable = false, length = 100)
    private String photos;

    @Column(nullable = false)
    private Boolean deleted = false;

    @OneToOne(mappedBy = "service", cascade = CascadeType.ALL)
    private ServicePrice servicePrice;

}
