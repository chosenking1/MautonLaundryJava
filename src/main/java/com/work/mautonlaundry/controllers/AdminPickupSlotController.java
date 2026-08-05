package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.PickupSlot;
import com.work.mautonlaundry.services.PickupSlotAdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

/** Admin management of the pickup windows customers choose from. */
@RestController
@RequestMapping("/api/v1/admin/pickup-slots")
@RequiredArgsConstructor
public class AdminPickupSlotController {

    private final PickupSlotAdminService pickupSlotAdminService;

    @GetMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PICKUP_SLOT_MANAGE')")
    public ResponseEntity<List<SlotView>> list() {
        return ResponseEntity.ok(pickupSlotAdminService.list().stream().map(SlotView::of).toList());
    }

    @PostMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PICKUP_SLOT_MANAGE')")
    public ResponseEntity<SlotView> create(@RequestBody SlotRequest request) {
        return ResponseEntity.ok(SlotView.of(pickupSlotAdminService.create(
                request.getLabel(), request.getStartTime(), request.getEndTime(),
                request.getSortOrder())));
    }

    @PutMapping("/{slotId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PICKUP_SLOT_MANAGE')")
    public ResponseEntity<SlotView> update(@PathVariable String slotId,
                                           @RequestBody SlotRequest request) {
        return ResponseEntity.ok(SlotView.of(pickupSlotAdminService.update(
                slotId, request.getLabel(), request.getStartTime(), request.getEndTime(),
                request.getSortOrder(), request.getActive())));
    }

    /** Retires a window. Bookings already in it keep their promise. */
    @DeleteMapping("/{slotId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('PICKUP_SLOT_MANAGE')")
    public ResponseEntity<SlotView> retire(@PathVariable String slotId) {
        return ResponseEntity.ok(SlotView.of(pickupSlotAdminService.retire(slotId)));
    }

    @Data
    public static class SlotRequest {
        private String label;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime startTime;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime endTime;
        private Integer sortOrder;
        private Boolean active;
    }

    public record SlotView(String id, String label, LocalTime startTime, LocalTime endTime,
                           Integer sortOrder, Boolean active, String description) {
        static SlotView of(PickupSlot s) {
            return new SlotView(s.getId(), s.getLabel(), s.getStartTime(), s.getEndTime(),
                    s.getSortOrder(), s.getActive(), s.describe());
        }
    }
}
