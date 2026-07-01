package com.mls.logistics.domain;

import com.mls.logistics.exception.InvalidRequestException;

/**
 * Lifecycle status of a {@link Shipment}.
 *
 * <p>Valid transitions (enforced by {@code ShipmentService}):</p>
 * <pre>
 * PLANNED    → IN_TRANSIT, DELIVERED
 * IN_TRANSIT → PLANNED, DELIVERED
 * DELIVERED  → (terminal — reverting a delivery would corrupt stock accounting)
 * </pre>
 */
public enum ShipmentStatus {

    PLANNED,
    IN_TRANSIT,
    DELIVERED;

    /**
     * Returns true when no further transitions are allowed from this status.
     */
    public boolean isTerminal() {
        return this == DELIVERED;
    }

    /**
     * Returns true when moving from this status to {@code next} is a valid
     * transition. Staying in the same status is always allowed.
     */
    public boolean canTransitionTo(ShipmentStatus next) {
        if (next == null) {
            return false;
        }
        if (next == this) {
            return true;
        }
        return switch (this) {
            case PLANNED, IN_TRANSIT -> true;
            case DELIVERED -> false;
        };
    }

    /**
     * Parses a client-provided status string (case-insensitive, trimmed).
     *
     * @throws InvalidRequestException if the value is not a known status
     */
    public static ShipmentStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Shipment status is required.");
        }
        try {
            return ShipmentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                "Unknown shipment status: '" + value + "'. Valid values: PLANNED, IN_TRANSIT, DELIVERED.");
        }
    }
}
