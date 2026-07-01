-- =====================================================================
-- MOLS V2 — Data integrity, audit immutability, and concurrency control
--
-- This migration hardens the baseline schema:
--   1. Normalizes legacy free-text status/type values so they can be read
--      as enums (@Enumerated(STRING)) and constrained with CHECKs.
--   2. Merges duplicate stock rows and enforces UNIQUE (resource, warehouse).
--   3. Adds NOT NULL / CHECK constraints that encode the business rules
--      at the database level (stock never negative, valid statuses only).
--   4. Adds stocks.version (optimistic locking) and movements.created_by
--      (audit actor, populated by JPA auditing going forward).
--   5. Adds indexes on foreign keys and the audit timeline.
--
-- Movements are an append-only audit trail: this migration never deletes
-- movement rows, and its CHECK constraints are added as NOT VALID so
-- historical rows are preserved as-is while new rows are enforced.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Normalize legacy data
-- ---------------------------------------------------------------------

-- Master data names: required going forward; backfill blanks.
UPDATE units       SET name = '(unnamed)' WHERE name IS NULL OR btrim(name) = '';
UPDATE warehouses  SET name = '(unnamed)' WHERE name IS NULL OR btrim(name) = '';
UPDATE resources   SET name = '(unnamed)' WHERE name IS NULL OR btrim(name) = '';

-- Order statuses → canonical enum values.
UPDATE orders SET status = upper(btrim(status)) WHERE status IS NOT NULL;
UPDATE orders SET status = 'CREATED'
WHERE status IS NULL OR status NOT IN ('CREATED', 'VALIDATED', 'COMPLETED', 'CANCELLED');

-- Shipment statuses → canonical enum values.
UPDATE shipments SET status = upper(btrim(status)) WHERE status IS NOT NULL;
UPDATE shipments SET status = 'PLANNED'
WHERE status IS NULL OR status NOT IN ('PLANNED', 'IN_TRANSIT', 'DELIVERED');

-- Vehicle statuses → canonical enum values (incl. legacy repair variants).
UPDATE vehicles SET status = upper(btrim(status)) WHERE status IS NOT NULL;
UPDATE vehicles SET status = 'IN_REPAIR'
WHERE status IN ('INREPAIR', 'REPAIR', 'REPAIRS', 'MAINTENANCE', 'IN_MAINTENANCE');
UPDATE vehicles SET status = 'AVAILABLE'
WHERE status IS NULL OR status NOT IN ('AVAILABLE', 'IN_USE', 'IN_REPAIR');

-- Movement types: normalize case only (content is audit history and is
-- otherwise never rewritten). Unknown legacy types, if any, must be fixed
-- manually — the enum mapping would fail to read them.
UPDATE movements SET type = upper(btrim(type))
WHERE type IS NOT NULL AND type <> upper(btrim(type));

-- ---------------------------------------------------------------------
-- 2. Stocks: merge duplicates per (resource, warehouse)
--    Keep the lowest id as canonical, repoint movements, sum quantities.
-- ---------------------------------------------------------------------
UPDATE movements m
SET stock_id = canon.min_id
FROM (
    SELECT resource_id, warehouse_id, MIN(id) AS min_id
    FROM stocks
    WHERE resource_id IS NOT NULL AND warehouse_id IS NOT NULL
    GROUP BY resource_id, warehouse_id
    HAVING COUNT(*) > 1
) canon
JOIN stocks dup
  ON dup.resource_id = canon.resource_id
 AND dup.warehouse_id = canon.warehouse_id
 AND dup.id <> canon.min_id
WHERE m.stock_id = dup.id;

UPDATE stocks s
SET quantity = agg.total
FROM (
    SELECT resource_id, warehouse_id, MIN(id) AS min_id, SUM(quantity) AS total
    FROM stocks
    WHERE resource_id IS NOT NULL AND warehouse_id IS NOT NULL
    GROUP BY resource_id, warehouse_id
    HAVING COUNT(*) > 1
) agg
WHERE s.id = agg.min_id;

DELETE FROM stocks s
USING (
    SELECT resource_id, warehouse_id, MIN(id) AS min_id
    FROM stocks
    WHERE resource_id IS NOT NULL AND warehouse_id IS NOT NULL
    GROUP BY resource_id, warehouse_id
    HAVING COUNT(*) > 1
) agg
WHERE s.resource_id = agg.resource_id
  AND s.warehouse_id = agg.warehouse_id
  AND s.id <> agg.min_id;

