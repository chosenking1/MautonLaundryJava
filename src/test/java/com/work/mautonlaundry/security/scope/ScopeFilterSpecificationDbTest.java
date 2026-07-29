package com.work.mautonlaundry.security.scope;

import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.enums.ScopeLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.domain.Specification;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes ScopeFilterService's Specifications through a real Hibernate
 * EntityManager against real Postgres — the one thing the mocked tests cannot do.
 *
 * <p><b>Why this exists.</b> {@link ScopeFilterServiceTest} mocks the repository,
 * so it proves the scope <em>decision</em> and never the filtering. The Criteria
 * API's translation into SQL — the join to pickup_address, the EXISTS on
 * laundryman_assignments, the IN over a state set — is unverified until it runs
 * against a database. A wrong predicate does not throw; it silently returns the
 * wrong rows, which for a security boundary means one region's orders showing up
 * in another's list.
 *
 * <p><b>No Spring context.</b> Spring Boot 4 moved @DataJpaTest out of
 * spring-boot-test-autoconfigure (which now ships only the jdbc and json
 * slices), and @SpringBootTest would boot the whole application — including
 * DataInitializationService, which would seed the dev database as a side effect
 * of running a test. Hibernate is bootstrapped directly instead, and the
 * Specification's toPredicate is invoked by hand. That is closer to the metal
 * and touches nothing it should not.
 *
 * <p><b>Requires Postgres on localhost:5432.</b> Safe: /src/test is gitignored,
 * so this never reaches the repo or the Docker build and only runs locally.
 *
 * <p>Schema and fixtures are created <b>inside a transaction that is always
 * rolled back</b> — Postgres DDL is transactional, and hbm2ddl is off, so the
 * dev database is left exactly as found.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScopeFilterSpecificationDbTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/imototo";
    private static final String USER = "diary_app_user";
    private static final String PASSWORD = "Playme2you";

    private static SessionFactory sessionFactory;
    private EntityManager em;

    /** specificationFor() is pure given a ScopeContext; its collaborators are unused on this path. */
    private final ScopeFilterService service = new ScopeFilterService(null, null, null, null);

    private int lagos;
    private int oyo;
    private int ikorodu;
    private int etiOsa;

    @BeforeAll
    void bootstrapHibernate() {
        Configuration cfg = new Configuration();
        cfg.setProperty("hibernate.connection.url", URL);
        cfg.setProperty("hibernate.connection.username", USER);
        cfg.setProperty("hibernate.connection.password", PASSWORD);
        cfg.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        cfg.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // Never let Hibernate touch the dev schema: auto-creating `states` here
        // would leave a constraint-less table that V13's CREATE TABLE collides
        // with later.
        cfg.setProperty("hibernate.hbm2ddl.auto", "none");
        cfg.setProperty("hibernate.show_sql", "false");

        // Booking's mappings reach AppUser, Address, BookingServiceItem and more,
        // so every entity is registered rather than hand-listing a set that would
        // rot the moment a relation is added.
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.findCandidateComponents("com.work.mautonlaundry.data.model").forEach(bd -> {
            try {
                cfg.addAnnotatedClass(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        });

        sessionFactory = cfg.buildSessionFactory();
    }

    @AfterAll
    void closeHibernate() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        em = sessionFactory.createEntityManager();
        em.getTransaction().begin();

        applyMigration("db/migration/V13__geo_reference.sql");

        // Only the reference rows this test needs; V14's full 774-row seed is
        // irrelevant to the predicate under test.
        exec("INSERT INTO states (name, normalized_name) VALUES ('Lagos','lagos'), ('Oyo','oyo')");
        lagos = refId("SELECT id FROM states WHERE normalized_name='lagos'");
        oyo = refId("SELECT id FROM states WHERE normalized_name='oyo'");
        exec("INSERT INTO lgas (state_id, name, normalized_name) VALUES "
                + "(" + lagos + ",'Ikorodu','ikorodu'), (" + lagos + ",'Eti Osa','etiosa')");
        ikorodu = refId("SELECT id FROM lgas WHERE normalized_name='ikorodu'");
        etiOsa = refId("SELECT id FROM lgas WHERE normalized_name='etiosa'");

        exec("INSERT INTO roles (name, description) VALUES ('T_SCOPE_IT','t')");
        long roleId = ((Number) em.createNativeQuery("SELECT id FROM roles WHERE name='T_SCOPE_IT'")
                .getSingleResult()).longValue();

        for (String u : List.of("it-cust", "it-spec")) {
            exec("INSERT INTO users (id,email,password,role_id,deleted,email_verified,is_first_login,online,rating) "
                    + "VALUES ('" + u + "','" + u + "@it.test','x'," + roleId + ",false,true,false,true,5.0)");
        }

        // a-nul is the important one: an address whose geography never resolved.
        exec("INSERT INTO address (id,user_id,state_id,lga_id) VALUES ('a-iko','it-cust'," + lagos + "," + ikorodu + ")");
        exec("INSERT INTO address (id,user_id,state_id,lga_id) VALUES ('a-eti','it-cust'," + lagos + "," + etiOsa + ")");
        exec("INSERT INTO address (id,user_id,state_id,lga_id) VALUES ('a-oyo','it-cust'," + oyo + ",NULL)");
        exec("INSERT INTO address (id,user_id,state_id,lga_id) VALUES ('a-nul','it-cust',NULL,NULL)");

        for (String[] b : new String[][]{{"it-b-iko", "a-iko"}, {"it-b-eti", "a-eti"},
                {"it-b-oyo", "a-oyo"}, {"it-b-nul", "a-nul"}}) {
            exec("INSERT INTO bookings (id,user_id,pickup_address_id,booking_type,status,total_price,"
                    + "express,deleted,auto_accepted,version,created_at) VALUES ('" + b[0] + "','it-cust','"
                    + b[1] + "','LAUNDRY','CREATED',100,false,false,false,0,NOW())");
        }
        // A soft-deleted Lagos booking: in scope geographically, but must never
        // reach the admin list.
        exec("INSERT INTO bookings (id,user_id,pickup_address_id,booking_type,status,total_price,"
                + "express,deleted,auto_accepted,version,created_at) VALUES ('it-b-del','it-cust','a-iko',"
                + "'LAUNDRY','COMPLETED',100,false,true,false,0,NOW())");
        exec("INSERT INTO laundryman_assignments (booking_id,laundryman_id,status,created_at) "
                + "VALUES ('it-b-eti','it-spec','ACCEPTED',NOW())");

        // Native inserts bypass the persistence context; clear it so the Criteria
        // queries read the database rather than a stale cache.
        em.flush();
        em.clear();
    }

    @AfterEach
    void rollback() {
        if (em != null) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
    }

    // ---- does the Specification actually filter correctly? ----
    //
    // These exercise the scope predicate ALONE, so the soft-deleted it-b-del
    // appears wherever it is geographically in scope. That is the correct
    // separation: scope answers "whose data", not "is this row alive". Callers
    // AND their own notDeleted() on top -- see the adminListShape tests below.

    @Test
    void stateScope_returnsOnlyThatStatesBookings() {
        assertThat(idsMatching(ScopeContext.states(ScopeLevel.STATE, Set.of(lagos))))
                .containsExactlyInAnyOrder("it-b-iko", "it-b-eti", "it-b-del");
    }

    @Test
    void zoneScope_returnsOnlyThatLgasBookings() {
        assertThat(idsMatching(ScopeContext.zone(Set.of(ikorodu))))
                .containsExactlyInAnyOrder("it-b-iko", "it-b-del");
    }

    @Test
    void regionalScope_spansItsMemberStates() {
        // REGIONAL is pre-flattened to a state set, exercising the same IN
        // predicate across more than one state.
        assertThat(idsMatching(ScopeContext.states(ScopeLevel.REGIONAL, Set.of(lagos, oyo))))
                .containsExactlyInAnyOrder("it-b-iko", "it-b-eti", "it-b-oyo", "it-b-del");
    }

    @Test
    void nationalScope_returnsEverythingIncludingUnresolvedAddresses() {
        assertThat(idsMatching(ScopeContext.national()))
                .containsExactlyInAnyOrder("it-b-iko", "it-b-eti", "it-b-oyo", "it-b-nul", "it-b-del");
    }

    @Test
    void deniedScope_returnsNothing() {
        assertThat(idsMatching(ScopeContext.denyAll())).isEmpty();
    }

    @Test
    void unresolvedAddress_isExcludedFromEveryGeographicScope() {
        // The fail-closed case: it-b-nul has state_id NULL and must not appear
        // even when scoping across every state that exists.
        assertThat(idsMatching(ScopeContext.states(ScopeLevel.STATE, Set.of(lagos, oyo))))
                .doesNotContain("it-b-nul");
        assertThat(idsMatching(ScopeContext.zone(Set.of(ikorodu)))).doesNotContain("it-b-nul");
    }

    @Test
    void bookingWithStateButNoLga_isVisibleToStateAndInvisibleToZone() {
        assertThat(idsMatching(ScopeContext.states(ScopeLevel.STATE, Set.of(oyo))))
                .containsExactly("it-b-oyo");
        assertThat(idsMatching(ScopeContext.zone(Set.of(etiOsa)))).containsExactly("it-b-eti");
    }

    @Test
    void specialistScope_returnsOnlyAssignedBookings() {
        assertThat(idsMatching(ScopeContext.specialist("it-spec"))).containsExactly("it-b-eti");
    }

    @Test
    void specialistWithNoAssignments_seesNothing() {
        assertThat(idsMatching(ScopeContext.specialist("it-cust"))).isEmpty();
    }

    @Test
    void specialistScope_doesNotDuplicateABookingWithSeveralAssignments() {
        // The reason SPECIALIST uses EXISTS rather than a join: a second
        // assignment row must not make the booking appear twice.
        exec("INSERT INTO laundryman_assignments (booking_id,laundryman_id,status,created_at) "
                + "VALUES ('it-b-eti','it-spec','OFFERED',NOW())");
        em.flush();
        em.clear();

        assertThat(idsMatching(ScopeContext.specialist("it-spec"))).containsExactly("it-b-eti");
    }

    @Test
    void scopeComposesWithOtherCriteria() {
        // Scope is AND-ed onto a caller's own filter -- how a repository will use it.
        Specification<Booking> scope =
                service.specificationFor(Booking.class, ScopeContext.states(ScopeLevel.STATE, Set.of(lagos)));
        Specification<Booking> alsoIkorodu = (root, q, cb) ->
                cb.equal(root.get("pickupAddress").get("lgaId"), ikorodu);

        assertThat(runIds(scope.and(alsoIkorodu)))
                .containsExactlyInAnyOrder("it-b-iko", "it-b-del");
    }

    // ---- the exact query shape BookingService.getAllBookings now builds ----

    @Test
    void adminListShape_stateScopedAdminSeesOnlyTheirStateAndNoDeletedRows() {
        // Mirrors BookingService: getScope(Booking.class).and(notDeleted()).
        // it-b-del is a Lagos booking, so only the deleted() clause keeps it out.
        Specification<Booking> spec =
                service.specificationFor(Booking.class, ScopeContext.states(ScopeLevel.STATE, Set.of(lagos)))
                        .and(notDeleted());

        assertThat(runIds(spec)).containsExactlyInAnyOrder("it-b-iko", "it-b-eti");
    }

    @Test
    void adminListShape_nationalAdminSeesEverythingUndeleted() {
        // The seeded state for every admin (V17): behaviour identical to the
        // findByDeletedFalse this replaced.
        Specification<Booking> spec =
                service.specificationFor(Booking.class, ScopeContext.national()).and(notDeleted());

        assertThat(runIds(spec))
                .containsExactlyInAnyOrder("it-b-iko", "it-b-eti", "it-b-oyo", "it-b-nul")
                .doesNotContain("it-b-del");
    }

    @Test
    void adminListShape_adminWithNoScopeRowSeesNothing() {
        // Fails closed: a scope-less admin gets an empty list, not every order.
        Specification<Booking> spec =
                service.specificationFor(Booking.class, ScopeContext.denyAll()).and(notDeleted());

        assertThat(runIds(spec)).isEmpty();
    }

    /** Mirrors BookingService.notDeleted(). */
    private static Specification<Booking> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    // ---- helpers ----

    private List<String> idsMatching(ScopeContext scope) {
        return runIds(service.specificationFor(Booking.class, scope));
    }

    /**
     * Invokes the Specification exactly as Spring Data would, then runs it.
     *
     * <p>Selects the id rather than the entity. Two reasons: the predicate is
     * what is under test, so hydrating a Booking adds nothing; and Booking has an
     * eager @OneToOne to Payment, so selecting the entity would also load the
     * payment row — which fails against this database, whose `payment` table
     * predates the entity's checkoutUrl field. That staleness is real but has
     * nothing to do with scope filtering, and a predicate test should not depend
     * on it.
     */
    private List<String> runIds(Specification<Booking> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> cq = cb.createQuery(String.class);
        Root<Booking> root = cq.from(Booking.class);
        Predicate predicate = spec.toPredicate(root, cq, cb);
        cq.select(root.get("id")).where(predicate);
        return em.createQuery(cq).getResultList().stream()
                // The dev database has bookings of its own; assert only on ours.
                .filter(id -> id.startsWith("it-b-"))
                .collect(Collectors.toList());
    }

    private void exec(String sql) {
        em.createNativeQuery(sql).executeUpdate();
    }

    private int refId(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).intValue();
    }

    /** Applies a migration inside the test transaction, so it rolls back with it. */
    private void applyMigration(String path) {
        String sql;
        try {
            sql = new String(new ClassPathResource(path).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
        String stripped = sql.lines()
                .filter(l -> !l.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
        for (String statement : stripped.split(";")) {
            if (!statement.isBlank()) {
                exec(statement);
            }
        }
    }
}
