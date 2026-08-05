package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.Booking;
import com.work.mautonlaundry.data.model.SupportMessage;
import com.work.mautonlaundry.data.model.SupportTicket;
import com.work.mautonlaundry.data.model.enums.SupportCategory;
import com.work.mautonlaundry.data.model.enums.SupportTicketStatus;
import com.work.mautonlaundry.data.repository.BookingRepository;
import com.work.mautonlaundry.data.repository.SupportTicketRepository;
import com.work.mautonlaundry.dtos.requests.supportrequests.AddTicketMessageRequest;
import com.work.mautonlaundry.dtos.requests.supportrequests.CreateTicketRequest;
import com.work.mautonlaundry.dtos.responses.supportresponse.TicketMessageView;
import com.work.mautonlaundry.dtos.responses.supportresponse.TicketView;
import com.work.mautonlaundry.exceptions.ForbiddenOperationException;
import com.work.mautonlaundry.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Customer complaints.
 *
 * <p>A ticket is owned by the customer who raised it. Staff reach every ticket
 * through the permission-guarded endpoints; a customer reaches only their own,
 * enforced here rather than left to the controller so no future caller can skip
 * the check.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupportService {

    private final SupportTicketRepository ticketRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    @Transactional
    public TicketView createTicket(CreateTicketRequest request) {
        AppUser caller = currentUser();

        SupportTicket ticket = new SupportTicket();
        ticket.setUser(caller);
        ticket.setCategory(parseCategory(request.getCategory()));
        ticket.setSubject(request.getSubject().trim());
        ticket.setStatus(SupportTicketStatus.OPEN);

        if (request.getBookingId() != null && !request.getBookingId().isBlank()) {
            // Silently ignored rather than rejected when it is not theirs: a
            // mistyped id should not lose the complaint they just wrote out.
            bookingRepository.findByIdAndDeletedFalse(request.getBookingId())
                    .filter(b -> b.getUser() != null && b.getUser().getId().equals(caller.getId()))
                    .ifPresent(ticket::setBooking);
        }

        SupportMessage first = new SupportMessage();
        first.setAuthor(caller);
        first.setFromStaff(false);
        first.setBody(request.getMessage().trim());
        ticket.addMessage(first);

        SupportTicket saved = ticketRepository.save(ticket);
        log.info("Support ticket {} raised by {} ({})", saved.getId(), caller.getEmail(), saved.getCategory());
        return toView(saved, true, false);
    }

    /** A customer's own tickets. */
    @Transactional(readOnly = true)
    public List<TicketView> myTickets() {
        return ticketRepository.findByUserOrderByLastMessageAtDesc(currentUser())
                .stream().map(t -> toView(t, false, false)).toList();
    }

    /** The staff queue. Unresolved first by default -- that is the work. */
    @Transactional(readOnly = true)
    public Page<TicketView> queue(boolean includeClosed, Pageable pageable) {
        Page<SupportTicket> page = includeClosed
                ? ticketRepository.findAllByOrderByLastMessageAtDesc(pageable)
                : ticketRepository.findByStatusInOrderByLastMessageAtAsc(
                        List.of(SupportTicketStatus.OPEN, SupportTicketStatus.IN_PROGRESS), pageable);
        return page.map(t -> toView(t, false, true));
    }

    @Transactional(readOnly = true)
    public TicketView getTicket(String ticketId, boolean asStaff) {
        SupportTicket ticket = load(ticketId);
        if (!asStaff) {
            requireOwner(ticket);
        }
        return toView(ticket, true, asStaff);
    }

    @Transactional
    public TicketView addMessage(String ticketId, AddTicketMessageRequest request, boolean asStaff) {
        SupportTicket ticket = load(ticketId);
        AppUser caller = currentUser();
        if (!asStaff) {
            requireOwner(ticket);
        }

        SupportMessage message = new SupportMessage();
        message.setAuthor(caller);
        message.setFromStaff(asStaff);
        message.setBody(request.getMessage().trim());
        message.setCreatedAt(LocalDateTime.now());
        ticket.addMessage(message);

        // A staff reply moves an untouched ticket into progress; a customer reply
        // reopens one that was marked resolved, because being told it is fixed is
        // not the same as it being fixed.
        if (asStaff && ticket.getStatus() == SupportTicketStatus.OPEN) {
            ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        } else if (!asStaff && ticket.getStatus() == SupportTicketStatus.RESOLVED) {
            ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
            ticket.setResolvedAt(null);
        }

        SupportTicket saved = ticketRepository.save(ticket);

        if (asStaff) {
            notificationService.notifySupportReply(
                    ticket.getUser().getEmail(), ticket.getId(), ticket.getSubject());
        }
        return toView(saved, true, asStaff);
    }

    @Transactional
    public TicketView setStatus(String ticketId, String rawStatus) {
        SupportTicket ticket = load(ticketId);
        SupportTicketStatus status;
        try {
            status = SupportTicketStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + rawStatus);
        }
        ticket.setStatus(status);
        ticket.setResolvedAt(status == SupportTicketStatus.RESOLVED ? LocalDateTime.now() : null);
        return toView(ticketRepository.save(ticket), true, true);
    }

    // ---- helpers ----

    private SupportTicket load(String ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Complaint not found"));
    }

    private AppUser currentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new ForbiddenOperationException("Not signed in"));
    }

    private void requireOwner(SupportTicket ticket) {
        AppUser caller = currentUser();
        if (ticket.getUser() == null || !ticket.getUser().getId().equals(caller.getId())) {
            throw new ForbiddenOperationException("This complaint is not yours");
        }
    }

    private static SupportCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return SupportCategory.OTHER;
        try {
            return SupportCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // An unrecognised category must never cost someone their complaint.
            return SupportCategory.OTHER;
        }
    }

    static String firstName(AppUser user) {
        return DeliveryService.firstNameOf(user);
    }

    private TicketView toView(SupportTicket ticket, boolean withMessages, boolean forStaff) {
        Booking booking = ticket.getBooking();
        return TicketView.builder()
                .id(ticket.getId())
                .bookingId(booking == null ? null : booking.getId())
                .category(ticket.getCategory().name())
                .subject(ticket.getSubject())
                .status(ticket.getStatus().name())
                .createdAt(ticket.getCreatedAt())
                .lastMessageAt(ticket.getLastMessageAt())
                .customerName(forStaff ? ticket.getUser().getFull_name() : null)
                .customerPhone(forStaff ? ticket.getUser().getPhone_number() : null)
                .messages(withMessages
                        ? ticket.getMessages().stream().map(SupportService::toMessageView).toList()
                        : null)
                .build();
    }

    private static TicketMessageView toMessageView(SupportMessage m) {
        return TicketMessageView.builder()
                .id(m.getId())
                // Staff replies read as "Imototo" rather than naming an individual:
                // the customer is dealing with the company, not a person to chase.
                .authorName(m.isFromStaff() ? "Imototo Support" : firstName(m.getAuthor()))
                .fromStaff(m.isFromStaff())
                .body(m.getBody())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
