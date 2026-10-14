package com.work.mautonlaundry.data.model.enums;

/**
 * Where a complaint has got to.
 *
 * <p>RESOLVED is set by staff when they believe it is handled; CLOSED happens
 * after that, so a customer who disagrees can reply and reopen rather than
 * having the conversation shut on them.
 */
public enum SupportTicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public boolean isTerminal() {
        return this == CLOSED;
    }
}
