package com.work.mautonlaundry.services.geo;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Turns a place name into the canonical form used to match against the states
 * and lgas reference tables (Permission Architecture V2, spec §5).
 *
 * <p><b>This must stay in lockstep with V14__seed_states_lgas_regions.sql.</b>
 * The seed wrote normalized_name with lowercase + alphanumerics-only. If this
 * class and that migration ever disagree, lookups do not throw -- they silently
 * return nothing, every address drops into the unresolved queue, and scope
 * quietly stops working. There is no loud failure to alert you.
 *
 * <p>Diacritics are folded to their base letter first. The seeded data is pure
 * ASCII, so folding is a no-op against it and the forms stay identical; but
 * Google may return "Ilé-Ifè", and folding lets that reach the seeded "Ile-Ife".
 * Stripping the accented characters instead (which a bare [^a-z0-9] filter does)
 * would yield "ilif" and miss.
 */
public final class GeoNormalizer {

    private GeoNormalizer() {
    }

    /**
     * Suffixes safe to strip from an LGA name. Verified against the seed: no LGA
     * normalizes to a value ending in any of these.
     *
     * <p>"areacouncil" is deliberately absent. The FCT's "Municipal Area Council"
     * normalizes to "municipalareacouncil", and stripping the suffix would leave
     * "municipal", which matches nothing. FCT uses Area Councils rather than
     * LGAs, and that is the seeded name.
     */
    private static final List<String> LGA_SUFFIXES = List.of(
            "localgovernmentarea",
            "localgovernmentcouncil",
            "localgovernment",
            "lga"
    );

    /** Google returns administrative_area_level_1 as "Lagos" or sometimes "Lagos State". */
    private static final String STATE_SUFFIX = "state";

    /**
     * The canonical form. Mirrors the migration exactly:
     * lowercase, then keep only [a-z0-9].
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String folded = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    /** Canonical form of a state name, tolerating a trailing "State". */
    public static String normalizeState(String raw) {
        String n = normalize(raw);
        if (n.length() > STATE_SUFFIX.length() && n.endsWith(STATE_SUFFIX)) {
            return n.substring(0, n.length() - STATE_SUFFIX.length());
        }
        return n;
    }

    /** Canonical form of an LGA name, tolerating administrative suffixes. */
    public static String normalizeLga(String raw) {
        String n = normalize(raw);
        for (String suffix : LGA_SUFFIXES) {
            // The length guard stops a name that IS the suffix from becoming "".
            if (n.length() > suffix.length() && n.endsWith(suffix)) {
                return n.substring(0, n.length() - suffix.length());
            }
        }
        return n;
    }
}
