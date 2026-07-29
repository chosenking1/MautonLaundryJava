package com.work.mautonlaundry.services.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Server-side proxy for the remaining Google web services the apps call:
 * Directions and forward Geocoding.
 *
 * <p>Companion to {@link PlacesProxyService}, for the same reason. Between them
 * the two cover every direct Google call the clients used to make:
 *
 * <ul>
 *   <li>Places  -- user map_picker_screen, ops profile_page</li>
 *   <li>Geocoding -- user geocoding_helper (typed address to coordinates)</li>
 *   <li>Directions -- user delivery_tracking_screen (customer ETA),
 *       ops delivery_navigation_page (rider route)</li>
 * </ul>
 *
 * <p>Until all of them route through here the Android key has to stay
 * unrestricted, because a package/SHA-1 restriction does not apply to
 * web-service calls -- and an unrestricted key inside a shipped APK is a
 * spendable credential on every user's phone. Once the clients are converted,
 * the app key can be locked down to the Maps SDK and this server key (IP
 * restricted) becomes the only thing that can spend money.
 *
 * <p>Responses pass through verbatim so the existing client parsing is
 * unchanged: this is a transport move, not a contract change.
 */
@Service
public class MapsProxyService {

    private static final Logger log = LoggerFactory.getLogger(MapsProxyService.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String DIRECTIONS = "https://maps.googleapis.com/maps/api/directions/json";
    private static final String GEOCODE = "https://maps.googleapis.com/maps/api/geocode/json";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final String apiKey;

    public MapsProxyService(@Value("${app.geo.google-api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * A driving route between two coordinates.
     *
     * @param origin      "lat,lng"
     * @param destination "lat,lng"
     * @param mode        travel mode; defaults to driving, which is all the
     *                    clients ask for today
     */
    public Optional<String> directions(String origin, String destination, String mode) {
        if (!isConfigured() || isBlank(origin) || isBlank(destination)) {
            return Optional.empty();
        }
        String url = DIRECTIONS
                + "?origin=" + enc(origin)
                + "&destination=" + enc(destination)
                + "&mode=" + enc(isBlank(mode) ? "driving" : mode);
        return get(url + "&key=" + enc(apiKey), "directions");
    }

    /**
     * Forward-geocodes typed text to coordinates.
     *
     * <p>Biased to Nigeria exactly as geocoding_helper.dart did, so results do
     * not drift now that the call moved server-side.
     */
    public Optional<String> geocode(String address) {
        if (!isConfigured() || isBlank(address)) {
            return Optional.empty();
        }
        String url = GEOCODE
                + "?address=" + enc(address)
                + "&region=ng"
                + "&components=" + enc("country:NG");
        return get(url + "&key=" + enc(apiKey), "geocode");
    }

    private Optional<String> get(String url, String what) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Maps {} HTTP {}", what, response.statusCode());
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Maps {} failed: {}", what, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