-- Stock rows without a resource or warehouse are unusable; remove them
-- only when they carry no audit history (movements are never orphaned).
DELETE FROM stocks s
WHERE (s.resource_id IS NULL OR s.warehouse_id IS NULL)
  AND NOT EXISTS (SELECT 1 FROM movements m WHERE m.stock_id = s.id);

-- Order items must reference an order and a resource; orphan rows are junk.
DELETE FROM order_items WHERE order_id IS NULL OR resource_id IS NULL;

-- ---------------------------------------------------------------------
-- 3. Constraints
-- ---------------------------------------------------------------------

-- Master data
ALTER TABLE units      ALTER COLUMN name SET NOT NULL;
ALTER TABLE warehouses ALTER COLUMN name SET NOT NULL;
ALTER TABLE resources  ALTER COLUMN name SET NOT NULL;

-- Orders
ALTER TABLE orders ALTER COLUMN status SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('CREATED', 'VALIDATED', 'COMPLETED', 'CANCELLED'));

-- Shipments
ALTER TABLE shipments ALTER COLUMN status SET NOT NULL;
ALTER TABLE shipments ADD CONSTRAINT chk_shipments_status
    CHECK (status IN ('PLANNED', 'IN_TRANSIT', 'DELIVERED'));

-- Vehicles
ALTER TABLE vehicles ALTER COLUMN status SET NOT NULL;
ALTER TABLE vehicles ADD CONSTRAINT chk_vehicles_status
    CHECK (status IN ('AVAILABLE', 'IN_USE', 'IN_REPAIR'));

-- Stocks: business rule "stock never negative" enforced at the DB level,
-- and one row per (resource, warehouse).
ALTER TABLE stocks ALTER COLUMN resource_id  SET NOT NULL;
ALTER TABLE stocks ALTER COLUMN warehouse_id SET NOT NULL;
ALTER TABLE stocks ADD CONSTRAINT chk_stocks_quantity_non_negative
    CHECK (quantity >= 0);
ALTER TABLE stocks ADD CONSTRAINT uq_stocks_resource_warehouse
    UNIQUE (resource_id, warehouse_id);

-- Order items
ALTER TABLE order_items ALTER COLUMN order_id    SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN resource_id SET NOT NULL;
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_quantity_positive
    CHECK (quantity > 0);

-- Movements: enforced for new rows only (NOT VALID) — historical audit
-- rows are preserved untouched, whatever their state.
ALTER TABLE movements ADD CONSTRAINT chk_movements_type
    CHECK (type IN ('ENTRY', 'EXIT', 'TRANSFER')) NOT VALID;
ALTER TABLE movements ADD CONSTRAINT chk_movements_quantity_positive
    CHECK (quantity > 0) NOT VALID;

-- ---------------------------------------------------------------------
-- 4. New columns
-- ---------------------------------------------------------------------

-- Optimistic locking for concurrent stock adjustments.
ALTER TABLE stocks ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Audit actor: who caused the movement. Nullable because historical rows
-- predate actor tracking; populated automatically from now on.
ALTER TABLE movements ADD COLUMN created_by VARCHAR(50);

-- ---------------------------------------------------------------------
-- 5. Indexes (foreign keys + audit timeline)
-- ---------------------------------------------------------------------
CREATE INDEX idx_stocks_warehouse      ON stocks (warehouse_id);
CREATE INDEX idx_orders_unit           ON orders (unit_id);
CREATE INDEX idx_orders_status         ON orders (status);
CREATE INDEX idx_order_items_order     ON order_items (order_id);
CREATE INDEX idx_order_items_resource  ON order_items (resource_id);
CREATE INDEX idx_shipments_order       ON shipments (order_id);
CREATE INDEX idx_shipments_vehicle     ON shipments (vehicle_id);
CREATE INDEX idx_shipments_warehouse   ON shipments (warehouse_id);
CREATE INDEX idx_movements_stock       ON movements (stock_id);
CREATE INDEX idx_movements_order       ON movements (order_id);
CREATE INDEX idx_movements_shipment    ON movements (shipment_id);
CREATE INDEX idx_movements_date_time   ON movements (date_time DESC);
