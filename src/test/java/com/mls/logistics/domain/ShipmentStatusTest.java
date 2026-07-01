package com.mls.logistics.domain;

import com.mls.logistics.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Shipment status state machine.
 */
class ShipmentStatusTest {

    @Test
    void plannedAndInTransit_CanMoveFreely() {
        assertThat(ShipmentStatus.PLANNED.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(ShipmentStatus.PLANNED.canTransitionTo(ShipmentStatus.DELIVERED)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.PLANNED)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.DELIVERED)).isTrue();
    }

    @Test
    void delivered_IsTerminal() {
        assertThat(ShipmentStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(ShipmentStatus.DELIVERED.canTransitionTo(ShipmentStatus.PLANNED)).isFalse();
        assertThat(ShipmentStatus.DELIVERED.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isFalse();
        assertThat(ShipmentStatus.DELIVERED.canTransitionTo(ShipmentStatus.DELIVERED)).isTrue();
    }

    @Test
    void from_ParsesCaseInsensitively() {
        assertThat(ShipmentStatus.from("in_transit")).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void from_RejectsUnknownValues() {
        assertThatThrownBy(() -> ShipmentStatus.from("SHIPPED"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown shipment status");
    }
}
