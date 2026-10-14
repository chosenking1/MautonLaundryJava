package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.BookingStatus;
import com.work.mautonlaundry.data.model.enums.BookingType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false)
    private BookingType bookingType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CREATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_address_id", nullable = false)
    private Address pickupAddress;

    @Column(name = "return_date")
    private LocalDateTime returnDate;

    /**
     * The day the customer asked to be collected on, or null to be collected
     * now.
     *
     * <p>Null is the old behaviour and stays supported: APKs already in the wild
     * send no schedule, and their bookings must keep dispatching immediately.
     */
    @Column(name = "scheduled_pickup_date")
    private LocalDate scheduledPickupDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_slot_id")
    private PickupSlot pickupSlot;

    /**
     * When the hold was lifted and the booking was offered to laundry partners.
     *
     * <p>This is what makes the release sweep idempotent -- it can run twice, or
     * again after a restart, without re-offering a booking that is already out.
     */
    @Column(name = "pickup_released_at")
    private LocalDateTime pickupReleasedAt;

    /** True while this booking is waiting for its window to open. */
    @Transient
    public boolean isAwaitingScheduledPickup() {
        return scheduledPickupDate != null && pickupReleasedAt == null;
    }

    @Column(nullable = false)
    private Boolean express = false;

    @Column(name = "delivery_fee", precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "discount_code", length = 50)
    private String discountCode;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", precision = 10, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "tracking_number", unique = true)
    private String trackingNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "assignment_timestamp")
    private LocalDateTime assignmentTimestamp;

    @Column(name = "auto_accepted", nullable = false)
    private Boolean autoAccepted = false;

    @Column(name = "delivery_broadcast_at")
    private LocalDateTime deliveryBroadcastAt;

    @Column(name = "customer_early_delivery_opt_in")
    private Boolean customerEarlyDeliveryOptIn;

    @Column(name = "customer_early_delivery_decision_at")
    private LocalDateTime customerEarlyDeliveryDecisionAt;

    @Column(name = "laundry_marked_done_at")
    private LocalDateTime laundryMarkedDoneAt;

    @Column(nullable = false)
    private Boolean deleted = false;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BookingLaundryItem> items;

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL)
    private Payment payment;

}
