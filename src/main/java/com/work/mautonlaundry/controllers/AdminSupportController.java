package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.supportrequests.AddTicketMessageRequest;
import com.work.mautonlaundry.dtos.responses.supportresponse.TicketView;
import com.work.mautonlaundry.services.SupportService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** The staff side of complaints. */
@RestController
@RequestMapping("/api/v1/admin/support/tickets")
@RequiredArgsConstructor
public class AdminSupportController {

    private final SupportService supportService;

    @GetMapping
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SUPPORT_VIEW')")
    public ResponseEntity<Page<TicketView>> queue(
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(supportService.queue(includeClosed, PageRequest.of(page, size)));
    }

    @GetMapping("/{ticketId}")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SUPPORT_VIEW')")
    public ResponseEntity<TicketView> one(@PathVariable String ticketId) {
        return ResponseEntity.ok(supportService.getTicket(ticketId, true));
    }

    @PostMapping("/{ticketId}/messages")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SUPPORT_RESPOND')")
    public ResponseEntity<TicketView> reply(@PathVariable String ticketId,
                                            @Valid @RequestBody AddTicketMessageRequest request) {
        return ResponseEntity.ok(supportService.addMessage(ticketId, request, true));
    }

    @PatchMapping("/{ticketId}/status")
    @PreAuthorize("@permissionEvaluationService.currentUserHasPermission('SUPPORT_RESPOND')")
    public ResponseEntity<TicketView> setStatus(@PathVariable String ticketId,
                                                 @RequestBody StatusRequest request) {
        return ResponseEntity.ok(supportService.setStatus(ticketId, request.getStatus()));
    }

    @Data
    public static class StatusRequest {
        private String status;
    }
}
