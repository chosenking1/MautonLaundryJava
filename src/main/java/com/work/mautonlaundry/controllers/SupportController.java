package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.dtos.requests.supportrequests.AddTicketMessageRequest;
import com.work.mautonlaundry.dtos.requests.supportrequests.CreateTicketRequest;
import com.work.mautonlaundry.dtos.responses.supportresponse.TicketView;
import com.work.mautonlaundry.services.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A customer's own complaints.
 *
 * <p>Not permission-guarded: raising a complaint is something any signed-in
 * customer must be able to do, and gating it behind a permission would mean a
 * misconfigured role silently removed someone's only route to us. Ownership is
 * enforced in the service.
 */
@RestController
@RequestMapping("/api/v1/support/tickets")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @PostMapping
    public ResponseEntity<TicketView> create(@Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.ok(supportService.createTicket(request));
    }

    @GetMapping
    public ResponseEntity<List<TicketView>> mine() {
        return ResponseEntity.ok(supportService.myTickets());
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketView> one(@PathVariable String ticketId) {
        return ResponseEntity.ok(supportService.getTicket(ticketId, false));
    }

    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<TicketView> reply(@PathVariable String ticketId,
                                            @Valid @RequestBody AddTicketMessageRequest request) {
        return ResponseEntity.ok(supportService.addMessage(ticketId, request, false));
    }
}
