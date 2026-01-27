package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "laundryman_assignments")
@Getter
@Setter
@NoArgsConstructor
public class LaundrymanAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne
    @JoinColumn(name = "laundryman_id", nullable = false)
    private AppUser laundryman;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum AssignmentStatus {
        ASSIGNED, RECEIVED, COMPLETED
    }
}