-- Lightweight daily aggregation for the admin Executive Dashboard.
-- One row per completed day, written by a scheduled midnight (Africa/Lagos) job.
--
-- IF NOT EXISTS for the same reason as V5: this migration never reached a
-- deployment (a blanket *.sql gitignore excluded it), but ddl-auto=update
-- already created the table from the entity, so a bare CREATE TABLE would fail
-- on first run and refuse to start the app.
-- Today's metrics are still computed live from transactional tables; historical
-- trend metrics (month/year totals, growth rates) read from this table. This is
-- sufficient for launch volumes and avoids materialized-view infrastructure.
--
-- Column semantics (all values are for the single day = snapshot_date):
--   total_revenue        sum of COMPLETED customer payments that day
--   platform_earnings    total_revenue x configurable commission rate (default 0.30)
--   total_orders         bookings created that day (non-deleted)
--   new_customers        customers whose FIRST order was that day (summable -> monthly new)
--   active_customers     distinct customers who ordered in the trailing 30 days as of day end (gauge)
--   returning_customers  distinct customers who ordered that day and had ordered before (gauge)
--   average_order_value  total_revenue / total_orders (0 when no orders)

CREATE TABLE IF NOT EXISTS analytics_daily_snapshot (
    snapshot_date        DATE PRIMARY KEY,
    total_revenue        NUMERIC(14,2) NOT NULL DEFAULT 0,
    platform_earnings    NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_orders         INT           NOT NULL DEFAULT 0,
    new_customers        INT           NOT NULL DEFAULT 0,
    active_customers     INT           NOT NULL DEFAULT 0,
    returning_customers  INT           NOT NULL DEFAULT 0,
    average_order_value  NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW()
);
