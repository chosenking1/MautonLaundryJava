package com.work.mautonlaundry.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The backend serves its own HTML (password reset, email verification) whose
 * scripts POST back to /api/auth/**. Browsers attach Origin to every POST, so
 * Spring treats those as CORS requests -- meaning the backend must allow its own
 * origin or it rejects its own pages with "Invalid CORS request".
 */
class CorsConfigTest {

    private CorsConfiguration configure(List<String> configured, String baseUrl) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOriginPatterns", configured);
        ReflectionTestUtils.setField(config, "appBaseUrl", baseUrl);
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        return source.getCorsConfigurations().get("/**");
    }

    @Test
    void ownOriginIsAllowed_soTheResetPageCanPostToItself() {
        CorsConfiguration cors = configure(
                List.of("https://admin.imototo.com.ng"),
                "https://staging-api.imototo.com.ng");

        assertThat(cors.getAllowedOriginPatterns())
                .contains("https://staging-api.imototo.com.ng")
                .contains("https://admin.imototo.com.ng");
    }

    @Test
    void portIsPreserved_soLocalDevelopmentMatches() {
        CorsConfiguration cors = configure(List.of(), "http://localhost:8079");
        assertThat(cors.getAllowedOriginPatterns()).contains("http://localhost:8079");
    }

    @Test
    void pathOnTheBaseUrlIsStripped_anOriginIsSchemeHostPortOnly() {
        CorsConfiguration cors = configure(List.of(), "https://api.imototo.com.ng/");
        assertThat(cors.getAllowedOriginPatterns()).contains("https://api.imototo.com.ng");
    }

    @Test
    void blankOrUnparseableBaseUrlAddsNothing() {
        assertThat(configure(List.of("https://a.test"), "").getAllowedOriginPatterns())
                .containsExactly("https://a.test");
        assertThat(configure(List.of("https://a.test"), "not a url").getAllowedOriginPatterns())
                .containsExactly("https://a.test");
    }

    @Test
    void ownOriginIsNotDuplicatedWhenAlreadyConfigured() {
        CorsConfiguration cors = configure(
                List.of("https://api.imototo.com.ng"),
                "https://api.imototo.com.ng");
        assertThat(cors.getAllowedOriginPatterns()).containsExactly("https://api.imototo.com.ng");
    }
}
