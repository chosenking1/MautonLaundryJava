-- Records arrivals reported from further away than the expected radius.
--
-- Riders self-declare "arrived" and nothing checked it, so the status trail
-- could say a rider was at the customer's gate while they were kilometres away.
-- The physical handoffs are protected by one-time codes, so goods could never
-- change hands falsely -- but the customer was still told "your rider has
-- arrived" on the rider's word alone.
--
-- The check is deliberately soft. GPS fails in dense urban areas, and a rider
-- who genuinely is at the gate but blocked by a bad fix is a worse outcome than
-- an unverified arrival: they cannot complete the job at all. So an out-of-range
-- arrival is accepted and flagged, making the pattern reviewable instead of
-- invisible.
ALTER TABLE delivery_assignments
    ADD COLUMN IF NOT EXISTS arrival_flagged BOOLEAN;
ALTER TABLE delivery_assignments
    ADD COLUMN IF NOT EXISTS arrival_distance_m INTEGER;

-- Supports "show me flagged arrivals" without scanning the table.
CREATE INDEX IF NOT EXISTS idx_delivery_assignments_arrival_flagged
    ON delivery_assignments (arrival_flagged) WHERE arrival_flagged = TRUE;
