package com.mls.logistics.service;

import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.ShipmentStatus;
import com.mls.logistics.domain.Unit;
import com.mls.logistics.dto.request.CreateOrderRequest;
import com.mls.logistics.dto.request.CreateOrderItemRequest;
import com.mls.logistics.dto.request.UpdateOrderRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for OrderService.
 *
 * Tests business logic without requiring database or Spring context.
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemService orderItemService;

    @Mock
    private ShipmentRepository shipmentRepository;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        Unit unit = new Unit();
        unit.setId(1L);

        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setUnit(unit);
        testOrder.setDateCreated(LocalDate.of(2024, 1, 1));
        testOrder.setStatus(OrderStatus.CREATED);
    }

    @Test
    void getAllOrders_ShouldReturnAllOrders() {
        // Given
        List<Order> orders = Arrays.asList(testOrder);
        when(orderRepository.findAll()).thenReturn(orders);

        // When
        List<Order> result = orderService.getAllOrders();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository, times(1)).findAll();
    }

    @Test
    void getOrderById_WhenExists_ShouldReturnOrder() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        Optional<Order> result = orderService.getOrderById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    void getOrderById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Order> result = orderService.getOrderById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(orderRepository, times(1)).findById(999L);
    }

    @Test
    void createOrder_WithValidRequest_ShouldReturnCreatedOrder() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(1L, 1L, LocalDate.now(), "CREATED");
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        Order result = orderService.createOrder(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void createOrderWithItems_WithValidItems_ShouldCreateOrderAndItems() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(1L, 1L, LocalDate.now(), "CREATED");
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        CreateOrderItemRequest item1 = new CreateOrderItemRequest(null, 10L, 2);
        CreateOrderItemRequest item2 = new CreateOrderItemRequest(null, 11L, 5);

        when(orderItemService.createOrderItem(any(CreateOrderItemRequest.class))).thenReturn(null);

        // When
        Order result = orderService.createOrderWithItems(request, List.of(item1, item2));

        // Then
        assertThat(result).isNotNull();
        verify(orderRepository, times(1)).save(any(Order.class));

        ArgumentCaptor<CreateOrderItemRequest> captor = ArgumentCaptor.forClass(CreateOrderItemRequest.class);
        verify(orderItemService, times(2)).createOrderItem(captor.capture());

        List<CreateOrderItemRequest> captured = captor.getAllValues();
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getOrderId()).isEqualTo(1L);
        assertThat(captured.get(1).getOrderId()).isEqualTo(1L);
        assertThat(captured).extracting(CreateOrderItemRequest::getResourceId).containsExactly(10L, 11L);
        assertThat(captured).extracting(CreateOrderItemRequest::getQuantity).containsExactly(2, 5);
    }

    @Test
    void deleteOrder_WhenExists_ShouldDeleteSuccessfully() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(shipmentRepository.existsByOrderIdAndStatus(1L, ShipmentStatus.DELIVERED)).thenReturn(false);
        when(shipmentRepository.findByOrderId(1L, Sort.unsorted())).thenReturn(List.of());
        doNothing().when(orderRepository).deleteById(1L);

        // When
        orderService.deleteOrder(1L);

        // Then
        verify(orderRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteOrder_WhenExists_ShouldReleaseItemReservationsBeforeCascadeDelete() {
        // Given: deleteById cascades via JPA and bypasses OrderItemService,
        // so deleteOrder must release reservations itself first.
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(shipmentRepository.existsByOrderIdAndStatus(1L, ShipmentStatus.DELIVERED)).thenReturn(false);
        when(shipmentRepository.findByOrderId(1L, Sort.unsorted())).thenReturn(List.of());
        doNothing().when(orderRepository).deleteById(1L);

        // When
        orderService.deleteOrder(1L);

        // Then
        verify(orderItemService, times(1)).releaseReservationsForOrder(1L);
    }

    @Test
    void deleteOrder_WithDeliveredShipment_ShouldThrowException() {
        // Given: even a non-COMPLETED order can have a DELIVERED shipment
        // (partial fulfillment) whose stock movements are part of the audit trail
        testOrder.setStatus(OrderStatus.PARTIALLY_SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(shipmentRepository.existsByOrderIdAndStatus(1L, ShipmentStatus.DELIVERED)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> orderService.deleteOrder(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("audit trail");

        verify(orderRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrder_WithNonDeliveredShipment_ShouldThrowException() {
        // Given: shipments no longer cascade-delete with their order (see
        // Order.shipments) — a PLANNED/IN_TRANSIT shipment still holds a
        // required FK to this order and must be deleted first.
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(shipmentRepository.existsByOrderIdAndStatus(1L, ShipmentStatus.DELIVERED)).thenReturn(false);
        when(shipmentRepository.findByOrderId(1L, Sort.unsorted()))
                .thenReturn(List.of(new com.mls.logistics.domain.Shipment()));

        // When & Then
        assertThatThrownBy(() -> orderService.deleteOrder(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("existing shipments");

        verify(orderRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrder_WhenNotExists_ShouldThrowException() {
        // Given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.deleteOrder(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found with id: '999'");

        verify(orderRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrder_WhenCompleted_ShouldThrowException() {
        // Given: completed orders carry fulfilled stock movements (audit trail)
        testOrder.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThatThrownBy(() -> orderService.deleteOrder(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("audit trail");

        verify(orderRepository, never()).deleteById(any());
    }

    @Test
    void updateOrder_WithValidTransition_ShouldApplyStatus() {
        // Given: CREATED -> VALIDATED is a legal transition
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus("VALIDATED");

        // When
        Order result = orderService.updateOrder(1L, request);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.VALIDATED);
    }

    @Test
    void updateOrder_WithInvalidTransition_ShouldThrowException() {
        // Given: COMPLETED is terminal — reopening is forbidden
        testOrder.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus("CREATED");

        // When & Then
        assertThatThrownBy(() -> orderService.updateOrder(1L, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid order status transition");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrder_WhenCancelled_ShouldReleaseItemReservations() {
        // Given: CREATED -> CANCELLED is a legal transition into a terminal state
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus("CANCELLED");

        // When
        Order result = orderService.updateOrder(1L, request);

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderItemService, times(1)).releaseReservationsForOrder(1L);
    }

    @Test
    void updateOrder_WithNonTerminalTransition_ShouldNotReleaseItemReservations() {
        // Given: CREATED -> VALIDATED stays open, nothing to release yet
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus("VALIDATED");

        // When
        orderService.updateOrder(1L, request);

        // Then
        verify(orderItemService, never()).releaseReservationsForOrder(any());
    }

    @Test
    void updateOrder_WithUnknownStatus_ShouldThrowException() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus("BANANA");

        // When & Then
        assertThatThrownBy(() -> orderService.updateOrder(1L, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown order status");

        verify(orderRepository, never()).save(any());
    }
}
