-- Customer complaints and the conversation about each one.
--
-- The app has had a "Help & Support" menu item wired to an empty handler since
-- launch, so a customer with a damaged garment or a double charge had no route
-- to us inside the product at all.
--
-- Threaded rather than a single message and reply: the most common exchange is
-- "which order was this?", and without a thread that clarification happens over
-- phone or WhatsApp and leaves no record attached to the complaint.

CREATE TABLE IF NOT EXISTS support_tickets (
    id               VARCHAR(36)  PRIMARY KEY,
    user_id          VARCHAR(36)  NOT NULL REFERENCES users(id),
    -- Nullable: "I was charged twice" belongs to no single order.
    booking_id       VARCHAR(36)  REFERENCES bookings(id),
    category         VARCHAR(40)  NOT NULL,
    subject          VARCHAR(160) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- Queue ordering: a chased ticket should not sink below quieter older ones.
    last_message_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    resolved_at      TIMESTAMP,
    CONSTRAINT support_tickets_status_check
        CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED'))
);

CREATE TABLE IF NOT EXISTS support_messages (
    id          VARCHAR(36)   PRIMARY KEY,
    ticket_id   VARCHAR(36)   NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    author_id   VARCHAR(36)   NOT NULL REFERENCES users(id),
    -- Recorded, not derived from the author's role: roles change, and a reply
    -- must still read as having come from staff a year later.
    from_staff  BOOLEAN       NOT NULL DEFAULT FALSE,
    body        VARCHAR(4000) NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- "My tickets", newest activity first.
CREATE INDEX IF NOT EXISTS idx_support_tickets_user
    ON support_tickets (user_id, last_message_at DESC);
-- The staff queue: open work, oldest activity first.
CREATE INDEX IF NOT EXISTS idx_support_tickets_status
    ON support_tickets (status, last_message_at);
CREATE INDEX IF NOT EXISTS idx_support_messages_ticket
    ON support_messages (ticket_id, created_at);

-- Permissions for the staff side. ON CONFLICT so a re-run is a no-op, matching
-- the rest of the seeded catalogue.
INSERT INTO permissions (name, description, category, active) VALUES
    ('SUPPORT_VIEW',    'View customer complaints',            'SUPPORT', TRUE),
    ('SUPPORT_RESPOND', 'Reply to and resolve complaints',     'SUPPORT', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('SUPPORT_VIEW', 'SUPPORT_RESPOND')
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
