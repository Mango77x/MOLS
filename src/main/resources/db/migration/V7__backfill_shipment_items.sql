-- =====================================================================
-- MOLS V7 — Backfill shipment_items for pre-V6 shipments
--
-- V6 introduced shipment_items to track which order items (and how much
-- of each) a shipment carries, and OrderItemService.shippingProgress()
-- computes deliveredQuantity/remainingQuantity exclusively from that
-- table. It didn't backfill it, though: any shipment created before V6
-- has zero shipment_items rows, so a DELIVERED pre-V6 shipment shows
-- deliveredQuantity = 0 on its order's items even though stock was
-- genuinely deducted and a Movement was recorded for it at the time.
--
-- Before V6, a shipment always carried its whole order (there was no
-- partial-shipment concept), so backfilling one shipment_items row per
-- order item — for its full ordered quantity — for every shipment that
-- has none yet is a faithful reconstruction of what V6's model would
-- have recorded had it existed at delivery time.
-- =====================================================================

INSERT INTO shipment_items (shipment_id, order_item_id, quantity)
SELECT s.id, oi.id, oi.quantity
FROM shipments s
JOIN order_items oi ON oi.order_id = s.order_id
WHERE NOT EXISTS (
    SELECT 1 FROM shipment_items si WHERE si.shipment_id = s.id
);
