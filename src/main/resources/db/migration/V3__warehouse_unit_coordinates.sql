-- =====================================================================
-- MOLS V3 — Geographic coordinates for warehouses and units
--
-- Adds optional latitude/longitude to the two entities with a physical
-- location, enabling the logistics map planned for the React frontend
-- (warehouse/unit pins, shipment routes).
--
-- Columns are nullable: existing rows have no coordinates and capturing
-- them is optional. Valid ranges are enforced with CHECK constraints so
-- bad data cannot enter through any write path.
-- =====================================================================

ALTER TABLE warehouses ADD COLUMN latitude  double precision;
ALTER TABLE warehouses ADD COLUMN longitude double precision;

ALTER TABLE units ADD COLUMN latitude  double precision;
ALTER TABLE units ADD COLUMN longitude double precision;

ALTER TABLE warehouses
    ADD CONSTRAINT chk_warehouses_latitude  CHECK (latitude  IS NULL OR (latitude  BETWEEN -90  AND 90)),
    ADD CONSTRAINT chk_warehouses_longitude CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180));

ALTER TABLE units
    ADD CONSTRAINT chk_units_latitude  CHECK (latitude  IS NULL OR (latitude  BETWEEN -90  AND 90)),
    ADD CONSTRAINT chk_units_longitude CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180));
