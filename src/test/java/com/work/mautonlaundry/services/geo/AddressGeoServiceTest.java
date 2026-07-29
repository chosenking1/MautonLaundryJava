package com.work.mautonlaundry.services.geo;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How each resolution outcome is persisted. This mapping is the difference
 * between an order reaching the right zone head and vanishing from their queue,
 * and between a retryable blip and a row written off permanently.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressGeoServiceTest {

    private static final String ID = "addr-1";

    @Mock private AddressRepository addressRepository;
    @Mock private GeoResolutionService geoResolutionService;

    @InjectMocks private AddressGeoService service;

    private Address address;

    @BeforeEach
    void setUp() {
        address = new Address();
        address.setId(ID);
        address.setLatitude(6.5897);
        address.setLongitude(3.5064);
        when(addressRepository.findById(ID)).thenReturn(Optional.of(address));
        when(addressRepository.countUnresolvedWithCoordinates()).thenReturn(0L);
    }

    private void resolverReturns(GeoResolution r) {
        when(geoResolutionService.resolve(anyDouble(), anyDouble())).thenReturn(r);
    }

    @Test
    void resolved_writesStateLgaAndTimestamp() {
        resolverReturns(GeoResolution.resolved(25, 377, "Lagos", "Ikorodu"));

        service.resolveAndPersist(ID);

        assertThat(address.getStateId()).isEqualTo(25);
        assertThat(address.getLgaId()).isEqualTo(377);
        assertThat(address.getGeoResolvedAt()).isNotNull();
        verify(addressRepository).save(address);
    }

    @Test
    void stateOnly_keepsTheStateSoStateScopedStaffStillSeeIt() {
        resolverReturns(GeoResolution.stateOnly(25, "Lagos", "Some Unknown LGA"));

        service.resolveAndPersist(ID);

        assertThat(address.getStateId()).isEqualTo(25);
        assertThat(address.getLgaId()).isNull();
        // Stamped: attempted, so the backfill will not keep retrying it.
        assertThat(address.getGeoResolvedAt()).isNotNull();
        verify(addressRepository).save(address);
    }

    @Test
    void unresolved_isStampedSoTheBackfillStopsRetrying() {
        resolverReturns(GeoResolution.unresolved("Greater London", "Camden"));

        service.resolveAndPersist(ID);

        assertThat(address.getStateId()).isNull();
        assertThat(address.getLgaId()).isNull();
        assertThat(address.getGeoResolvedAt()).isNotNull();
    }

    @Test
    void disabled_leavesRowEligibleForRetryAndSavesNothing() {
        // No API key is not the address's fault: it must stay retryable.
        resolverReturns(GeoResolution.disabled());

        service.resolveAndPersist(ID);

        assertThat(address.getGeoResolvedAt()).isNull();
        verify(addressRepository, never()).save(any());
    }

    @Test
    void providerError_leavesRowEligibleForRetryAndSavesNothing() {
        resolverReturns(GeoResolution.error());

        service.resolveAndPersist(ID);

        assertThat(address.getGeoResolvedAt()).isNull();
        verify(addressRepository, never()).save(any());
    }

    @Test
    void missingAddress_doesNotThrow() {
        when(addressRepository.findById("gone")).thenReturn(Optional.empty());

        assertThat(service.resolveAndPersist("gone").status())
                .isEqualTo(GeoResolution.Status.UNRESOLVED);
        verify(geoResolutionService, never()).resolve(any(), any());
    }

    @Test
    void asyncListenerSwallowsFailuresSoNothingUpstreamBreaks() {
        when(geoResolutionService.resolve(any(), any())).thenThrow(new RuntimeException("boom"));

        // The address is already committed; this thread has no caller waiting.
        service.onResolutionRequested(new AddressGeoResolutionRequested(ID));
    }

    // ---- backfill ----

    @Test
    void backfill_reportsEachOutcome() {
        Address a = addr("a"), b = addr("b"), c = addr("c");
        when(addressRepository.findUnresolvedWithCoordinates(10)).thenReturn(List.of(a, b, c));
        when(addressRepository.findById("a")).thenReturn(Optional.of(a));
        when(addressRepository.findById("b")).thenReturn(Optional.of(b));
        when(addressRepository.findById("c")).thenReturn(Optional.of(c));
        when(geoResolutionService.resolve(any(), any()))
                .thenReturn(GeoResolution.resolved(25, 377, "Lagos", "Ikorodu"))
                .thenReturn(GeoResolution.stateOnly(25, "Lagos", "Odd"))
                .thenReturn(GeoResolution.error());

        AddressGeoService.BackfillReport r = service.backfill(10);

        assertThat(r.attempted()).isEqualTo(3);
        assertThat(r.resolved()).isEqualTo(1);
        assertThat(r.stateOnly()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(1);
    }

    @Test
    void backfill_oneBadRowDoesNotAbortTheRest() {
        Address a = addr("a"), b = addr("b");
        when(addressRepository.findUnresolvedWithCoordinates(10)).thenReturn(List.of(a, b));
        when(addressRepository.findById("a")).thenReturn(Optional.of(a));
        when(addressRepository.findById("b")).thenReturn(Optional.of(b));
        when(geoResolutionService.resolve(any(), any()))
                .thenThrow(new RuntimeException("network"))
                .thenReturn(GeoResolution.resolved(25, 377, "Lagos", "Ikorodu"));

        AddressGeoService.BackfillReport r = service.backfill(10);

        assertThat(r.attempted()).isEqualTo(2);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.resolved()).isEqualTo(1);
    }

    @Test
    void backfill_withNothingPendingIsANoOp() {
        when(addressRepository.findUnresolvedWithCoordinates(10)).thenReturn(List.of());

        AddressGeoService.BackfillReport r = service.backfill(10);

        assertThat(r.attempted()).isZero();
        verify(geoResolutionService, never()).resolve(any(), any());
    }

    private Address addr(String id) {
        Address a = new Address();
        a.setId(id);
        a.setLatitude(6.5);
        a.setLongitude(3.5);
        return a;
    }
}
