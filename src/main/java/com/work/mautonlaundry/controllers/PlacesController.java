package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.services.geo.PlacesProxyService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Address search, proxied so the Google key stays on the server.
 *
 * <p>These endpoints are authenticated: BasicConfiguration ends with
 * {@code anyRequest().authenticated()} and nothing here is added to the permitAll
 * list, so an anonymous caller cannot spend the key's quota. RateLimitFilter
 * additionally caps /api/v1/places per caller, because Places is billed per
 * request and a logged-in client looping on keystrokes is the likeliest way to
 * run up a bill by accident.
 *
 * <p>Replaces the direct Google calls in map_picker_screen.dart:169/:191 and
 * ops profile_page.dart:888. Responses are Google's JSON verbatim, so the
 * clients' existing parsing is unchanged -- only the base URL moves.
 */
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlacesController {

    private final PlacesProxyService placesProxyService;

    /**
     * @param sessionToken optional; groups keystrokes plus the follow-up details
     *                     call into a single billable Places session. Clients
     *                     should generate one per address search and reuse it.
     */
    @GetMapping(value = "/autocomplete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> autocomplete(@RequestParam @NotBlank String input,
                                               @RequestParam(required = false) String sessionToken) {
        return placesProxyService.autocomplete(input, sessionToken)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503)
                        .body("{\"message\":\"Address search is unavailable right now. "
                                + "You can still drop a pin on the map.\"}"));
    }

    @GetMapping(value = "/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> details(@RequestParam @NotBlank String placeId,
                                          @RequestParam(required = false) String sessionToken) {
        return placesProxyService.details(placeId, sessionToken)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503)
                        .body("{\"message\":\"Could not load that address. Please try again.\"}"));
    }
}
