package com.mls.logistics.service;

import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.Resource;
import com.mls.logistics.dto.request.CreateOrderItemRequest;
import com.mls.logistics.dto.request.UpdateOrderItemRequest;
import com.mls.logistics.exception.InsufficientStockException;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderItemRepository;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.ResourceRepository;
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
    private ResourceRepository resourceRepository;

    @Mock
    private StockService stockService;

    @InjectMocks
    private OrderItemService orderItemService;

    private OrderItem testOrderItem;
    private Order openOrder;
    private Resource testResource;

    @BeforeEach
    void setUp() {
        openOrder = new Order();
        openOrder.setId(1L);
        openOrder.setStatus(OrderStatus.CREATED);

        testResource = new Resource();
        testResource.setId(1L);
        testResource.setReservedQuantity(0);

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
        when(resourceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testResource));
        when(stockService.getTotalAvailableQuantity(1L)).thenReturn(100);
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testOrderItem);

        // When
        OrderItem result = orderItemService.createOrderItem(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(10);
        // The reservation was recorded on the resource before saving the item
        assertThat(testResource.getReservedQuantity()).isEqualTo(10);
        verify(resourceRepository, times(1)).save(testResource);
        verify(orderItemRepository, times(1)).save(any(OrderItem.class));
    }

    @Test
    void createOrderItem_WhenRequestedExceedsUnreservedStock_ShouldThrowException() {
        // Given: 100 physical, but 95 already reserved by other order items -> only 5 truly free
        testResource.setReservedQuantity(95);
        CreateOrderItemRequest request = new CreateOrderItemRequest(1L, 1L, 10);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(openOrder));
        when(resourceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testResource));
        when(stockService.getTotalAvailableQuantity(1L)).thenReturn(100);

        // When & Then
        assertThatThrownBy(() -> orderItemService.createOrderItem(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("available (physical stock minus existing reservations): 5");

        verify(orderItemRepository, never()).save(any());
        verify(resourceRepository, never()).save(any());
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

        verifyNoInteractions(resourceRepository);
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
        // Given: item currently reserves 10; resource has 10 reserved (just this item) and 30 physical
        testResource.setReservedQuantity(10);
        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setQuantity(20);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));
        when(resourceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testResource));
        when(stockService.getTotalAvailableQuantity(1L)).thenReturn(30);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        OrderItem result = orderItemService.updateOrderItem(1L, request);

        // Then: released 10 then reserved 20 -> net reserved is 20 (10 - 10 + 20)
        assertThat(testResource.getReservedQuantity()).isEqualTo(20);
        assertThat(result.getQuantity()).isEqualTo(20);
    }

    @Test
    void updateOrderItem_WhenNewQuantityExceedsAvailability_ShouldThrowAndRestoreOriginalReservation() {
        // Given: item reserves 10 out of 10 physical (nothing else free)
        testResource.setReservedQuantity(10);
        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setQuantity(50);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));
        when(resourceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testResource));
        when(stockService.getTotalAvailableQuantity(1L)).thenReturn(10);

        // When & Then
        assertThatThrownBy(() -> orderItemService.updateOrderItem(1L, request))
                .isInstanceOf(InsufficientStockException.class);

        // The original 10-unit reservation must still be intact (released, then restored)
        assertThat(testResource.getReservedQuantity()).isEqualTo(10);
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void updateOrderItem_WhenNothingReservationAffectingChanges_ShouldNotTouchReservation() {
        // Given: only moving the item to a different (open) order, same resource/quantity
        Order otherOrder = new Order();
        otherOrder.setId(2L);
        otherOrder.setStatus(OrderStatus.CREATED);

        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setOrderId(2L);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(otherOrder));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        OrderItem result = orderItemService.updateOrderItem(1L, request);

        // Then
        assertThat(result.getOrder().getId()).isEqualTo(2L);
        verifyNoInteractions(resourceRepository);
    }

    @Test
    void deleteOrderItem_WhenExists_ShouldReleaseReservationAndDelete() {
        // Given
        testResource.setReservedQuantity(10);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));
        when(resourceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testResource));
        doNothing().when(orderItemRepository).deleteById(1L);

        // When
        orderItemService.deleteOrderItem(1L);

        // Then
        assertThat(testResource.getReservedQuantity()).isEqualTo(0);
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
        verifyNoInteractions(resourceRepository);
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

        testResource.setReservedQuantity(15);
        when(orderItemRepository.findByOrderId(eq(1L), any(Sort.class))).thenReturn(List.of(testOrderItem, second));
        when(resourceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testResource));

        // When
        orderItemService.releaseReservationsForOrder(1L);

        // Then
        assertThat(testResource.getReservedQuantity()).isEqualTo(0);
        assertThat(testOrderItem.isReservationActive()).isFalse();
        assertThat(second.isReservationActive()).isFalse();

        ArgumentCaptor<OrderItem> savedItems = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository, times(2)).save(savedItems.capture());
        assertThat(savedItems.getAllValues()).extracting(OrderItem::getId).containsExactlyInAnyOrder(1L, 2L);
    }
}
