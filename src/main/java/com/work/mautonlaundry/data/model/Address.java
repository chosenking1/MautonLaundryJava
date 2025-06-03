package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Table(name = "address")
@Data
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String street;
    private Integer street_number;
    private String city;
    private String state;
    private String zip;
    private String country;
    private Double latitude;
    private Double longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column
    private Boolean isDefault; // To mark the default address
    @Column
    private Boolean deleted; // To mark if the address is deleted
    @Column
    private LocalDateTime lastUsed; // To track the last used time

    @CreationTimestamp
    @Column
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column
    private LocalDateTime modifiedAt;
}
