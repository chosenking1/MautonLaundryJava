package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.LaundryAgentHours;
import com.work.mautonlaundry.data.repository.LaundryAgentHoursRepository;
import com.work.mautonlaundry.data.repository.UserRepository;
import com.work.mautonlaundry.exceptions.userexceptions.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Per-laundry-agent operating hours. Backs the assignment-side filter that
 * skips an agent who is closed right now without ever issuing them a
 * booking offer (see LaundryAssignmentService).
 *
 * Timezone: hours are stored as TIME (no zone) and interpreted in
 * {@code app.dispatch.operating-hours.timezone} (Africa/Lagos by default), so
 * a single config controls both global dispatch quiet hours and per-agent
 * windows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LaundryAgentHoursService {

    private final LaundryAgentHoursRepository repository;
    private final UserRepository userRepository;

    @Value("${app.dispatch.operating-hours.timezone:Africa/Lagos}")
    private String operatingHoursTimezone;

    /**
     * True when the agent is currently within their published opening window
     * for today's day-of-week, OR when the agent has no rows configured at
     * all (treated as "always open" for backward compat).
     */
    public boolean isOpenAt(String agentId, Instant moment) {
        List<LaundryAgentHours> rows = repository.findByUser_Id(agentId);
        if (rows.isEmpty()) return true;
        ZoneId zone = resolveZone();
        ZonedDateTime localised = moment.atZone(zone);
        short dow = (short) localised.getDayOfWeek().getValue();
        Optional<LaundryAgentHours> today = rows.stream()
                .filter(r -> r.getDayOfWeek() == dow)
                .findFirst();
        if (today.isEmpty()) return false;
        LocalTime now = localised.toLocalTime();
        LaundryAgentHours h = today.get();
        return !now.isBefore(h.getOpeningTime()) && now.isBefore(h.getClosingTime());
    }

    public List<LaundryAgentHours> getHoursFor(String agentId) {
        return repository.findByUser_Id(agentId);
    }

    /**
     * Bulk-replace the agent's hours. Each entry must have a unique
     * day-of-week; the call wipes any existing rows for the agent and
     * inserts the supplied set in one transaction.
     */
    @Transactional
    public List<LaundryAgentHours> replaceHours(String agentId, List<DayWindow> windows) {
        AppUser agent = userRepository.findUserById(agentId)
                .orElseThrow(() -> new UserNotFoundException("Agent not found: " + agentId));
        if (!agent.hasRole("LAUNDRY_AGENT")) {
            throw new IllegalArgumentException("User is not a LAUNDRY_AGENT: " + agentId);
        }
        // Validate unique days + sane windows up front so we never wipe rows
        // and then reject on bad input.
        boolean[] daySeen = new boolean[8];
        for (DayWindow w : windows) {
            if (w.dayOfWeek < 1 || w.dayOfWeek > 7) {
                throw new IllegalArgumentException("dayOfWeek must be 1..7 (Mon..Sun): " + w.dayOfWeek);
            }
            if (daySeen[w.dayOfWeek]) {
                throw new IllegalArgumentException("Duplicate day_of_week in input: " + w.dayOfWeek);
            }
            if (!w.closingTime.isAfter(w.openingTime)) {
                throw new IllegalArgumentException(
                        "closing_time must be after opening_time for day " + w.dayOfWeek);
            }
            daySeen[w.dayOfWeek] = true;
        }

        repository.deleteByUser_Id(agentId);
        repository.flush();

        for (DayWindow w : windows) {
            LaundryAgentHours row = new LaundryAgentHours();
            row.setUser(agent);
            row.setDayOfWeek((short) w.dayOfWeek);
            row.setOpeningTime(w.openingTime);
            row.setClosingTime(w.closingTime);
            repository.save(row);
        }
        log.info("Replaced laundry agent hours: agentId={} rows={}", agentId, windows.size());
        return repository.findByUser_Id(agentId);
    }

    private ZoneId resolveZone() {
        try {
            return ZoneId.of(operatingHoursTimezone);
        } catch (Exception ex) {
            log.warn("Invalid app.dispatch.operating-hours.timezone '{}' — falling back to Africa/Lagos",
                    operatingHoursTimezone);
            return ZoneId.of("Africa/Lagos");
        }
    }

    /** Plain-data carrier so callers don't depend on the JPA entity. */
    public record DayWindow(int dayOfWeek, LocalTime openingTime, LocalTime closingTime) {
        public static DayWindow of(DayOfWeek day, LocalTime open, LocalTime close) {
            return new DayWindow(day.getValue(), open, close);
        }
    }
}
