-- =====================================================================
-- MOLS V4 — Order-item stock reservation
--
-- Order-item creation previously only *checked* stock availability
-- against current physical quantity without recording anything, so two
-- orders (concurrent or simply sequential) could each pass the check and
-- together commit more demand than physically exists. The gap only
-- surfaced later, at shipment delivery, when the deduction could fail.
--
-- This adds a reservation counter per resource, tracking quantity
-- committed to still-open (non-terminal) order items. Order items track
-- whether their reservation is still outstanding so it is released
-- exactly once (order cancellation, completion, or item/order deletion).
-- =====================================================================

ALTER TABLE resources ADD COLUMN reserved_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE resources ADD CONSTRAINT chk_resources_reserved_quantity_non_negative
    CHECK (reserved_quantity >= 0);

ALTER TABLE order_items ADD COLUMN reservation_active BOOLEAN NOT NULL DEFAULT true;

-- Order items on already-closed orders never had a live reservation
-- under the new model; mark them released so a future edit/delete does
-- not try to release stock that was never counted as reserved.
UPDATE order_items oi
SET reservation_active = false
FROM orders o
WHERE oi.order_id = o.id
  AND o.status IN ('COMPLETED', 'CANCELLED');

-- Backfill reserved_quantity so resources with existing open order items
-- start at the correct committed total instead of 0.
UPDATE resources r
SET reserved_quantity = agg.total
FROM (
    SELECT oi.resource_id, SUM(oi.quantity) AS total
    FROM order_items oi
    WHERE oi.reservation_active = true
    GROUP BY oi.resource_id
) agg
WHERE r.id = agg.resource_id;
