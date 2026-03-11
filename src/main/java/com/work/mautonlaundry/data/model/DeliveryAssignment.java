package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentPhase;
import com.work.mautonlaundry.data.model.enums.DeliveryAssignmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_assignments")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne
    @JoinColumn(name = "delivery_agent_id", nullable = false)
    private AppUser deliveryAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryAssignmentStatus status = DeliveryAssignmentStatus.OFFERED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryAssignmentPhase phase;

    @Column(name = "offered_at", nullable = false)
    private LocalDateTime offeredAt = LocalDateTime.now();

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(precision = 10, scale = 8)
    private BigDecimal currentLatitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal currentLongitude;

    @Column(name = "last_location_update")
    private LocalDateTime lastLocationUpdate;

    @Column(name = "bearing")
    private Integer bearing;

    @Column(name = "speed")
    private Double speed;
}
