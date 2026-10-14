package com.work.mautonlaundry.data.model;

import com.work.mautonlaundry.data.model.enums.SupportCategory;
import com.work.mautonlaundry.data.model.enums.SupportTicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer complaint and the conversation about it.
 *
 * <p>The booking link is optional: most complaints are about a specific order,
 * but "I was charged twice" or "the app will not load" belong to no order and
 * must still be reportable.
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@NoArgsConstructor
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** The order this is about, when it is about one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SupportCategory category = SupportCategory.OTHER;

    @Column(nullable = false, length = 160)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * When the last message arrived, from either side. Sorting the queue by this
     * rather than by creation date keeps a ticket the customer has just chased
     * from sinking below quieter older ones.
     */
    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<SupportMessage> messages = new ArrayList<>();

    public void addMessage(SupportMessage message) {
        message.setTicket(this);
        messages.add(message);
        lastMessageAt = message.getCreatedAt();
    }
}
