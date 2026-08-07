package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.PickupSlot;
import com.work.mautonlaundry.data.repository.PickupSlotRepository;
import com.work.mautonlaundry.dtos.responses.pickup.PickupAvailabilityResponse;
import com.work.mautonlaundry.dtos.responses.pickup.PickupSlotOption;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides which pickup windows a customer may still choose, and when a booking
 * held for one should be let go.
 *
 * <p>Every rule here is a promise about a rider's time, so each is refused
 * server-side as well as hidden in the app: the app is the polite version, this
 * is the enforced one. A client that offers a window that opens in four minutes,
 * or a date next year, gets a 403 rather than a booking nobody can fill.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PickupSchedulingService {

    private final PickupSlotRepository pickupSlotRepository;

    @Value("${app.pickup.standard.lead-days:1}")
    private int leadDays;

    @Value("${app.pickup.same-day-enabled:true}")
    private boolean sameDayEnabled;

    @Value("${app.pickup.cutoff-time:14:00}")
    private String cutoffTime;

    @Value("${app.pickup.horizon-days:7}")
    private int horizonDays;

    @Value("${app.pickup.min-notice-minutes:60}")
    private long minNoticeMinutes;

    @Value("${app.pickup.release-lead-minutes:60}")
    private long releaseLeadMinutes;

    @Value("${app.dispatch.operating-hours.timezone:Africa/Lagos}")
    private String timezone;

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE d MMM");

    // ---------------------------------------------------------------- reading

    /**
     * Local now, in the timezone the business actually operates in.
     *
     * <p>Every rule in this class is relative to this one call, deliberately: a
     * cutoff at 14:00 behaves differently at 09:00 and at 15:00, so tests pin
     * time by overriding this rather than by hoping they run in the morning.
     */
    public LocalDateTime now() {
        return LocalDateTime.now(zone());
    }

    /**
     * Derived from {@link #now()} rather than read from the clock again, so
     * "today" cannot mean one date here and another in {@link #availability()}
     * -- which is what happens either side of midnight when two calls land on
     * opposite sides of it.
     */
    public LocalDate today() {
        return now().toLocalDate();
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("Invalid app.dispatch.operating-hours.timezone '{}' -- falling back to Africa/Lagos",
                    timezone);
            return ZoneId.of("Africa/Lagos");
        }
    }

    private LocalTime cutoff() {
        try {
            return LocalTime.parse(cutoffTime);
        } catch (Exception e) {
            log.warn("Invalid app.pickup.cutoff-time '{}' -- falling back to 14:00", cutoffTime);
            return LocalTime.of(14, 0);
        }
    }

    @Transactional(readOnly = true)
    public PickupAvailabilityResponse availability() {
        List<PickupSlot> slots = pickupSlotRepository.findByActiveTrueOrderBySortOrderAscStartTimeAsc();
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();

        List<PickupAvailabilityResponse.Day> days = new ArrayList<>();
        for (int offset = 0; offset <= Math.max(0, horizonDays); offset++) {
            LocalDate date = today.plusDays(offset);
            List<PickupSlotOption> options = new ArrayList<>();
            for (PickupSlot slot : slots) {
                String refusal = refusalFor(date, slot, now);
                options.add(refusal == null
                        ? PickupSlotOption.open(slot.getId(), slot.getLabel(),
                                slot.getStartTime(), slot.getEndTime())
                        : PickupSlotOption.closed(slot.getId(), slot.getLabel(),
                                slot.getStartTime(), slot.getEndTime(), refusal));
            }
            boolean any = options.stream().anyMatch(PickupSlotOption::selectable);
            days.add(new PickupAvailabilityResponse.Day(date, dayLabel(date, today), any, options));
        }

        return new PickupAvailabilityResponse(true, "As soon as possible", days);
    }

    private String dayLabel(LocalDate date, LocalDate today) {
        if (date.equals(today)) return "Today";
        if (date.equals(today.plusDays(1))) return "Tomorrow";
        return DAY_LABEL.format(date);
    }

    // ------------------------------------------------------------ the rules

    /**
     * Why this window cannot be chosen, or null if it can.
     *
     * <p>Returned as customer-facing text because it is shown next to the
     * window it refers to; a code would only be turned back into this sentence
     * in three different clients.
     */
    private String refusalFor(LocalDate date, PickupSlot slot, LocalDateTime now) {
        LocalDate today = now.toLocalDate();

        if (date.isBefore(today)) {
            return "This day has passed.";
        }
        if (date.isAfter(today.plusDays(Math.max(0, horizonDays)))) {
            return "Too far ahead to book yet.";
        }

        if (date.equals(today)) {
            if (!sameDayEnabled) {
                return "Same-day pickup is not available.";
            }
            if (!now.toLocalTime().isBefore(cutoff())) {
                return "Today's cutoff has passed.";
            }
        } else if (date.isBefore(today.plusDays(effectiveLeadDays(now)))) {
            // Same-day is off, or its cutoff has gone, so the earliest day moves
            // out by the standard lead time.
            return "Too soon -- the earliest is "
                    + dayLabel(today.plusDays(effectiveLeadDays(now)), today) + ".";
        }

        LocalDateTime windowOpens = LocalDateTime.of(date, slot.getStartTime());
        if (windowOpens.isBefore(now.plusMinutes(minNoticeMinutes))) {
            return "Too soon to arrange.";
        }
        return null;
    }

    /**
     * The earliest day offset that may be booked.
     *
     * <p>Zero while same-day is genuinely open. Once the cutoff passes, today
     * stops counting and the standard lead applies from tomorrow -- otherwise a
     * lead of one day would still resolve to today at 11pm.
     */
    private int effectiveLeadDays(LocalDateTime now) {
        if (sameDayEnabled && now.toLocalTime().isBefore(cutoff())) {
            return 0;
        }
        return Math.max(1, leadDays);
    }

    // ------------------------------------------------------------ validating

    /**
     * Resolves a customer's choice, refusing anything the rules do not allow.
     *
     * @return the slot, or null when no schedule was requested (collect now)
     */
    @Transactional(readOnly = true)
    public PickupSlot validateChoice(LocalDate date, String slotId) {
        boolean hasDate = date != null;
        boolean hasSlot = slotId != null && !slotId.isBlank();

        if (!hasDate && !hasSlot) {
            return null;   // no schedule: collect as soon as possible, as before
        }
        if (hasDate != hasSlot) {
            // Half a choice cannot be honoured: a date with no window is not a
            // time, and a window with no date is not a day.
            throw new ForbiddenOperationException(
                    "Choose both a pickup day and a time window, or neither.");
        }

        PickupSlot slot = pickupSlotRepository.findById(slotId)
                .orElseThrow(() -> new ForbiddenOperationException("That pickup window no longer exists."));
        if (!Boolean.TRUE.equals(slot.getActive())) {
            throw new ForbiddenOperationException("That pickup window is no longer offered.");
        }

        String refusal = refusalFor(date, slot, now());
        if (refusal != null) {
            throw new ForbiddenOperationException(refusal);
        }
        return slot;
    }

    // ------------------------------------------------------------- releasing

    /**
     * When a booking held for this window should be offered to laundry
     * partners.
     *
     * <p>Before the window opens, not at it: finding a partner, then a rider,
     * then having that rider travel, all has to happen inside the window the
     * customer was promised.
     */
    public LocalDateTime releaseAt(LocalDate date, PickupSlot slot) {
        return LocalDateTime.of(date, slot.getStartTime()).minusMinutes(releaseLeadMinutes);
    }

    public boolean isDueForRelease(LocalDate date, PickupSlot slot) {
        return !now().isBefore(releaseAt(date, slot));
    }

    /** "Thursday, Morning 8:00 - 12:00" -- for confirmations and emails. */
    public String describeWindow(LocalDate date, PickupSlot slot) {
        if (date == null || slot == null) {
            return null;
        }
        return dayLabel(date, today()) + ", " + slot.describe();
    }

    public Duration releaseLead() {
        return Duration.ofMinutes(releaseLeadMinutes);
    }
}
