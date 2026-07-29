package com.work.mautonlaundry.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    /**
     * Free text, as supplied by the client. Retained for display and for the
     * legacy rows that predate canonical geography, but NOT used for scoping --
     * "Lagos" / "lagos" / "Lagos State" are all possible here. Scope reads
     * stateId instead.
     */
    private String state;
    private String zip;
    private String country;
    private Double latitude;
    private Double longitude;

    // --- canonical geography (Permission Architecture V2, spec §5) ---
    // Resolved server-side from latitude/longitude; never accepted from the
    // client, because these decide who can see the resulting order.

    /** FK to states. Null until resolved, or if the coordinate is unresolvable. */
    @Column(name = "state_id")
    private Integer stateId;

    /**
     * FK to lgas -- the ZONE scope target. Null is normal and not an error: the
     * state can resolve while the LGA does not (GeoResolution.Status.STATE_ONLY).
     */
    @Column(name = "lga_id")
    private Integer lgaId;

    /**
     * When resolution was last attempted. NULL means never tried, which is what
     * the backfill selects on -- it distinguishes "not yet attempted" from
     * "attempted and genuinely unresolvable", so the job cannot loop forever on
     * the same unresolvable rows.
     */
    @Column(name = "geo_resolved_at")
    private LocalDateTime geoResolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @JsonIgnore
    private AppUser user;

    @Column
    private Boolean isDefault;
    @Column
    private Boolean deleted;
    @Column
    private LocalDateTime lastUsed;

    @CreationTimestamp
    @Column
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column
    private LocalDateTime modifiedAt;
}
