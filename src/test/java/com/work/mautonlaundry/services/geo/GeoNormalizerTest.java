package com.work.mautonlaundry.services.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the contract between GeoNormalizer and the normalized_name values
 * seeded by V14__seed_states_lgas_regions.sql. A drift between the two produces
 * no error -- just an empty result and a silently broken scope layer -- so the
 * expected values below are the literal strings in that migration.
 */
class GeoNormalizerTest {

    // ---- must mirror the migration's lowercase + [a-z0-9] rule ----

    @Test
    void normalize_matchesTheSeededForms() {
        assertThat(GeoNormalizer.normalize("Lagos")).isEqualTo("lagos");
        assertThat(GeoNormalizer.normalize("Oshodi-Isolo")).isEqualTo("oshodiisolo");
        assertThat(GeoNormalizer.normalize("Ajeromi-Ifelodun")).isEqualTo("ajeromiifelodun");
        assertThat(GeoNormalizer.normalize("Federal Capital Territory")).isEqualTo("federalcapitalterritory");
        assertThat(GeoNormalizer.normalize("Municipal Area Council")).isEqualTo("municipalareacouncil");
    }

    @Test
    void normalize_collapsesTheSeparatorsGoogleDisagreesOn() {
        // Google returns "Oshodi/Isolo"; the official register says "Oshodi-Isolo".
        assertThat(GeoNormalizer.normalize("Oshodi/Isolo"))
                .isEqualTo(GeoNormalizer.normalize("Oshodi-Isolo"));
        assertThat(GeoNormalizer.normalize("Eti Osa"))
                .isEqualTo(GeoNormalizer.normalize("Eti-Osa"));
        assertThat(GeoNormalizer.normalize("  Ikorodu  ")).isEqualTo("ikorodu");
    }

    @Test
    void normalize_foldsDiacriticsRatherThanDroppingThem() {
        // Dropping them would give "ilif" and never match the seeded "ileife".
        assertThat(GeoNormalizer.normalize("Ilé-Ifè")).isEqualTo("ileife");
        assertThat(GeoNormalizer.normalize("Ile-Ife")).isEqualTo("ileife");
    }

    @Test
    void normalize_handlesNullAndEmpty() {
        assertThat(GeoNormalizer.normalize(null)).isEmpty();
        assertThat(GeoNormalizer.normalize("")).isEmpty();
        assertThat(GeoNormalizer.normalize("---")).isEmpty();
    }

    // ---- state suffix ----

    @Test
    void normalizeState_toleratesTrailingState() {
        assertThat(GeoNormalizer.normalizeState("Lagos State")).isEqualTo("lagos");
        assertThat(GeoNormalizer.normalizeState("Lagos")).isEqualTo("lagos");
        assertThat(GeoNormalizer.normalizeState("Ogun State")).isEqualTo("ogun");
    }

    // ---- LGA suffixes ----

    @Test
    void normalizeLga_toleratesAdministrativeSuffixes() {
        assertThat(GeoNormalizer.normalizeLga("Ikorodu Local Government Area")).isEqualTo("ikorodu");
        assertThat(GeoNormalizer.normalizeLga("Ikorodu LGA")).isEqualTo("ikorodu");
        assertThat(GeoNormalizer.normalizeLga("Ikorodu")).isEqualTo("ikorodu");
    }

    @Test
    void normalizeLga_doesNotMangleFctMunicipalAreaCouncil() {
        // The regression this class exists for: stripping an "Area Council"
        // suffix would yield "municipal", which matches nothing in the seed.
        assertThat(GeoNormalizer.normalizeLga("Municipal Area Council"))
                .isEqualTo("municipalareacouncil");
    }

    @Test
    void normalizeLga_doesNotEraseANameThatIsOnlyASuffix() {
        assertThat(GeoNormalizer.normalizeLga("LGA")).isEqualTo("lga");
    }

    @Test
    void normalizeLga_leavesRealLgaNamesAlone() {
        // Surulere exists in both Lagos and Oyo -- the state FK disambiguates,
        // so normalization must not try to.
        assertThat(GeoNormalizer.normalizeLga("Surulere")).isEqualTo("surulere");
        assertThat(GeoNormalizer.normalizeLga("Lagos Island")).isEqualTo("lagosisland");
        assertThat(GeoNormalizer.normalizeLga("Ibeju-Lekki")).isEqualTo("ibejulekki");
    }
}
