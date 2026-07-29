package com.work.mautonlaundry.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails the build if a @PreAuthorize demands a permission that is not seeded.
 *
 * <p><b>Why this exists.</b> Converting the 66 @PreAuthorize annotations from
 * hasRole()/hasAuthority() to permission checks is the highest-risk step in
 * Permission Architecture V2: PermissionEvaluationService is closed-by-default,
 * so an endpoint demanding a permission that was never seeded denies everyone —
 * silently, with no error anywhere, until someone reports they cannot use the
 * admin portal. A typo does exactly the same thing.
 *
 * <p>This turns that class of mistake into a red build. It reads the source
 * rather than the runtime, so it needs no database and no application context,
 * and it runs on every {@code mvn test}.
 *
 * <p>It also lets the conversion proceed in reviewable batches: a half-finished
 * conversion is caught here rather than discovered in production.
 *
 * <p>What it does NOT check: whether the permission is the <em>right</em> one for
 * that endpoint. Only that it exists and is granted. Choosing correctly is still
 * a human judgement.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreAuthorizePermissionGuardTest {

    private static final Path SRC =
            Paths.get("src/main/java/com/work/mautonlaundry");
    private static final Path MIGRATIONS = Paths.get("src/main/resources/db/migration");
    private static final Path V18 =
            Paths.get("src/main/resources/db/migration/V18__seed_endpoint_permissions.sql");
    private static final Path SEED_SERVICE =
            Paths.get("src/main/java/com/work/mautonlaundry/services/DataInitializationService.java");

    /** Matches currentUserHasPermission('X') inside a @PreAuthorize expression. */
    private static final Pattern PERMISSION_CHECK =
            Pattern.compile("currentUserHasPermission\\('([A-Z0-9_]+)'\\)");

    /** Matches hasAuthority('X') — the pre-conversion form, which names a permission too. */
    private static final Pattern HAS_AUTHORITY =
            Pattern.compile("hasAuthority\\('([A-Z0-9_]+)'\\)");

    private static final Pattern PRE_AUTHORIZE =
            Pattern.compile("@PreAuthorize\\(\"([^\"]+)\"\\)");

    /** Matches a seeded permission name in V18's VALUES list. */
    private static final Pattern SEEDED_V18 =
            Pattern.compile("\\('([A-Z0-9_]+)',\\s*'");

    /** Matches the legacy names seeded in DataInitializationService. */
    private static final Pattern SEEDED_LEGACY =
            Pattern.compile("\\{\"([A-Z0-9_]+)\",\\s*\"");

    // ---- the guard ----

    @Test
    void everyPermissionDemandedByAnEndpointIsSeeded() throws IOException {
        Map<String, String> demanded = demandedPermissions();
        Set<String> seeded = seededPermissions();

        Set<String> missing = new TreeSet<>(demanded.keySet());
        missing.removeAll(seeded);

        assertThat(missing)
                .withFailMessage(() -> "These @PreAuthorize permissions are not seeded, so the "
                        + "endpoints demanding them would deny EVERYONE (closed-by-default):\n"
                        + missing.stream()
                        .map(p -> "  " + p + "   <- " + demanded.get(p))
                        .reduce("", (a, b) -> a + b + "\n")
                        + "\nAdd them to V18__seed_endpoint_permissions.sql and grant them.")
                .isEmpty();
    }

    @Test
    void everySeededPermissionIsGrantedToAdmin() throws IOException {
        // ADMIN receives a CROSS JOIN over all permissions in V18, so this holds
        // by construction -- the test pins it so a future edit that narrows that
        // grant cannot silently lock admins out of a converted endpoint.
        String sql = Files.readString(V18, StandardCharsets.UTF_8);
        assertThat(sql)
                .withFailMessage("V18 must grant every permission to ADMIN via CROSS JOIN; "
                        + "narrowing it would deny admins any permission not listed explicitly.")
                .contains("CROSS JOIN permissions p")
                .contains("WHERE r.name = 'ADMIN'");
    }

    @Test
    void riderPermissionsAreGrantedExplicitlyToDeliveryAgent() throws IOException {
        // DataInitializationService only creates DELIVERY_AGENT `if absent` and
        // never re-syncs its permissions, so anything a rider endpoint demands
        // MUST be granted in SQL or every existing rider is denied.
        String sql = Files.readString(V18, StandardCharsets.UTF_8);
        List<String> riderPermissions = List.of(
                "DELIVERY_JOB_VIEW", "DELIVERY_ACCEPT", "HANDOFF_VERIFY",
                "AGENT_PRESENCE_UPDATE", "AGENT_LOCATION_UPDATE", "TRACKING_LOCATION_POST");

        String riderGrant = sql.substring(sql.indexOf("WHERE r.name = 'DELIVERY_AGENT'") - 800,
                sql.indexOf("WHERE r.name = 'DELIVERY_AGENT'"));
        for (String p : riderPermissions) {
            assertThat(riderGrant)
                    .withFailMessage("Rider permission " + p + " is not granted to DELIVERY_AGENT in V18. "
                            + "That role is never re-synced by code, so the grant must be in SQL.")
                    .contains("'" + p + "'");
        }
    }

    @Test
    void noHardcodedRoleNameSurvivesConversion() throws IOException {
        // The Definition of Done (spec §11): "No hardcoded role names in
        // application code (SUPER_ADMIN is seeded data, not a code constant)."
        //
        // The conversion is complete, so this is now asserted rather than
        // reported: a hasRole() reappearing in a @PreAuthorize is a regression
        // that fails the build. Permissions are the enforcement unit; a role
        // check bypasses the module, exclusion and individual-grant dimensions
        // entirely.
        Map<String, String> roleChecks = new LinkedHashMap<>();
        Pattern hasRole = Pattern.compile("hasRole\\('([A-Z_]+)'\\)");
        for (Path f : javaSources()) {
            String src = readCode(f);
            Matcher pa = PRE_AUTHORIZE.matcher(src);
            while (pa.find()) {
                Matcher hr = hasRole.matcher(pa.group(1));
                while (hr.find()) {
                    roleChecks.merge(hr.group(1), f.getFileName().toString(),
                            (a, b) -> a.equals(b) ? a : a + ", " + b);
                }
            }
        }
        assertThat(roleChecks)
                .withFailMessage(() -> "A @PreAuthorize has reintroduced a hardcoded role name, "
                        + "which bypasses modules, exclusions and individual grants:\n"
                        + roleChecks.entrySet().stream()
                        .map(e -> "  hasRole('" + e.getKey() + "')  <- " + e.getValue())
                        .reduce("", (a, b) -> a + b + "\n")
                        + "\nUse @permissionEvaluationService.currentUserHasPermission('X') and seed X in a migration.")
                .isEmpty();
    }

    @Test
    void reportConversionProgress() throws IOException {
        int converted = 0;
        int remaining = 0;
        for (Path f : javaSources()) {
            String src = readCode(f);
            Matcher pa = PRE_AUTHORIZE.matcher(src);
            while (pa.find()) {
                if (pa.group(1).contains("currentUserHasPermission")) {
                    converted++;
                } else {
                    remaining++;
                }
            }
        }
        System.out.printf("%n=== @PreAuthorize conversion: %d converted, %d remaining (%d total) ===%n",
                converted, remaining, converted + remaining);
    }

    // ---- helpers ----

    /** @return permission key -> where it is demanded, for both the converted and legacy forms */
    private Map<String, String> demandedPermissions() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (Path f : javaSources()) {
            String src = readCode(f);
            Matcher pa = PRE_AUTHORIZE.matcher(src);
            while (pa.find()) {
                String expr = pa.group(1);
                for (Pattern p : List.of(PERMISSION_CHECK, HAS_AUTHORITY)) {
                    Matcher m = p.matcher(expr);
                    while (m.find()) {
                        out.merge(m.group(1), f.getFileName().toString(),
                                (a, b) -> a.contains(b) ? a : a + ", " + b);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Every permission name seeded by any migration, or by
     * DataInitializationService.
     *
     * <p>Scans the whole migration directory rather than one file: pinning it to
     * V18 meant the next migration to seed a permission would fail this test
     * despite being correct. The tuple pattern only matches an ALL-CAPS
     * permission VALUES row, so the reference data seeded elsewhere (states,
     * LGAs, aliases -- all lowercase or SELECT-based) is not picked up.
     */
    private Set<String> seededPermissions() throws IOException {
        Set<String> out = new LinkedHashSet<>();

        try (Stream<Path> migrations = Files.walk(MIGRATIONS)) {
            for (Path m : migrations.filter(p -> p.toString().endsWith(".sql")).toList()) {
                Matcher seeded = SEEDED_V18.matcher(Files.readString(m, StandardCharsets.UTF_8));
                while (seeded.find()) {
                    out.add(seeded.group(1));
                }
            }
        }

        // The original 24 live in a String[][] in DataInitializationService.
        Matcher legacy = SEEDED_LEGACY.matcher(Files.readString(SEED_SERVICE, StandardCharsets.UTF_8));
        while (legacy.find()) {
            out.add(legacy.group(1));
        }
        return out;
    }

    private List<Path> javaSources() throws IOException {
        try (Stream<Path> s = Files.walk(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .toList();
        }
    }

    /**
     * Source with comment lines removed.
     *
     * <p>PermissionEvaluationService's own javadoc contains a worked
     * {@code @PreAuthorize(...)} example, which would otherwise be counted as a
     * real annotation and inflate the progress report.
     */
    private String readCode(Path f) throws IOException {
        return Files.readString(f, StandardCharsets.UTF_8).lines()
                .filter(l -> {
                    String t = l.trim();
                    return !t.startsWith("*") && !t.startsWith("//") && !t.startsWith("/*");
                })
                .reduce("", (a, b) -> a + b + "\n");
    }
}
