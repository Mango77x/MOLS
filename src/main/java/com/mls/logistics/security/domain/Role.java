package com.mls.logistics.security.domain;

import com.mls.logistics.exception.InvalidRequestException;

/**
 * Roles available in the MOLS system.
 *
 * ADMIN    - Full access: read, create, update, delete all entities
 * OPERATOR - Operational access: can work on Orders/Shipments, but cannot administer master data
 * AUDITOR  - Read-only access with strong audit visibility
 */
public enum Role {
    ADMIN,
    OPERATOR,
    AUDITOR;

    /**
     * Parses a role from a request value, same friendly-error convention as
     * OrderStatus/ShipmentStatus/VehicleStatus.from(String) — an unknown
     * value gets a clear 400 message instead of falling through to Jackson's
     * generic enum-deserialization error.
     */
    public static Role from(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Role is required.");
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                "Unknown role: '" + value + "'. Valid values: ADMIN, OPERATOR, AUDITOR.");
        }
    }
}