-- Letting a customer choose when their laundry is collected.
--
-- Until now every booking dispatched the instant it was created, so "book a
-- pickup" meant "a rider is coming now, whatever you are doing". The customer
-- app has shown a step labelled "Schedule" since launch that selects a pickup
-- address, and application.properties has carried a commented "Pickup
-- scheduling policy" block that no Java code has ever read.
--
-- Slots are rows rather than an enum or a fixed set of hours in config because
-- the right shape is an operations question that will change: three coarse
-- blocks while the rider pool is small, two-hour windows once it is dense
-- enough to hit them, and probably different answers per city later. Making
-- that a deploy would mean it never gets tuned.

CREATE TABLE IF NOT EXISTS pickup_slots (
    id          VARCHAR(36) PRIMARY KEY,
    -- What the customer reads. Shown next to the times, not instead of them:
    -- "Morning" alone has each person guessing a different four hours.
    label       VARCHAR(40) NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    -- Display order is set by hand rather than by start_time, so an admin can
    -- promote a slot they want filled without moving its hours.
    sort_order  INT         NOT NULL DEFAULT 0,
    -- Retiring a slot must not orphan the bookings already placed into it, so
    -- slots are deactivated rather than deleted.
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP,
    CONSTRAINT pickup_slots_label_unique UNIQUE (label),
    CONSTRAINT pickup_slots_order_check CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_pickup_slots_active
    ON pickup_slots (active, sort_order);

-- A starting set inside the existing dispatch window (01:00-20:00 Africa/Lagos),
-- deliberately coarse: a window we beat is worth more right now than a narrow
-- one we miss. Splitting these into two-hour windows is an admin edit, not a
-- release.
INSERT INTO pickup_slots (id, label, start_time, end_time, sort_order, active) VALUES
    ('a1e1c4d2-0001-4a10-9c01-000000000001', 'Morning',   '08:00', '12:00', 1, TRUE),
    ('a1e1c4d2-0002-4a10-9c01-000000000002', 'Afternoon', '12:00', '16:00', 2, TRUE),
    ('a1e1c4d2-0003-4a10-9c01-000000000003', 'Evening',   '16:00', '20:00', 3, TRUE)
ON CONFLICT (label) DO NOTHING;

-- Nullable throughout, and that is the compatibility contract: released APKs
-- send no schedule, and a booking without one must keep dispatching
-- immediately exactly as it does today. Scheduling is opt-in per booking.
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS scheduled_pickup_date DATE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS pickup_slot_id VARCHAR(36) REFERENCES pickup_slots(id);
-- Set when the hold is lifted. This is what makes releasing idempotent: the
-- sweep can run twice, or run again after a restart, without re-offering a
-- booking that is already out with a laundry partner.
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS pickup_released_at TIMESTAMP;

-- The releaser's query: held bookings whose window is due. Partial, because
-- the rows it wants are a vanishing fraction of the table and every other
-- booking would otherwise be scanned every two minutes forever.
CREATE INDEX IF NOT EXISTS idx_bookings_awaiting_release
    ON bookings (scheduled_pickup_date)
    WHERE pickup_released_at IS NULL AND scheduled_pickup_date IS NOT NULL;

INSERT INTO permissions (name, description, category, active) VALUES
    ('PICKUP_SLOT_MANAGE', 'Create and edit pickup time slots', 'OPERATIONS', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'PICKUP_SLOT_MANAGE'
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
