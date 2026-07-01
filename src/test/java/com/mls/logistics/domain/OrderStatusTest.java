package com.mls.logistics.domain;

import com.mls.logistics.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Order status state machine.
 */
class OrderStatusTest {

    @Test
    void createdOrder_CanBeValidatedCompletedOrCancelled() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.VALIDATED)).isTrue();
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void validatedOrder_CanBeCompletedOrCancelled_ButNotReopened() {
        assertThat(OrderStatus.VALIDATED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.VALIDATED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.VALIDATED.canTransitionTo(OrderStatus.CREATED)).isFalse();
    }

    @Test
    void terminalStatuses_AllowNoTransitions() {
        for (OrderStatus terminal : new OrderStatus[]{OrderStatus.COMPLETED, OrderStatus.CANCELLED}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (OrderStatus next : OrderStatus.values()) {
                if (next != terminal) {
                    assertThat(terminal.canTransitionTo(next))
                            .as("%s -> %s must be forbidden", terminal, next)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void sameStatus_IsAlwaysAllowed() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).isTrue();
        }
    }

    @Test
    void from_ParsesCaseInsensitively() {
        assertThat(OrderStatus.from(" created ")).isEqualTo(OrderStatus.CREATED);
        assertThat(OrderStatus.from("COMPLETED")).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void from_RejectsUnknownValues() {
        assertThatThrownBy(() -> OrderStatus.from("SHIPPED"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown order status");

        assertThatThrownBy(() -> OrderStatus.from(null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
