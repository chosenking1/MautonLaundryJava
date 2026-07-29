package com.work.mautonlaundry.services.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Thin wrapper over the Google Geocoding API. Isolated from
 * {@link GeoResolutionService} so the matching logic can be tested without a
 * network, and so a provider swap touches one class.
 *
 * <p>The key is read from the environment and must be a SERVER key, IP-restricted
 * to the backend host -- NOT the Android Maps SDK key from keys.properties. That
 * key is package/SHA-1 scoped in principle and ships inside the APK, so treating
 * it as a server credential would put a spendable key on every user's phone.
 */
@Component
public class GoogleGeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleGeocodingClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String ENDPOINT = "https://maps.googleapis.com/maps/api/geocode/json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final String apiKey;

    public GoogleGeocodingClient(@Value("${app.geo.google-api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Reverse-geocodes a coordinate.
     *
     * @return the parsed administrative components, or empty on any failure --
     *         callers must not distinguish "no result" from "call failed" by
     *         catching exceptions.
     */
    public Optional<GoogleAddress> reverseGeocode(double latitude, double longitude) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            String url = ENDPOINT
                    + "?latlng=" + URLEncoder.encode(latitude + "," + longitude, StandardCharsets.UTF_8)
                    + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Geocoding HTTP {} for {},{}", response.statusCode(), latitude, longitude);
                return Optional.empty();
            }
            return parse(response.body(), latitude, longitude);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Geocoding interrupted for {},{}", latitude, longitude);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Geocoding failed for {},{}: {}", latitude, longitude, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GoogleAddress> parse(String body, double lat, double lng) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        String status = root.path("status").asText();

        if (!"OK".equals(status)) {
            // ZERO_RESULTS is ordinary (sea, unmapped area). REQUEST_DENIED
            // means billing or key restriction and is worth shouting about,
            // because every resolution silently degrades until it is fixed.
            if ("ZERO_RESULTS".equals(status)) {
                log.debug("Geocoding ZERO_RESULTS for {},{}", lat, lng);
            } else {
                log.warn("Geocoding status {} for {},{}: {}", status, lat, lng,
                        root.path("error_message").asText(""));
            }
            return Optional.empty();
        }

        String state = null;
        String lga = null;
        // Google returns results ordered most-specific first; the administrative
        // components repeat across them, so take the first occurrence of each.
        for (JsonNode result : root.path("results")) {
            for (JsonNode component : result.path("address_components")) {
                for (JsonNode type : component.path("types")) {
                    String t = type.asText();
                    if (state == null && "administrative_area_level_1".equals(t)) {
                        state = component.path("long_name").asText(null);
                    } else if (lga == null && "administrative_area_level_2".equals(t)) {
                        lga = component.path("long_name").asText(null);
                    }
                }
            }
            if (state != null && lga != null) {
                break;
            }
        }

        if (state == null && lga == null) {
            return Optional.empty();
        }
        return Optional.of(new GoogleAddress(state, lga));
    }

    /**
     * The two administrative components that matter here.
     * For Nigeria: level_1 is the state ("Lagos"), level_2 is the LGA
     * ("Ikorodu"). Verified against the live API across 10 coordinates spanning
     * Lagos, FCT, Kano, Rivers and Oyo -- all 10 returned level_2.
     */
    public record GoogleAddress(String state, String lga) {
    }
}
