package com.mls.logistics.service;

import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderItem;
import com.mls.logistics.domain.Resource;
import com.mls.logistics.domain.Shipment;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.ShipmentStatus;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.domain.Vehicle;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.CreateShipmentRequest;
import com.mls.logistics.dto.request.UpdateShipmentRequest;
import com.mls.logistics.exception.InsufficientStockException;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private OrderItemService orderItemService;

    @Mock
    private StockService stockService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private ShipmentService shipmentService;

    private Shipment testShipment;

    @BeforeEach
    void setUp() {
        Order order = new Order();
        order.setId(1L);

        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);

        testShipment = new Shipment();
        testShipment.setId(1L);
        testShipment.setOrder(order);
        testShipment.setVehicle(vehicle);
        testShipment.setWarehouse(warehouse);
        testShipment.setStatus(ShipmentStatus.PLANNED);
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
        CreateShipmentRequest request = new CreateShipmentRequest(1L, 1L, "PLANNED");

        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.CREATED);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        order.setWarehouse(warehouse);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(testShipment);

        // When
        Shipment result = shipmentService.createShipment(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.PLANNED);
        verify(shipmentRepository, times(1)).save(any(Shipment.class));
    }

    @Test
    void updateShipment_WhenTransitionToDelivered_ShouldDeductStockForEachOrderItem() {
        // Given
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(testShipment);
        when(shipmentRepository.findByOrderId(eq(1L), any())).thenReturn(List.of(testShipment));

        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.CREATED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));

        Resource r1 = new Resource();
        r1.setId(10L);
        OrderItem i1 = new OrderItem();
        i1.setId(100L);
        i1.setResource(r1);
        i1.setQuantity(3);

        Resource r2 = new Resource();
        r2.setId(11L);
        OrderItem i2 = new OrderItem();
        i2.setId(101L);
        i2.setResource(r2);
        i2.setQuantity(5);

        when(orderItemService.getOrderItemsByOrderId(eq(1L), any())).thenReturn(List.of(i1, i2));

        Stock s1 = new Stock();
        s1.setId(200L);
        Stock s2 = new Stock();
        s2.setId(201L);
        when(stockService.getStockByResourceAndWarehouse(10L, 1L)).thenReturn(Optional.of(s1));
        when(stockService.getStockByResourceAndWarehouse(11L, 1L)).thenReturn(Optional.of(s2));

        UpdateShipmentRequest req = new UpdateShipmentRequest();
        req.setStatus("DELIVERED");

        // When
        shipmentService.updateShipment(1L, req);

        // Then
        verify(stockService, times(1)).adjustStock(eq(200L), argThat(a -> a != null && Integer.valueOf(-3).equals(a.getDelta())));
        verify(stockService, times(1)).adjustStock(eq(201L), argThat(a -> a != null && Integer.valueOf(-5).equals(a.getDelta())));
        verify(orderService, times(1)).markOrderCompleted(1L);
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
    void updateShipment_WhenOrderNotFullyDelivered_ShouldNotFulfillOrCompleteOrder() {
        // Given
        Shipment otherShipment = new Shipment();
        otherShipment.setId(2L);
        otherShipment.setOrder(testShipment.getOrder());
        otherShipment.setVehicle(testShipment.getVehicle());
        otherShipment.setWarehouse(testShipment.getWarehouse());
        otherShipment.setStatus(ShipmentStatus.PLANNED);

        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(testShipment);
        when(shipmentRepository.findByOrderId(eq(1L), any())).thenReturn(List.of(testShipment, otherShipment));

        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.CREATED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));

        UpdateShipmentRequest req = new UpdateShipmentRequest();
        req.setStatus("DELIVERED");

        // When
        shipmentService.updateShipment(1L, req);

        // Then
        verify(stockService, never()).adjustStock(anyLong(), any());
        verify(orderService, never()).markOrderCompleted(anyLong());
    }

    @Test
    void createShipment_WhenOrderCompleted_ShouldReject() {
        // Given
        Order completed = new Order();
        completed.setId(1L);
        completed.setStatus(OrderStatus.COMPLETED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(completed));

        CreateShipmentRequest request = new CreateShipmentRequest(1L, 1L, "PLANNED");

        // When & Then
        assertThatThrownBy(() -> shipmentService.createShipment(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("COMPLETED or CANCELLED order");
        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    @Test
    void updateShipment_WhenDeliverAndNoStockRecord_ShouldThrowConflict() {
        // Given
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(testShipment));
        when(shipmentRepository.findByOrderId(eq(1L), any())).thenReturn(List.of(testShipment));

        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.CREATED);
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));

        Resource r1 = new Resource();
        r1.setId(10L);
        OrderItem i1 = new OrderItem();
        i1.setId(100L);
        i1.setResource(r1);
        i1.setQuantity(3);

        when(orderItemService.getOrderItemsByOrderId(eq(1L), any())).thenReturn(List.of(i1));
        when(stockService.getStockByResourceAndWarehouse(10L, 1L)).thenReturn(Optional.empty());

        UpdateShipmentRequest req = new UpdateShipmentRequest();
        req.setStatus("DELIVERED");

        // When & Then
        assertThatThrownBy(() -> shipmentService.updateShipment(1L, req))
                .isInstanceOf(InsufficientStockException.class);
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
