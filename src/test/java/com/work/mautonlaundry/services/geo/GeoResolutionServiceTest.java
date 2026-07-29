package com.work.mautonlaundry.services.geo;

import com.work.mautonlaundry.data.model.State;
import com.work.mautonlaundry.data.repository.LgaRepository;
import com.work.mautonlaundry.data.repository.StateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The resolver's decision logic. The Google call is mocked; the strings fed in
 * are verbatim responses captured from the live Geocoding API, so these cases
 * are real rather than invented.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeoResolutionServiceTest {

    private static final double LAT = 6.5897;   // Igbogbo, Ikorodu
    private static final double LNG = 3.5064;

    @Mock private GoogleGeocodingClient geocodingClient;
    @Mock private StateRepository stateRepository;
    @Mock private LgaRepository lgaRepository;

    @InjectMocks private GeoResolutionService service;

    private State lagos;

    @BeforeEach
    void setUp() {
        lagos = new State();
        lagos.setId(25);
        lagos.setName("Lagos");
        lagos.setNormalizedName("lagos");
        when(geocodingClient.isConfigured()).thenReturn(true);
        when(stateRepository.findByNormalizedName("lagos")).thenReturn(Optional.of(lagos));
    }

    private void googleReturns(String state, String lga) {
        when(geocodingClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(Optional.of(new GoogleGeocodingClient.GoogleAddress(state, lga)));
    }

    @Test
    void resolvesStateAndLga() {
        googleReturns("Lagos", "Ikorodu");
        when(lgaRepository.resolveIdByStateAndName(25, "ikorodu")).thenReturn(Optional.of(377));

        GeoResolution r = service.resolve(LAT, LNG);

        assertThat(r.status()).isEqualTo(GeoResolution.Status.RESOLVED);
        assertThat(r.stateId()).isEqualTo(25);
        assertThat(r.lgaId()).isEqualTo(377);
        assertThat(r.rawLga()).isEqualTo("Ikorodu");
    }

    @Test
    void normalizesGoogleSpellingBeforeLookup() {
        // Live API returns 'Oshodi/Isolo'; the register says 'Oshodi-Isolo'.
        googleReturns("Lagos", "Oshodi/Isolo");
        when(lgaRepository.resolveIdByStateAndName(25, "oshodiisolo")).thenReturn(Optional.of(390));

        assertThat(service.resolve(LAT, LNG).status()).isEqualTo(GeoResolution.Status.RESOLVED);
        verify(lgaRepository).resolveIdByStateAndName(25, "oshodiisolo");
    }

    @Test
    void toleratesStateSuffixFromGoogle() {
        googleReturns("Lagos State", "Ikorodu");
        when(lgaRepository.resolveIdByStateAndName(anyInt(), anyString())).thenReturn(Optional.of(377));

        assertThat(service.resolve(LAT, LNG).status()).isEqualTo(GeoResolution.Status.RESOLVED);
        verify(stateRepository).findByNormalizedName("lagos");
    }

    @Test
    void unknownLgaFallsBackToStateOnlyRatherThanFailing() {
        // Known state, unrecognised LGA spelling: keep the state so STATE-scoped
        // staff still see the order, and let an alias fix the zone later.
        googleReturns("Lagos", "Some New LGA");
        when(lgaRepository.resolveIdByStateAndName(anyInt(), anyString())).thenReturn(Optional.empty());

        GeoResolution r = service.resolve(LAT, LNG);

        assertThat(r.status()).isEqualTo(GeoResolution.Status.STATE_ONLY);
        assertThat(r.stateId()).isEqualTo(25);
        assertThat(r.lgaId()).isNull();
        assertThat(r.rawLga()).isEqualTo("Some New LGA");  // admin needs this to add the alias
        assertThat(r.hasState()).isTrue();
    }

    @Test
    void missingLgaFromGoogleIsStateOnly() {
        googleReturns("Lagos", null);

        GeoResolution r = service.resolve(LAT, LNG);

        assertThat(r.status()).isEqualTo(GeoResolution.Status.STATE_ONLY);
        assertThat(r.stateId()).isEqualTo(25);
        verify(lgaRepository, never()).resolveIdByStateAndName(anyInt(), anyString());
    }

    @Test
    void unknownStateIsUnresolved() {
        googleReturns("Greater London", "Camden");
        when(stateRepository.findByNormalizedName("greaterlondon")).thenReturn(Optional.empty());

        GeoResolution r = service.resolve(LAT, LNG);

        assertThat(r.status()).isEqualTo(GeoResolution.Status.UNRESOLVED);
        assertThat(r.hasState()).isFalse();
        assertThat(r.rawState()).isEqualTo("Greater London");
    }

    @Test
    void noApiKeyIsDisabledNotError() {
        // Distinct states: DISABLED means nothing was attempted, so the admin
        // queue can say "configure a key" rather than "bad coordinate".
        when(geocodingClient.isConfigured()).thenReturn(false);

        GeoResolution r = service.resolve(LAT, LNG);

        assertThat(r.status()).isEqualTo(GeoResolution.Status.DISABLED);
        verify(geocodingClient, never()).reverseGeocode(anyDouble(), anyDouble());
    }

    @Test
    void providerFailureIsError() {
        when(geocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        assertThat(service.resolve(LAT, LNG).status()).isEqualTo(GeoResolution.Status.ERROR);
    }

    @Test
    void nullCoordinatesAreUnresolvedAndNeverCallGoogle() {
        assertThat(service.resolve(null, LNG).status()).isEqualTo(GeoResolution.Status.UNRESOLVED);
        assertThat(service.resolve(LAT, null).status()).isEqualTo(GeoResolution.Status.UNRESOLVED);
        verify(geocodingClient, never()).reverseGeocode(anyDouble(), anyDouble());
    }

    @Test
    void lgaLookupIsAlwaysScopedToTheResolvedState() {
        // Surulere exists in both Lagos and Oyo; an unscoped lookup would be
        // ambiguous. The state id must always be passed.
        googleReturns("Lagos", "Surulere");
        when(lgaRepository.resolveIdByStateAndName(eq(25), eq("surulere"))).thenReturn(Optional.of(400));

        assertThat(service.resolve(LAT, LNG).lgaId()).isEqualTo(400);
        verify(lgaRepository).resolveIdByStateAndName(25, "surulere");
    }
}
