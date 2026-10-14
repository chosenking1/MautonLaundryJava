package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.PickupSlot;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.PickupSlotRepository;
import com.work.mautonlaundry.exceptions.ConflictException;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.exceptions.serviceexceptions.ServiceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Admin editing of the pickup windows themselves.
 *
 * <p>This is the configurable half of scheduling: the shape of the offer --
 * three coarse blocks, or two-hour windows, or something per-city later -- is an
 * operations decision that should not need a release.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PickupSlotAdminService {

    private final PickupSlotRepository pickupSlotRepository;
    private final BookingRepository bookingRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PickupSlot> list() {
        return pickupSlotRepository.findAllByOrderBySortOrderAscStartTimeAsc();
    }

    @Transactional
    public PickupSlot create(String label, LocalTime start, LocalTime end, Integer sortOrder) {
        validate(label, start, end);
        pickupSlotRepository.findByLabelIgnoreCase(label.trim()).ifPresent(existing -> {
            throw new ConflictException("A window called '" + existing.getLabel() + "' already exists.");
        });

        PickupSlot slot = new PickupSlot();
        slot.setLabel(label.trim());
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setSortOrder(sortOrder == null ? nextSortOrder() : sortOrder);
        slot.setActive(true);
        PickupSlot saved = pickupSlotRepository.save(slot);
        auditService.logAction("CREATE", "PICKUP_SLOT", saved.getId());
        return saved;
    }

    @Transactional
    public PickupSlot update(String id, String label, LocalTime start, LocalTime end,
                             Integer sortOrder, Boolean active) {
        PickupSlot slot = pickupSlotRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Pickup window not found"));

        String newLabel = label == null || label.isBlank() ? slot.getLabel() : label.trim();
        LocalTime newStart = start == null ? slot.getStartTime() : start;
        LocalTime newEnd = end == null ? slot.getEndTime() : end;
        validate(newLabel, newStart, newEnd);

        pickupSlotRepository.findByLabelIgnoreCase(newLabel).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ConflictException("A window called '" + other.getLabel() + "' already exists.");
            }
        });

        slot.setLabel(newLabel);
        slot.setStartTime(newStart);
        slot.setEndTime(newEnd);
        if (sortOrder != null) slot.setSortOrder(sortOrder);
        if (active != null) slot.setActive(active);
        slot.setUpdatedAt(LocalDateTime.now());

        PickupSlot saved = pickupSlotRepository.save(slot);
        auditService.logAction("UPDATE", "PICKUP_SLOT", saved.getId());
        return saved;
    }

    /**
     * Retires a window.
     *
     * <p>Deactivation, never deletion: bookings already placed in a window still
     * have to be able to say what they were promised, and a customer holding a
     * confirmation for a window that no longer exists is worse than a tidy
     * table.
     */
    @Transactional
    public PickupSlot retire(String id) {
        PickupSlot slot = pickupSlotRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Pickup window not found"));

        long stillHeld = bookingRepository.countAwaitingScheduledPickupInSlot(id);
        if (stillHeld > 0) {
            // Retiring under a booking would leave it held with a window nobody
            // will release it for.
            throw new ConflictException(stillHeld + " booking(s) are still waiting on this window. "
                    + "Let them run, or move them, before retiring it.");
        }

        slot.setActive(false);
        slot.setUpdatedAt(LocalDateTime.now());
        PickupSlot saved = pickupSlotRepository.save(slot);
        auditService.logAction("RETIRE", "PICKUP_SLOT", saved.getId());
        return saved;
    }

    private int nextSortOrder() {
        return pickupSlotRepository.findAll().stream()
                .map(PickupSlot::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void validate(String label, LocalTime start, LocalTime end) {
        if (label == null || label.isBlank()) {
            throw new ForbiddenOperationException("A window needs a name customers will understand.");
        }
        if (start == null || end == null) {
            throw new ForbiddenOperationException("A window needs a start and an end time.");
        }
        if (!end.isAfter(start)) {
            throw new ForbiddenOperationException("A window must end after it starts.");
        }
    }
}
