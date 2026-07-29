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
 * Server-side proxy for Google Places autocomplete and details.
 *
 * <p><b>Why this exists.</b> The apps currently call the Places web service
 * directly from the device, using the Android Maps SDK key
 * (map_picker_screen.dart:169 and :191; ops profile_page.dart:888). That is
 * unsound in both directions:
 *
 * <ul>
 *   <li>An Android key is restricted by package name + SHA-1, and that
 *       restriction cannot apply to a web-service call. So the key must be left
 *       unrestricted for those calls to work at all -- and an unrestricted key
 *       shipped inside an APK can be extracted with unzip and spent by anyone.</li>
 *   <li>Restricting the key properly (as it should be, for the Maps SDK) breaks
 *       the in-app Places calls.</li>
 * </ul>
 *
 * <p>Routing Places through the backend resolves the contradiction: the Android
 * key goes back to being package/SHA-1 restricted and renders maps only, while a
 * single IP-restricted server key lives on the VPS and never leaves it. The apps
 * call these endpoints instead of Google, so the spendable credential is not on
 * the phone at all.
 *
 * <p>The response body is passed through verbatim so the clients' existing JSON
 * parsing keeps working -- this is a transport change, not a contract change.
 */
@Service
public class PlacesProxyService {

    private static final Logger log = LoggerFactory.getLogger(PlacesProxyService.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String AUTOCOMPLETE = "https://maps.googleapis.com/maps/api/place/autocomplete/json";
    private static final String DETAILS = "https://maps.googleapis.com/maps/api/place/details/json";

    /** Nigeria-only, matching what the clients already send. */
    private static final String COUNTRY_COMPONENT = "country:ng";

    /**
     * Mirrors the fields map_picker_screen.dart already requests. Kept explicit
     * because Places bills per field group -- widening this costs money.
     */
    private static final String DETAILS_FIELDS = "geometry,formatted_address,address_components";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final String apiKey;

    public PlacesProxyService(@Value("${app.geo.google-api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Optional<String> autocomplete(String input, String sessionToken) {
        if (!isConfigured() || input == null || input.isBlank()) {
            return Optional.empty();
        }
        StringBuilder url = new StringBuilder(AUTOCOMPLETE)
                .append("?input=").append(enc(input))
                .append("&components=").append(enc(COUNTRY_COMPONENT))
                .append("&key=").append(enc(apiKey));
        // A session token groups autocomplete keystrokes with the details call
        // into one billable session instead of many. Optional, and cheaper.
        if (sessionToken != null && !sessionToken.isBlank()) {
            url.append("&sessiontoken=").append(enc(sessionToken));
        }
        return get(url.toString(), "autocomplete");
    }

    public Optional<String> details(String placeId, String sessionToken) {
        if (!isConfigured() || placeId == null || placeId.isBlank()) {
            return Optional.empty();
        }
        StringBuilder url = new StringBuilder(DETAILS)
                .append("?place_id=").append(enc(placeId))
                .append("&fields=").append(enc(DETAILS_FIELDS))
                .append("&key=").append(enc(apiKey));
        if (sessionToken != null && !sessionToken.isBlank()) {
            url.append("&sessiontoken=").append(enc(sessionToken));
        }
        return get(url.toString(), "details");
    }

    private Optional<String> get(String url, String what) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Places {} HTTP {}", what, response.statusCode());
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Places {} failed: {}", what, e.getMessage());
            return Optional.empty();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
