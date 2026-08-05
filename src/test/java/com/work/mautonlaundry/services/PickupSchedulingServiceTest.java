package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.PickupSlot;
import com.work.mautonlaundry.data.repository.PickupSlotRepository;
import com.work.mautonlaundry.dtos.responses.pickup.PickupAvailabilityResponse;
import com.work.mautonlaundry.dtos.responses.pickup.PickupSlotOption;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The rules about which pickup window a customer may still choose.
 *
 * <p>Time is pinned rather than taken from the wall clock: a cutoff at 14:00
 * makes every one of these tests behave differently depending on when the suite
 * happens to run, and a scheduling test that passes all morning and fails after
 * lunch teaches people to re-run rather than to look.
 */
class PickupSchedulingServiceTest {

    private PickupSlotRepository slots;
    private LocalDateTime pinned;
    private PickupSchedulingService service;

    private PickupSlot morning;
    private PickupSlot afternoon;
    private PickupSlot evening;

    @BeforeEach
    void setUp() {
        slots = Mockito.mock(PickupSlotRepository.class);
        morning = slot("m", "Morning", 8, 12, 1);
        afternoon = slot("a", "Afternoon", 12, 16, 2);
        evening = slot("e", "Evening", 16, 20, 3);
        when(slots.findByActiveTrueOrderBySortOrderAscStartTimeAsc())
                .thenReturn(List.of(morning, afternoon, evening));
        when(slots.findById("m")).thenReturn(Optional.of(morning));
        when(slots.findById("a")).thenReturn(Optional.of(afternoon));
        when(slots.findById("e")).thenReturn(Optional.of(evening));

        service = new PickupSchedulingService(slots) {
            @Override
            public LocalDateTime now() {
                return pinned;
            }
        };
        configure(true, "14:00", 1, 7, 60, 60);
        at(2026, 8, 5, 9, 0);   // Wednesday morning, well before the cutoff
    }

    private PickupSlot slot(String id, String label, int startHour, int endHour, int order) {
        PickupSlot s = new PickupSlot();
        s.setId(id);
        s.setLabel(label);
        s.setStartTime(LocalTime.of(startHour, 0));
        s.setEndTime(LocalTime.of(endHour, 0));
        s.setSortOrder(order);
        s.setActive(true);
        return s;
    }

    private void configure(boolean sameDay, String cutoff, int leadDays, int horizon,
                           long minNotice, long releaseLead) {
        ReflectionTestUtils.setField(service, "sameDayEnabled", sameDay);
        ReflectionTestUtils.setField(service, "cutoffTime", cutoff);
        ReflectionTestUtils.setField(service, "leadDays", leadDays);
        ReflectionTestUtils.setField(service, "horizonDays", horizon);
        ReflectionTestUtils.setField(service, "minNoticeMinutes", minNotice);
        ReflectionTestUtils.setField(service, "releaseLeadMinutes", releaseLead);
        ReflectionTestUtils.setField(service, "timezone", "Africa/Lagos");
    }

    private void at(int y, int m, int d, int hh, int mm) {
        pinned = LocalDateTime.of(y, m, d, hh, mm);
    }

    private LocalDate today() {
        return pinned.toLocalDate();
    }

