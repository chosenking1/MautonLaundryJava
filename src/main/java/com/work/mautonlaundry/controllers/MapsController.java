package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.services.geo.MapsProxyService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Directions and forward geocoding, proxied so the Google key stays server-side.
 * Sibling of {@link PlacesController}; together they replace every direct Google
 * web-service call the Flutter apps used to make.
 *
 * <p>Authenticated by default (BasicConfiguration ends with
 * anyRequest().authenticated()) and rate-limited by RateLimitFilter, because
 * every call here is billed against our key.
 *
 * <p>Google's JSON is returned verbatim: the clients already parse
 * routes[].legs[].duration and results[].geometry, so only the base URL moves.
 */
@RestController
@RequestMapping("/api/v1/maps")
@RequiredArgsConstructor
public class MapsController {

    private final MapsProxyService mapsProxyService;

    /**
     * Replaces the direct Directions calls in the customer app's
     * delivery_tracking_screen (ETA) and the ops app's delivery_navigation_page
     * (rider route).
     *
     * @param origin      "lat,lng"
     * @param destination "lat,lng"
     */
    @GetMapping(value = "/directions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> directions(@RequestParam @NotBlank String origin,
                                             @RequestParam @NotBlank String destination,
                                             @RequestParam(required = false, defaultValue = "driving") String mode) {
        return mapsProxyService.directions(origin, destination, mode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503)
                        .body("{\"status\":\"UNAVAILABLE\",\"message\":\"Directions are unavailable right now.\"}"));
    }

    /** Replaces the customer app's GeocodingHelper. Nigeria-biased server-side. */
    @GetMapping(value = "/geocode", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> geocode(@RequestParam @NotBlank String address) {
        return mapsProxyService.geocode(address)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503)
                        .body("{\"status\":\"UNAVAILABLE\",\"message\":\"Address lookup is unavailable right now.\"}"));
    }
}
