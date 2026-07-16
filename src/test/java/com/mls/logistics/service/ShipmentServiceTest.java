package com.mls.logistics.service;

import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.domain.Resource;
import com.mls.logistics.domain.Shipment;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.ShipmentItem;
import com.mls.logistics.domain.ShipmentStatus;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.domain.Vehicle;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.CreateShipmentRequest;
import com.mls.logistics.dto.request.ShipmentItemLineRequest;
import com.mls.logistics.dto.request.UpdateShipmentRequest;
import com.mls.logistics.exception.InsufficientStockException;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.ShipmentItemRepository;
import com.mls.logistics.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShipmentService.
 *
 * Tests business logic without requiring database or Spring context.
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentItemRepository shipmentItemRepository;

    @Mock
    private OrderItemService orderItemService;

    @Mock
    private StockService stockService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private ShipmentService shipmentService;

    private Shipment testShipment;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = new Order();
        testOrder.setId(1L);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        testOrder.setWarehouse(warehouse);

        testShipment = new Shipment();
        testShipment.setId(1L);
        testShipment.setOrder(testOrder);
        testShipment.setVehicle(vehicle);
        testShipment.setWarehouse(warehouse);
        testShipment.setStatus(ShipmentStatus.PLANNED);
        testShipment.setItems(new ArrayList<>());
    }

    /**
     * Builds an order item. Does NOT stub {@code orderItemService.getOrderItemById} —
     * that lookup only happens when a request's {@code items} go through
     * {@code ShipmentService.buildItems}; tests that instead pre-populate
     * {@code testShipment.getItems()} directly never trigger it, and stubbing it
     * unconditionally would trip Mockito's unnecessary-stubbing check there.
     */
    private OrderItem orderItem(Long id, Long resourceId, int quantity) {
        Resource resource = new Resource();
        resource.setId(resourceId);
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrder(testOrder);
        item.setResource(resource);
        item.setQuantity(quantity);
        return item;
    }

    /** Mocks a {@link ShipmentItem} linking {@code testShipment} to the given order item/quantity. */
    private ShipmentItem shipmentItem(OrderItem item, int quantity) {
        ShipmentItem si = new ShipmentItem();
        si.setShipment(testShipment);
        si.setOrderItem(item);
        si.setQuantity(quantity);
        return si;
    }

    /** A mocked aggregate row; production code here only ever reads {@code getTotal()}. */
    private ShipmentItemRepository.OrderItemQuantity quantityRow(long total) {
        ShipmentItemRepository.OrderItemQuantity row = mock(ShipmentItemRepository.OrderItemQuantity.class);
        when(row.getTotal()).thenReturn(total);
        return row;
    }

    @Test
    void getAllShipments_ShouldReturnAllShipments() {
        // Given
        List<Shipment> shipments = Arrays.asList(testShipment);
        when(shipmentRepository.findAll()).thenReturn(shipments);

        // When
        List<Shipment> result = shipmentService.getAllShipments();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ShipmentStatus.PLANNED);
        verify(shipmentRepository, times(1)).findAll();
    }

    @Test
    void getShipmentById_WhenExists_ShouldReturnShipment() {
        // Given
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));

        // When
        Optional<Shipment> result = shipmentService.getShipmentById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(ShipmentStatus.PLANNED);
        verify(shipmentRepository, times(1)).findById(1L);
    }

    @Test
    void getShipmentById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        when(shipmentRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Shipment> result = shipmentService.getShipmentById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(shipmentRepository, times(1)).findById(999L);
    }

    @Test
    void createShipment_WithValidRequest_ShouldReturnCreatedShipment() {
        // Given
        testOrder.setStatus(OrderStatus.CREATED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(testOrder));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(testShipment);

        OrderItem item = orderItem(100L, 10L, 3);
        when(orderItemService.getOrderItemById(100L)).thenReturn(Optional.of(item));
        when(shipmentItemRepository.sumQuantityByOrderItemIds(List.of(100L))).thenReturn(List.of());

        CreateShipmentRequest request = new CreateShipmentRequest(1L, 1L, "PLANNED",
                List.of(new ShipmentItemLineRequest(100L, 3)));

        // When
        Shipment result = shipmentService.createShipment(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.PLANNED);
        verify(shipmentRepository, times(1)).save(any(Shipment.class));

        ArgumentCaptor<List<ShipmentItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(shipmentItemRepository, times(1)).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getQuantity()).isEqualTo(3);
        assertThat(captor.getValue().get(0).getOrderItem()).isEqualTo(item);
    }

    @Test
    void createShipment_WhenLineExceedsRemainingQuantity_ShouldReject() {
        // Given
        testOrder.setStatus(OrderStatus.CREATED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(testOrder));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(testShipment);

        OrderItem item = orderItem(100L, 10L, 3);
        when(orderItemService.getOrderItemById(100L)).thenReturn(Optional.of(item));
        // Already 2 allocated to another shipment; only 1 remains.
        List<ShipmentItemRepository.OrderItemQuantity> allocatedRows = List.of(quantityRow(2));
        when(shipmentItemRepository.sumQuantityByOrderItemIds(List.of(100L))).thenReturn(allocatedRows);

        CreateShipmentRequest request = new CreateShipmentRequest(1L, 1L, "PLANNED",
                List.of(new ShipmentItemLineRequest(100L, 2)));

        // When & Then
        assertThatThrownBy(() -> shipmentService.createShipment(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("only 1 remains unallocated");
        verify(shipmentItemRepository, never()).saveAll(any());
    }

    @Test
    void updateShipment_WhenTransitionToDelivered_ShouldDeductStockForEachOrderItem() {
        // Given
        OrderItem i1 = orderItem(100L, 10L, 3);
        OrderItem i2 = orderItem(101L, 11L, 5);
        testShipment.getItems().add(shipmentItem(i1, 3));
        testShipment.getItems().add(shipmentItem(i2, 5));

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(testShipment);

        testOrder.setStatus(OrderStatus.CREATED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(testOrder));
        when(orderItemService.getOrderItemsByOrderId(eq(1L), any())).thenReturn(List.of(i1, i2));

        Stock s1 = new Stock();
        s1.setId(200L);
        Stock s2 = new Stock();
        s2.setId(201L);
        when(stockService.getStockByResourceAndWarehouse(10L, 1L)).thenReturn(Optional.of(s1));
        when(stockService.getStockByResourceAndWarehouse(11L, 1L)).thenReturn(Optional.of(s2));

        // This shipment is the only one carrying either item, and each line covers
        // the item's full quantity, so both items end up fully delivered.
        List<ShipmentItemRepository.OrderItemQuantity> delivered100 = List.of(quantityRow(3));
        List<ShipmentItemRepository.OrderItemQuantity> delivered101 = List.of(quantityRow(5));
        when(shipmentItemRepository.sumDeliveredQuantityByOrderItemIds(List.of(100L))).thenReturn(delivered100);
        when(shipmentItemRepository.sumDeliveredQuantityByOrderItemIds(List.of(101L))).thenReturn(delivered101);

        UpdateShipmentRequest req = new UpdateShipmentRequest();
        req.setStatus("DELIVERED");

        // When
        shipmentService.updateShipment(1L, req);

        // Then
        verify(stockService, times(1)).adjustStock(eq(200L), argThat(a -> a != null && Integer.valueOf(-3).equals(a.getDelta())));
        verify(stockService, times(1)).adjustStock(eq(201L), argThat(a -> a != null && Integer.valueOf(-5).equals(a.getDelta())));
        verify(orderItemService, times(1)).releasePartialReservation(i1, 3, true);
        verify(orderItemService, times(1)).releasePartialReservation(i2, 5, true);
        verify(orderService, times(1)).markOrderCompleted(1L);
        verify(orderService, never()).markOrderPartiallyShipped(anyLong());
        verify(shipmentRepository, times(1)).save(any(Shipment.class));
    }

    @Test
    void updateShipment_WhenAlreadyDelivered_ShouldNotDeductStockAgain() {
        // Given
        testShipment.setStatus(ShipmentStatus.DELIVERED);
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(testShipment);

        UpdateShipmentRequest req = new UpdateShipmentRequest();
        req.setStatus("DELIVERED");

        // When
        shipmentService.updateShipment(1L, req);

        // Then
        verify(stockService, never()).adjustStock(anyLong(), any());
        verify(orderItemService, never()).getOrderItemsByOrderId(anyLong(), any());
        verify(orderService, never()).markOrderCompleted(anyLong());
        verify(shipmentRepository, times(1)).save(any(Shipment.class));
    }

    @Test
    void updateShipment_WhenOrderPartiallyDelivered_ShouldFulfillOnlyThisShipmentAndMarkOrderPartiallyShipped() {
        // Given: order has two items; this shipment carries only the first one in full.
        OrderItem i1 = orderItem(100L, 10L, 3);
        OrderItem i2 = orderItem(101L, 11L, 5);
        testShipment.getItems().add(shipmentItem(i1, 3));

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(testShipment);

        testOrder.setStatus(OrderStatus.CREATED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(testOrder));
        when(orderItemService.getOrderItemsByOrderId(eq(1L), any())).thenReturn(List.of(i1, i2));

        Stock s1 = new Stock();
        s1.setId(200L);
        when(stockService.getStockByResourceAndWarehouse(10L, 1L)).thenReturn(Optional.of(s1));

        List<ShipmentItemRepository.OrderItemQuantity> delivered100 = List.of(quantityRow(3));
        when(shipmentItemRepository.sumDeliveredQuantityByOrderItemIds(List.of(100L))).thenReturn(delivered100);
        when(shipmentItemRepository.sumDeliveredQuantityByOrderItemIds(List.of(101L))).thenReturn(List.of()); // i2 was never carried by any shipment

        UpdateShipmentRequest req = new UpdateShipmentRequest();
        req.setStatus("DELIVERED");

        // When
        shipmentService.updateShipment(1L, req);

        // Then: only i1's stock moves; i2 (and thus the order as a whole) is untouched.
        verify(stockService, times(1)).adjustStock(eq(200L), argThat(a -> a != null && Integer.valueOf(-3).equals(a.getDelta())));
        verify(stockService, never()).getStockByResourceAndWarehouse(11L, 1L);
        verify(orderItemService, times(1)).releasePartialReservation(i1, 3, true);
        verify(orderService, never()).markOrderCompleted(anyLong());
        verify(orderService, times(1)).markOrderPartiallyShipped(1L);
    }

    @Test
    void createShipment_WhenOrderCompleted_ShouldReject() {
        // Given
        Order completed = new Order();
        completed.setId(1L);
        completed.setStatus(OrderStatus.COMPLETED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(completed));

        CreateShipmentRequest request = new CreateShipmentRequest(1L, 1L, "PLANNED",
                List.of(new ShipmentItemLineRequest(100L, 3)));

        // When & Then
        assertThatThrownBy(() -> shipmentService.createShipment(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("COMPLETED or CANCELLED order");
        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    @Test
    void updateShipment_WhenDeliverAndNoStockRecord_ShouldThrowConflict() {
        // Given
        OrderItem i1 = orderItem(100L, 10L, 3);
        testShipment.getItems().add(shipmentItem(i1, 3));

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));

        testOrder.setStatus(OrderStatus.CREATED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(testOrder));
        when(stockService.getStockByResourceAndWarehouse(10L, 1L)).thenReturn(Optional.empty());

        UpdateShipmentRequest req = new UpdateShipmentRequest();
        req.setStatus("DELIVERED");

        // When & Then
        assertThatThrownBy(() -> shipmentService.updateShipment(1L, req))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void updateShipment_WhenReplacingItemsOnDeliveredShipment_ShouldReject() {
        // Given
        testShipment.setStatus(ShipmentStatus.DELIVERED);
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));

        UpdateShipmentRequest req = new UpdateShipmentRequest();
        req.setItems(List.of(new ShipmentItemLineRequest(100L, 1)));

        // When & Then
        assertThatThrownBy(() -> shipmentService.updateShipment(1L, req))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("DELIVERED shipment");
        verify(shipmentItemRepository, never()).deleteAll(any());
    }

    @Test
    void deleteShipment_WhenExists_ShouldDeleteSuccessfully() {
        // Given
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));
        doNothing().when(shipmentRepository).deleteById(1L);

        // When
        shipmentService.deleteShipment(1L);

        // Then
        verify(shipmentRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteShipment_WhenNotExists_ShouldThrowException() {
        // Given
        when(shipmentRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> shipmentService.deleteShipment(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Shipment not found with id: '999'");

        verify(shipmentRepository, never()).deleteById(any());
    }

    @Test
    void deleteShipment_WhenDelivered_ShouldThrowException() {
        // Given: delivered shipments already produced audited stock movements
        testShipment.setStatus(ShipmentStatus.DELIVERED);
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));

        // When & Then
        assertThatThrownBy(() -> shipmentService.deleteShipment(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("audit trail");

        verify(shipmentRepository, never()).deleteById(any());
    }

    @Test
    void updateShipment_RevertingDelivered_ShouldThrowInvalidTransition() {
        // Given: DELIVERED is terminal in the state machine
        testShipment.setStatus(ShipmentStatus.DELIVERED);
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));

        UpdateShipmentRequest request = new UpdateShipmentRequest();
        request.setStatus("PLANNED");

        // When & Then
        assertThatThrownBy(() -> shipmentService.updateShipment(1L, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid shipment status transition");

        verify(shipmentRepository, never()).save(any());
    }
}
