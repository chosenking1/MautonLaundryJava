package com.work.mautonlaundry.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A Local Government Area -- the ZONE scope level (Permission Architecture V2,
 * spec §5). Nigeria's hierarchy is
 * country -> geopolitical zone -> state -> LGA -> ward/locality,
 * so Igbogbo is a locality inside Ikorodu LGA, Lagos State, South-West.
 *
 * <p>Reference data: 774 rows seeded by V14__seed_states_lgas_regions.sql. The
 * FCT's six entries are Area Councils rather than LGAs, and are stored here
 * under their register names ("Municipal Area Council", not "AMAC" -- that is an
 * alias; see lga_aliases / V16).
 *
 * <p><b>Names are not globally unique.</b> Surulere is an LGA of both Lagos and
 * Oyo; so are Obi, Nasarawa, Bassa, Ifelodun and Irepodun in other pairs. The
 * identity of an LGA is (state, name) -- never name alone.
 */
@Entity
@Table(name = "lgas")
@Getter
@Setter
@NoArgsConstructor
public class Lga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id", nullable = false)
    private State state;

    @Column(nullable = false)
    private String name;

    /** @see State#getNormalizedName() */
    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
