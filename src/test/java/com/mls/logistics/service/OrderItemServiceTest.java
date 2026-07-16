package com.mls.logistics.service;

import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.Resource;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.CreateOrderItemRequest;
import com.mls.logistics.dto.request.UpdateOrderItemRequest;
import com.mls.logistics.exception.InsufficientStockException;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderItemRepository;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.ShipmentItemRepository;
import com.mls.logistics.repository.StockRepository;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderItemService.
 *
 * Tests business logic without requiring database or Spring context.
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
class OrderItemServiceTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ShipmentItemRepository shipmentItemRepository;

    @InjectMocks
    private OrderItemService orderItemService;

    private OrderItem testOrderItem;
    private Order openOrder;
    private Resource testResource;
    private Warehouse orderWarehouse;
    private Stock testStock;

    @BeforeEach
    void setUp() {
        orderWarehouse = new Warehouse();
        orderWarehouse.setId(1L);

        openOrder = new Order();
        openOrder.setId(1L);
        openOrder.setStatus(OrderStatus.CREATED);
        openOrder.setWarehouse(orderWarehouse);

        testResource = new Resource();
        testResource.setId(1L);

        testStock = new Stock();
        testStock.setId(1L);
        testStock.setResource(testResource);
        testStock.setWarehouse(orderWarehouse);
        testStock.setQuantity(100);
        testStock.setReservedQuantity(0);

        testOrderItem = new OrderItem();
        testOrderItem.setId(1L);
        testOrderItem.setOrder(openOrder);
        testOrderItem.setResource(testResource);
        testOrderItem.setQuantity(10);
        testOrderItem.setReservationActive(true);
    }

    @Test
    void getAllOrderItems_ShouldReturnAllOrderItems() {
        // Given
        List<OrderItem> orderItems = Arrays.asList(testOrderItem);
        when(orderItemRepository.findAll()).thenReturn(orderItems);

        // When
        List<OrderItem> result = orderItemService.getAllOrderItems();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuantity()).isEqualTo(10);
        verify(orderItemRepository, times(1)).findAll();
    }

    @Test
    void getOrderItemById_WhenExists_ShouldReturnOrderItem() {
        // Given
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));

        // When
        Optional<OrderItem> result = orderItemService.getOrderItemById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getQuantity()).isEqualTo(10);
        verify(orderItemRepository, times(1)).findById(1L);
    }

    @Test
    void getOrderItemById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        when(orderItemRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<OrderItem> result = orderItemService.getOrderItemById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(orderItemRepository, times(1)).findById(999L);
    }

    @Test
    void createOrderItem_WithValidRequest_ShouldReserveStockAndReturnCreatedOrderItem() {
        // Given
        CreateOrderItemRequest request = new CreateOrderItemRequest(1L, 1L, 10);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(openOrder));
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(testStock));
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testOrderItem);

        // When
        OrderItem result = orderItemService.createOrderItem(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(10);
        // The reservation was recorded on the order's warehouse stock row before saving the item
        assertThat(testStock.getReservedQuantity()).isEqualTo(10);
        verify(stockRepository, times(1)).save(testStock);
        verify(orderItemRepository, times(1)).save(any(OrderItem.class));
    }

    @Test
    void createOrderItem_WhenRequestedExceedsUnreservedStock_ShouldThrowException() {
        // Given: 100 physical in the order's warehouse, but 95 already reserved -> only 5 truly free
        testStock.setReservedQuantity(95);
        CreateOrderItemRequest request = new CreateOrderItemRequest(1L, 1L, 10);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(openOrder));
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(testStock));

        // When & Then
        assertThatThrownBy(() -> orderItemService.createOrderItem(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("available in this warehouse (physical stock minus existing reservations): 5");

        verify(orderItemRepository, never()).save(any());
        verify(stockRepository, never()).save(any());
    }

    @Test
    void createOrderItem_WhenResourceHasNoStockInOrdersWarehouse_ShouldThrowException() {
        // Given: no stock row at all for this resource in the order's warehouse
        CreateOrderItemRequest request = new CreateOrderItemRequest(1L, 1L, 10);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(openOrder));
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderItemService.createOrderItem(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("no stock record");

        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void createOrderItem_WhenOrderIsClosed_ShouldThrowException() {
        // Given
        openOrder.setStatus(OrderStatus.COMPLETED);
        CreateOrderItemRequest request = new CreateOrderItemRequest(1L, 1L, 10);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(openOrder));

        // When & Then
        assertThatThrownBy(() -> orderItemService.createOrderItem(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("COMPLETED or CANCELLED");

        verifyNoInteractions(stockRepository);
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void createOrderItem_WhenOrderDoesNotExist_ShouldThrowException() {
        // Given
        CreateOrderItemRequest request = new CreateOrderItemRequest(999L, 1L, 10);
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderItemService.createOrderItem(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found with id: '999'");
    }

    @Test
    void updateOrderItem_WhenQuantityIncreases_ShouldReleaseOldAndReserveNewAmount() {
        // Given: item currently reserves 10; this warehouse's stock has 10 reserved (just this item) and 30 physical
        testStock.setQuantity(30);
        testStock.setReservedQuantity(10);
        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setQuantity(20);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(testStock));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        OrderItem result = orderItemService.updateOrderItem(1L, request);

        // Then: released 10 then reserved 20 -> net reserved is 20 (10 - 10 + 20)
        assertThat(testStock.getReservedQuantity()).isEqualTo(20);
        assertThat(result.getQuantity()).isEqualTo(20);
    }

    @Test
    void updateOrderItem_WhenNewQuantityExceedsAvailability_ShouldThrowAndRestoreOriginalReservation() {
        // Given: item reserves 10 out of 10 physical (nothing else free)
        testStock.setQuantity(10);
        testStock.setReservedQuantity(10);
        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setQuantity(50);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(testStock));

        // When & Then
        assertThatThrownBy(() -> orderItemService.updateOrderItem(1L, request))
                .isInstanceOf(InsufficientStockException.class);

        // The original 10-unit reservation must still be intact (released, then restored)
        assertThat(testStock.getReservedQuantity()).isEqualTo(10);
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void updateOrderItem_WhenNothingReservationAffectingChanges_ShouldNotTouchReservation() {
        // Given: only moving the item to a different order sourced from the SAME warehouse,
        // same resource/quantity — nothing about the reservation actually changes.
        Order otherOrder = new Order();
        otherOrder.setId(2L);
        otherOrder.setStatus(OrderStatus.CREATED);
        otherOrder.setWarehouse(orderWarehouse);

        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setOrderId(2L);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(otherOrder));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        OrderItem result = orderItemService.updateOrderItem(1L, request);

        // Then
        assertThat(result.getOrder().getId()).isEqualTo(2L);
        verifyNoInteractions(stockRepository);
    }

    @Test
    void updateOrderItem_WhenMovedToOrderInADifferentWarehouse_ShouldReReserveAgainstNewWarehouse() {
        // Given: moving the item to an order sourced from a different warehouse
        // must release from the old warehouse's stock and reserve in the new one.
        testStock.setReservedQuantity(10);

        Warehouse otherWarehouse = new Warehouse();
        otherWarehouse.setId(2L);
        Order otherOrder = new Order();
        otherOrder.setId(2L);
        otherOrder.setStatus(OrderStatus.CREATED);
        otherOrder.setWarehouse(otherWarehouse);

        Stock otherStock = new Stock();
        otherStock.setId(2L);
        otherStock.setResource(testResource);
        otherStock.setWarehouse(otherWarehouse);
        otherStock.setQuantity(50);
        otherStock.setReservedQuantity(0);

        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setOrderId(2L);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(otherOrder));
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(testStock));
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 2L)).thenReturn(Optional.of(otherStock));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        OrderItem result = orderItemService.updateOrderItem(1L, request);

        // Then
        assertThat(result.getOrder().getId()).isEqualTo(2L);
        assertThat(testStock.getReservedQuantity()).isEqualTo(0);
        assertThat(otherStock.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    void deleteOrderItem_WhenExists_ShouldReleaseReservationAndDelete() {
        // Given
        testStock.setReservedQuantity(10);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(testStock));
        doNothing().when(orderItemRepository).deleteById(1L);

        // When
        orderItemService.deleteOrderItem(1L);

        // Then
        assertThat(testStock.getReservedQuantity()).isEqualTo(0);
        assertThat(testOrderItem.isReservationActive()).isFalse();
        verify(orderItemRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteOrderItem_WhenNotExists_ShouldThrowException() {
        // Given
        when(orderItemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderItemService.deleteOrderItem(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("OrderItem not found with id: '999'");

        verify(orderItemRepository, never()).deleteById(any());
    }

    @Test
    void releaseReservation_WhenAlreadyReleased_ShouldBeNoOp() {
        // Given
        testOrderItem.setReservationActive(false);

        // When
        orderItemService.releaseReservation(testOrderItem);

        // Then
        verifyNoInteractions(stockRepository);
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void releaseReservationsForOrder_ShouldReleaseEveryActiveItem() {
        // Given
        OrderItem second = new OrderItem();
        second.setId(2L);
        second.setOrder(openOrder);
        second.setResource(testResource);
        second.setQuantity(5);
        second.setReservationActive(true);

        testStock.setReservedQuantity(15);
        when(orderItemRepository.findByOrderId(eq(1L), any(Sort.class))).thenReturn(List.of(testOrderItem, second));
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(testStock));

        // When
        orderItemService.releaseReservationsForOrder(1L);

        // Then
        assertThat(testStock.getReservedQuantity()).isEqualTo(0);
        assertThat(testOrderItem.isReservationActive()).isFalse();
        assertThat(second.isReservationActive()).isFalse();

        ArgumentCaptor<OrderItem> savedItems = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository, times(2)).save(savedItems.capture());
        assertThat(savedItems.getAllValues()).extracting(OrderItem::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void releasePartialReservation_WhenNotFullyDelivered_ShouldDrainOnlyThatQuantityAndKeepReservationActive() {
        // Given: item reserves 10; a shipment just delivered 4 of it (not the whole item)
        testStock.setReservedQuantity(10);
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(testStock));

        // When
        orderItemService.releasePartialReservation(testOrderItem, 4, false);

        // Then: only the delivered portion is released; the rest stays held
        assertThat(testStock.getReservedQuantity()).isEqualTo(6);
        assertThat(testOrderItem.isReservationActive()).isTrue();
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void releasePartialReservation_WhenFullyDelivered_ShouldDeactivateReservation() {
        // Given: item reserves 10; this call brings cumulative delivered up to the full quantity
        testStock.setReservedQuantity(10);
        when(stockRepository.findByResourceIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(testStock));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        orderItemService.releasePartialReservation(testOrderItem, 10, true);

        // Then
        assertThat(testStock.getReservedQuantity()).isEqualTo(0);
        assertThat(testOrderItem.isReservationActive()).isFalse();
        verify(orderItemRepository, times(1)).save(testOrderItem);
    }

    @Test
    void updateOrderItem_WhenQuantityBelowShipmentAllocation_ShouldReject() {
        // Given: 6 of this item's 10 units are already allocated to a shipment
        ShipmentItemRepository.OrderItemQuantity allocation = mock(ShipmentItemRepository.OrderItemQuantity.class);
        when(allocation.getTotal()).thenReturn(6L);
        when(shipmentItemRepository.sumQuantityByOrderItemIds(List.of(1L))).thenReturn(List.of(allocation));

        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setQuantity(5);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));

        // When & Then
        assertThatThrownBy(() -> orderItemService.updateOrderItem(1L, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already allocated to shipments");
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void deleteOrderItem_WhenAllocatedToShipment_ShouldReject() {
        // Given
        ShipmentItemRepository.OrderItemQuantity allocation = mock(ShipmentItemRepository.OrderItemQuantity.class);
        when(allocation.getTotal()).thenReturn(3L);
        when(shipmentItemRepository.sumQuantityByOrderItemIds(List.of(1L))).thenReturn(List.of(allocation));
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));

        // When & Then
        assertThatThrownBy(() -> orderItemService.deleteOrderItem(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already allocated to a shipment");
        verify(orderItemRepository, never()).deleteById(any());
        verifyNoInteractions(stockRepository);
    }
}
