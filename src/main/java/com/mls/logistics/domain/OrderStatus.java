package com.mls.logistics.domain;

import com.mls.logistics.exception.InvalidRequestException;

/**
 * Lifecycle status of an {@link Order}.
 *
 * <p>Valid transitions (enforced by {@code OrderService}):</p>
 * <pre>
 * CREATED   → VALIDATED, COMPLETED, CANCELLED
 * VALIDATED → COMPLETED, CANCELLED
 * COMPLETED → (terminal)
 * CANCELLED → (terminal)
 * </pre>
 *
 * <p>{@code CREATED → COMPLETED} is allowed because fulfillment is driven by
 * shipment delivery, which may complete an order that was never explicitly
 * validated.</p>
 */
public enum OrderStatus {

    CREATED,
    VALIDATED,
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
            case CREATED -> next == VALIDATED || next == COMPLETED || next == CANCELLED;
            case VALIDATED -> next == COMPLETED || next == CANCELLED;
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
                "Unknown order status: '" + value + "'. Valid values: CREATED, VALIDATED, COMPLETED, CANCELLED.");
        }
    }
}