    private PickupSlotOption option(LocalDate date, String slotId) {
        return service.availability().days().stream()
                .filter(day -> day.date().equals(date))
                .flatMap(day -> day.slots().stream())
                .filter(s -> s.slotId().equals(slotId))
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------------------------- same day

    @Test
    void aLaterWindowTodayIsSelectableBeforeTheCutoff() {
        at(2026, 8, 5, 9, 0);
        assertThat(option(today(), "e").selectable()).isTrue();
    }

    @Test
    void aWindowThatHasAlreadyOpenedTodayIsNot() {
        at(2026, 8, 5, 9, 0);   // the 8am window is already running
        PickupSlotOption morningToday = option(today(), "m");
        assertThat(morningToday.selectable()).isFalse();
        assertThat(morningToday.unavailableReason()).isEqualTo("Too soon to arrange.");
    }

    @Test
    void aWindowOpeningInsideTheNoticePeriodIsNot() {
        // 11:30, with the noon window an hour of notice away to the minute.
        at(2026, 8, 5, 11, 30);
        assertThat(option(today(), "a").selectable()).isFalse();
        at(2026, 8, 5, 10, 59);
        assertThat(option(today(), "a").selectable()).isTrue();
    }

    @Test
    void afterTheCutoffTodayClosesEntirely() {
        at(2026, 8, 5, 14, 1);
        PickupAvailabilityResponse.Day todayDay = service.availability().days().get(0);
        assertThat(todayDay.date()).isEqualTo(today());
        assertThat(todayDay.hasSelectableSlot()).isFalse();
        assertThat(todayDay.slots()).allSatisfy(s ->
                assertThat(s.unavailableReason()).isEqualTo("Today's cutoff has passed."));
    }

    @Test
    void theCutoffIsExclusiveAtTheMinuteItself() {
        at(2026, 8, 5, 14, 0);
        assertThat(option(today(), "e").unavailableReason()).isEqualTo("Today's cutoff has passed.");
    }

    @Test
    void withSameDayOffTodayIsClosedEvenEarlyMorning() {
        configure(false, "14:00", 1, 7, 60, 60);
        at(2026, 8, 5, 6, 0);
        assertThat(option(today(), "e").unavailableReason())
                .isEqualTo("Same-day pickup is not available.");
    }

    // ------------------------------------------------------------ lead time

    @Test
    void tomorrowIsOpenOnceTodayHasClosed() {
        at(2026, 8, 5, 15, 0);
        assertThat(option(today().plusDays(1), "m").selectable()).isTrue();
    }

    @Test
    void withSameDayOffTheLeadPushesPastTomorrowOnly() {
        // lead-days=2 means the earliest is the day after tomorrow.
        configure(false, "14:00", 2, 7, 60, 60);
        at(2026, 8, 5, 6, 0);
        assertThat(option(today().plusDays(1), "m").selectable()).isFalse();
        assertThat(option(today().plusDays(2), "m").selectable()).isTrue();
    }

    @Test
    void aLeadOfOneDayStillResolvesToTomorrowLateAtNight() {
        // At 23:00 a naive "today plus one day" is tomorrow, which is right --
        // but only because the cutoff already removed today. This pins that the
        // two rules do not cancel out and re-open today.
        at(2026, 8, 5, 23, 0);
        assertThat(option(today(), "m").selectable()).isFalse();
        assertThat(option(today().plusDays(1), "m").selectable()).isTrue();
    }

    // -------------------------------------------------------------- horizon

    @Test
    void theHorizonIsTheLastDayOffered() {
        at(2026, 8, 5, 9, 0);
        List<PickupAvailabilityResponse.Day> days = service.availability().days();
        assertThat(days).hasSize(8);   // today plus seven
        assertThat(days.get(7).date()).isEqualTo(today().plusDays(7));
        assertThat(days.get(7).hasSelectableSlot()).isTrue();
    }

    @Test
    void beyondTheHorizonIsRefusedEvenIfAskedForDirectly() {
        at(2026, 8, 5, 9, 0);
        assertThatThrownBy(() -> service.validateChoice(today().plusDays(30), "m"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Too far ahead");
    }

    // ------------------------------------------------------------- labelling

    @Test
    void theFirstTwoDaysAreNamedRatherThanDated() {
        at(2026, 8, 5, 9, 0);
        List<PickupAvailabilityResponse.Day> days = service.availability().days();
        assertThat(days.get(0).label()).isEqualTo("Today");
        assertThat(days.get(1).label()).isEqualTo("Tomorrow");
        assertThat(days.get(2).label()).isEqualTo("Fri 7 Aug");
    }

    @Test
    void closedWindowsAreReturnedWithTheirReasonRatherThanOmitted() {
        at(2026, 8, 5, 9, 0);
        // A customer seeing only "Evening" cannot tell whether the rest are
        // full, closed, or never offered.
        assertThat(service.availability().days().get(0).slots()).hasSize(3);
    }

    @Test
    void asapIsAlwaysOffered() {
        at(2026, 8, 5, 23, 59);
        assertThat(service.availability().asapSelectable()).isTrue();
    }

    // ------------------------------------------------------------ validating

    @Test
    void noChoiceAtAllMeansCollectNow() {
        assertThat(service.validateChoice(null, null)).isNull();
        assertThat(service.validateChoice(null, "  ")).isNull();
    }

    @Test
    void halfAChoiceIsRefused() {
        // A date with no window is not a time; a window with no date is not a day.
        assertThatThrownBy(() -> service.validateChoice(today().plusDays(1), null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("both");
        assertThatThrownBy(() -> service.validateChoice(null, "m"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("both");
    }

    @Test
    void aValidChoiceComesBackAsTheSlot() {
        at(2026, 8, 5, 9, 0);
        assertThat(service.validateChoice(today().plusDays(1), "m")).isSameAs(morning);
    }

    @Test
    void theServerRefusesWhatTheAppWouldHaveHidden() {
        // The app filters; this is what stops a stale or hand-rolled client
        // booking a window that opens in four minutes.
        at(2026, 8, 5, 11, 59);
        assertThatThrownBy(() -> service.validateChoice(today(), "a"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Too soon");
    }

    @Test
    void aRetiredWindowIsRefusedEvenWhenItsTimesStillFit() {
        morning.setActive(false);
        at(2026, 8, 5, 9, 0);
        assertThatThrownBy(() -> service.validateChoice(today().plusDays(1), "m"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("no longer offered");
    }

    @Test
    void anUnknownWindowIsRefused() {
        when(slots.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.validateChoice(today().plusDays(1), "ghost"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("no longer exists");
    }

    @Test
    void yesterdayIsRefused() {
        at(2026, 8, 5, 9, 0);
        assertThatThrownBy(() -> service.validateChoice(today().minusDays(1), "m"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("has passed");
    }

    // -------------------------------------------------------------- release

    @Test
    void releaseHappensBeforeTheWindowOpensNotAtIt() {
        // Finding a partner, then a rider, then travel all has to fit inside the
        // window the customer was promised.
        LocalDate date = LocalDate.of(2026, 8, 6);
        assertThat(service.releaseAt(date, morning))
                .isEqualTo(LocalDateTime.of(2026, 8, 6, 7, 0));
    }

    @Test
    void aBookingIsNotDueUntilItsReleaseMoment() {
        LocalDate tomorrow = LocalDate.of(2026, 8, 6);
        at(2026, 8, 6, 6, 59);
        assertThat(service.isDueForRelease(tomorrow, morning)).isFalse();
        at(2026, 8, 6, 7, 0);
        assertThat(service.isDueForRelease(tomorrow, morning)).isTrue();
    }

    @Test
    void aWindowAlreadyPastIsDueImmediately() {
        // Whatever the cause -- downtime, a slow restart -- a missed window must
        // dispatch now rather than wait another day.
        at(2026, 8, 7, 10, 0);
        assertThat(service.isDueForRelease(LocalDate.of(2026, 8, 6), morning)).isTrue();
    }

    @Test
    void theWindowReadsAsASentence() {
        at(2026, 8, 5, 9, 0);
        assertThat(service.describeWindow(today().plusDays(1), morning))
                .isEqualTo("Tomorrow, Morning, 8:00 - 12:00");
        assertThat(service.describeWindow(null, morning)).isNull();
        assertThat(service.describeWindow(today(), null)).isNull();
    }
}
