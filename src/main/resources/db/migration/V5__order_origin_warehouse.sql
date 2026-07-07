-- =====================================================================
-- MOLS V5 — Order origin warehouse
--
-- Previously, an order's items reserved stock against a resource's total
-- across ALL warehouses, but delivery always deducted from ONE warehouse
-- chosen independently and only at shipment time — so a validated order
-- could still fail at delivery if that specific warehouse didn't have
-- enough. Fixing this properly requires knowing the order's warehouse
-- from the moment items are added, not just at shipment time.
--
-- Orders now carry a fixed origin warehouse from creation. Shipments
-- inherit it automatically instead of choosing their own.
-- =====================================================================

ALTER TABLE orders ADD COLUMN warehouse_id BIGINT;

-- Backfill existing orders: prefer the warehouse of an existing shipment
-- (the real-world source that was already used), falling back to the
-- earliest-created warehouse for orders that never got one.
UPDATE orders o
SET warehouse_id = COALESCE(
    (SELECT s.warehouse_id FROM shipments s WHERE s.order_id = o.id ORDER BY s.id LIMIT 1),
    (SELECT MIN(id) FROM warehouses)
)
WHERE o.warehouse_id IS NULL;

ALTER TABLE orders ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT fk_orders_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);
CREATE INDEX idx_orders_warehouse ON orders (warehouse_id);

-- Reservation moves from resources (global across warehouses) to stocks
-- (specific to one resource + one warehouse), matching the new per-order
-- warehouse constraint.
ALTER TABLE resources DROP CONSTRAINT IF EXISTS chk_resources_reserved_quantity_non_negative;
ALTER TABLE resources DROP COLUMN IF EXISTS reserved_quantity;

ALTER TABLE stocks ADD COLUMN reserved_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE stocks ADD CONSTRAINT chk_stocks_reserved_quantity_non_negative
    CHECK (reserved_quantity >= 0);

-- Backfill reserved_quantity from existing open order items, now that we
-- know each order's warehouse.
UPDATE stocks s
SET reserved_quantity = agg.total
FROM (
    SELECT o.warehouse_id, oi.resource_id, SUM(oi.quantity) AS total
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    WHERE oi.reservation_active = true
    GROUP BY o.warehouse_id, oi.resource_id
) agg
WHERE s.warehouse_id = agg.warehouse_id
  AND s.resource_id = agg.resource_id;
