package com.work.mautonlaundry.data.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Services {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;


    @Column(unique = true, nullable = false)
    private String service_name;

    @Column(nullable = false)
    private String service_category;

    @Column
    private String service_details;

    @Column(nullable = false)
    private ServiceType type_of_service;

    @Column(nullable = false)
    private int service_price;

    @Column
    private int service_price_white;

    @Column(nullable = false, length = 100)
    private String photos;




}
