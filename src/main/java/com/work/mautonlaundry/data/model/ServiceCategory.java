package com.work.mautonlaundry.data.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Table(name = "service_category")
@Data
public class ServiceCategory {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
}
