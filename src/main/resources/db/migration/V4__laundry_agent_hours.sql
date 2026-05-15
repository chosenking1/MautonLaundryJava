-- Per-laundry-agent operating hours. Normalised: one row per (user_id, day_of_week).
--
-- Backstory: dispatch quiet hours are global ("don't offer between 20:00 and 08:00 Africa/Lagos"),
-- but real laundry shops keep different hours — some close at 4pm, some don't open Sunday. We need
-- per-agent windows so the dispatcher / laundry-assignment code can skip an agent who is closed
-- right now without paging them or expiring the booking.
--
-- day_of_week uses the Postgres ISO convention via Java's DayOfWeek.getValue():
--   1 = Monday, 2 = Tuesday, ..., 7 = Sunday.
-- opening_time and closing_time are local TIME values; the application layer interprets them as
-- Africa/Lagos (matching app.dispatch.operating-hours.timezone).
--
-- Backward compat: an agent with NO rows is treated as "always open" so the existing single-agent
-- setup keeps working until rows are added via the admin endpoint.

CREATE TABLE laundry_agent_hours (
    id            VARCHAR(36) PRIMARY KEY,
    user_id       VARCHAR(36) NOT NULL REFERENCES users(id),
    day_of_week   SMALLINT    NOT NULL,
    opening_time  TIME        NOT NULL,
    closing_time  TIME        NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT laundry_agent_hours_dow_check CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT laundry_agent_hours_window_check CHECK (closing_time > opening_time),
    CONSTRAINT laundry_agent_hours_unique_day UNIQUE (user_id, day_of_week)
);

CREATE INDEX idx_laundry_agent_hours_user ON laundry_agent_hours(user_id);
