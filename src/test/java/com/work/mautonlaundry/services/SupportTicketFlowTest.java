package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.SupportMessage;
import com.work.mautonlaundry.data.model.SupportTicket;
import com.work.mautonlaundry.data.model.enums.SupportTicketStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ticket's own behaviour: ordering by last activity, and the rule that a
 * customer replying to a "resolved" complaint reopens it.
 */
class SupportTicketFlowTest {

    private SupportMessage message(String body, boolean fromStaff, LocalDateTime at) {
        SupportMessage m = new SupportMessage();
        m.setAuthor(new AppUser());
        m.setFromStaff(fromStaff);
        m.setBody(body);
        m.setCreatedAt(at);
        return m;
    }

    @Test
    void addingAMessageMovesTheTicketUpTheQueue() {
        // The queue sorts on last activity, so a chased ticket must not sink
        // below quieter older ones.
        SupportTicket ticket = new SupportTicket();
        LocalDateTime later = LocalDateTime.now().plusHours(2);

        ticket.addMessage(message("Any update?", false, later));

        assertThat(ticket.getLastMessageAt()).isEqualTo(later);
        assertThat(ticket.getMessages()).hasSize(1);
    }

    @Test
    void messagesAreLinkedBackToTheirTicket() {
        SupportTicket ticket = new SupportTicket();
        SupportMessage m = message("My shirt is torn", false, LocalDateTime.now());

        ticket.addMessage(m);

        assertThat(m.getTicket()).isSameAs(ticket);
    }

    @Test
    void aNewTicketStartsOpenAndUnresolved() {
        SupportTicket ticket = new SupportTicket();
        assertThat(ticket.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void onlyClosedIsTerminal() {
        // RESOLVED deliberately is not: a customer who disagrees replies and the
        // conversation continues rather than being shut on them.
        assertThat(SupportTicketStatus.CLOSED.isTerminal()).isTrue();
        assertThat(SupportTicketStatus.RESOLVED.isTerminal()).isFalse();
        assertThat(SupportTicketStatus.OPEN.isTerminal()).isFalse();
        assertThat(SupportTicketStatus.IN_PROGRESS.isTerminal()).isFalse();
    }
}
