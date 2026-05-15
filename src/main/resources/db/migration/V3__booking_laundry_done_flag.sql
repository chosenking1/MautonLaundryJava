-- Tracks the moment the laundry agent marked the order "done washing".
-- The booking can stay at WASHING after this if payment is not yet COMPLETED;
-- once payment lands, the system uses this flag to decide whether to auto-
-- advance to READY_FOR_DELIVERY (rather than re-mark mid-wash bookings).

ALTER TABLE bookings
    ADD COLUMN laundry_marked_done_at TIMESTAMP;
