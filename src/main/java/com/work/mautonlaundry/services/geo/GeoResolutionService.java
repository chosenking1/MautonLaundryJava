package com.work.mautonlaundry.services.geo;

import com.work.mautonlaundry.data.model.State;
import com.work.mautonlaundry.data.repository.LgaRepository;
import com.work.mautonlaundry.data.repository.StateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves a coordinate to a canonical (state, LGA) -- the STATE and ZONE scope
 * targets of Permission Architecture V2 (spec §5).
 *
 * <p><b>Server-side on purpose.</b> The Flutter apps already reverse-geocode with
 * the device's native geocoder and could simply post their answer. They must not.
 * An address's state and LGA decide which staff can see the resulting order, so a
 * client-asserted value is a client-asserted access-control decision -- a tampered
 * app could file an order into another state's queue, or hide it from the zone
 * that should handle it. The client supplies coordinates; the server decides what
 * they mean.
 *
 * <p>Failure is never silent-but-permissive: an unrecognised place yields
 * STATE_ONLY or UNRESOLVED and lands in the admin queue. It never guesses.
 */
@Service
@RequiredArgsConstructor
public class GeoResolutionService {

    private static final Logger log = LoggerFactory.getLogger(GeoResolutionService.class);

    private final GoogleGeocodingClient geocodingClient;
    private final StateRepository stateRepository;
    private final LgaRepository lgaRepository;

    public GeoResolution resolve(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return GeoResolution.unresolved(null, null);
        }
        if (!geocodingClient.isConfigured()) {
            // Distinct from ERROR so the admin queue can say "not configured"
            // rather than implying the coordinate is bad.
            return GeoResolution.disabled();
        }

        Optional<GoogleGeocodingClient.GoogleAddress> lookup =
                geocodingClient.reverseGeocode(latitude, longitude);
        if (lookup.isEmpty()) {
            return GeoResolution.error();
        }

        String rawState = lookup.get().state();
        String rawLga = lookup.get().lga();

        Optional<State> state = Optional.ofNullable(rawState)
                .map(GeoNormalizer::normalizeState)
                .filter(n -> !n.isEmpty())
                .flatMap(stateRepository::findByNormalizedName);

        if (state.isEmpty()) {
            // Either Google returned no state, or one we do not stock -- which
            // for a Nigeria-only business means the coordinate is out of country.
            log.debug("Unresolved state '{}' at {},{}", rawState, latitude, longitude);
            return GeoResolution.unresolved(rawState, rawLga);
        }

        Integer stateId = state.get().getId();

        if (rawLga == null || rawLga.isBlank()) {
            return GeoResolution.stateOnly(stateId, rawState, null);
        }

        String normalizedLga = GeoNormalizer.normalizeLga(rawLga);
        Optional<Integer> lgaId = lgaRepository.resolveIdByStateAndName(stateId, normalizedLga);

        if (lgaId.isEmpty()) {
            // Known state, unknown LGA spelling. One alias row fixes this
            // permanently for every future address in that LGA.
            log.info("Unmatched LGA '{}' (normalized '{}') in state '{}' at {},{} -- add an lga_aliases row to resolve",
                    rawLga, normalizedLga, rawState, latitude, longitude);
            return GeoResolution.stateOnly(stateId, rawState, rawLga);
        }

        return GeoResolution.resolved(stateId, lgaId.get(), rawState, rawLga);
    }
}
