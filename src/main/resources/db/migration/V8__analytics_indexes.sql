-- Indexes for the analytics/dashboard queries (Phase 1).
-- The dashboard, customer-intelligence and CAS queries filter and aggregate
-- bookings by created_at, user_id and status, and users by created_at.
-- Postgres does not auto-create an index on FK columns, so bookings.user_id
-- in particular benefits. Kept to simple single-column indexes — sufficient at
-- launch volumes; idempotent via IF NOT EXISTS.

CREATE INDEX IF NOT EXISTS idx_bookings_created_at ON bookings (created_at);
CREATE INDEX IF NOT EXISTS idx_bookings_user_id    ON bookings (user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status     ON bookings (status);
CREATE INDEX IF NOT EXISTS idx_users_created_at    ON users (created_at);
