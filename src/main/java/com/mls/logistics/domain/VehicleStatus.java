package com.mls.logistics.domain;

import com.mls.logistics.exception.InvalidRequestException;

/**
 * Operational status of a {@link Vehicle}.
 *
 * <p>Vehicle statuses are descriptive (no ordered lifecycle), so any
 * status change is allowed. The parser also normalizes legacy values that
 * may exist in older databases (e.g. {@code MAINTENANCE} → {@code IN_REPAIR}).</p>
 */
public enum VehicleStatus {

    AVAILABLE,
    IN_USE,
    IN_REPAIR;

    /**
     * Parses a client-provided status string (case-insensitive, trimmed),
     * normalizing known legacy variants.
     *
     * @throws InvalidRequestException if the value is not a known status
     */
    public static VehicleStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Vehicle status is required.");
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "AVAILABLE" -> AVAILABLE;
            case "IN_USE" -> IN_USE;
            case "IN_REPAIR", "INREPAIR", "REPAIR", "REPAIRS", "MAINTENANCE", "IN_MAINTENANCE" -> IN_REPAIR;
            default -> throw new InvalidRequestException(
                "Unknown vehicle status: '" + value + "'. Valid values: AVAILABLE, IN_USE, IN_REPAIR.");
        };
    }
}
