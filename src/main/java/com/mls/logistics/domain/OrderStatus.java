package com.mls.logistics.domain;

import com.mls.logistics.exception.InvalidRequestException;

/**
 * Lifecycle status of an {@link Order}.
 *
 * <p>Valid transitions (enforced by {@code OrderService}):</p>
 * <pre>
 * CREATED           → VALIDATED, PARTIALLY_SHIPPED, COMPLETED, CANCELLED
 * VALIDATED         → PARTIALLY_SHIPPED, COMPLETED, CANCELLED
 * PARTIALLY_SHIPPED → COMPLETED
 * COMPLETED         → (terminal)
 * CANCELLED         → (terminal)
 * </pre>
 *
 * <p>{@code CREATED → COMPLETED} is allowed because fulfillment is driven by
 * shipment delivery, which may complete an order that was never explicitly
 * validated. {@code PARTIALLY_SHIPPED} reflects some (not all) order items
 * having been delivered by at least one shipment; it cannot move to
 * {@code CANCELLED} because stock has already physically left the warehouse
 * for part of the order and there is no compensating-reversal flow.</p>
 */
public enum OrderStatus {

    CREATED,
    VALIDATED,
    PARTIALLY_SHIPPED,
    COMPLETED,
    CANCELLED;

    /**
     * Returns true when no further transitions are allowed from this status.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * Returns true when moving from this status to {@code next} is a valid
     * transition. Staying in the same status is always allowed (idempotent
     * updates that do not change the status).
     */
    public boolean canTransitionTo(OrderStatus next) {
        if (next == null) {
            return false;
        }
        if (next == this) {
            return true;
        }
        return switch (this) {
            case CREATED -> next == VALIDATED || next == PARTIALLY_SHIPPED || next == COMPLETED || next == CANCELLED;
            case VALIDATED -> next == PARTIALLY_SHIPPED || next == COMPLETED || next == CANCELLED;
            case PARTIALLY_SHIPPED -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /**
     * Parses a client-provided status string (case-insensitive, trimmed).
     *
     * @throws InvalidRequestException if the value is not a known status
     */
    public static OrderStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Order status is required.");
        }
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                "Unknown order status: '" + value + "'. Valid values: CREATED, VALIDATED, PARTIALLY_SHIPPED, COMPLETED, CANCELLED.");
        }
    }
}
