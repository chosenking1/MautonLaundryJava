package com.work.mautonlaundry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origin-patterns:http://localhost:*,https://localhost:*,http://127.0.0.1:*,https://127.0.0.1:*,https://*.a.free.pinggy.link,http://*.a.free.pinggy.link,http://srkbs-102-89-44-192.a.free.pinggy.link}")
    private List<String> allowedOriginPatterns;

    /** Where this backend is publicly reachable -- the origin of the pages it serves itself. */
    @Value("${app.base-url:}")
    private String appBaseUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(withOwnOrigin(allowedOriginPatterns));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Adds this backend's own origin to the allowed list.
     *
     * <p>The server serves HTML of its own -- the password-reset and email
     * verification pages -- whose scripts POST back to /api/auth/**. Browsers
     * attach an Origin header to every POST, including same-origin ones, and
     * Spring treats any request carrying Origin as a CORS request. With only the
     * frontends configured, the backend rejected its own page with
     * "Invalid CORS request", so the reset form could never submit.
     *
     * <p>Derived from app.base-url rather than asking operators to repeat the
     * host in CORS_ALLOWED_ORIGINS: the two can never drift, and staging, prod
     * and local all get it without extra configuration.
     */
    private List<String> withOwnOrigin(List<String> configured) {
        List<String> origins = new ArrayList<>(configured == null ? List.of() : configured);
        String own = originOf(appBaseUrl);
        if (own != null && !origins.contains(own)) {
            origins.add(own);
        }
        return origins;
    }

    /** scheme://host[:port] from a base URL, or null when unset/unparseable. */
    private static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getPort() == -1
                    ? uri.getScheme() + "://" + uri.getHost()
                    : uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
