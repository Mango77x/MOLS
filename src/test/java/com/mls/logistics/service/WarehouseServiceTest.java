package com.mls.logistics.service;

import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.CreateWarehouseRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.ShipmentRepository;
import com.mls.logistics.repository.StockRepository;
import com.mls.logistics.repository.WarehouseRepository;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for WarehouseService.
 *
 * Tests business logic without requiring database or Spring context.
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    private Warehouse testWarehouse;

    @BeforeEach
    void setUp() {
        testWarehouse = new Warehouse();
        testWarehouse.setId(1L);
        testWarehouse.setName("Test Warehouse");
        testWarehouse.setLocation("Test Location");
    }

    @Test
    void getAllWarehouses_ShouldReturnAllWarehouses() {
        // Given
        List<Warehouse> warehouses = Arrays.asList(testWarehouse);
        when(warehouseRepository.findAll()).thenReturn(warehouses);

        // When
        List<Warehouse> result = warehouseService.getAllWarehouses();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Warehouse");
        verify(warehouseRepository, times(1)).findAll();
    }

    @Test
    void getWarehouseById_WhenExists_ShouldReturnWarehouse() {
        // Given
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));

        // When
        Optional<Warehouse> result = warehouseService.getWarehouseById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Warehouse");
        verify(warehouseRepository, times(1)).findById(1L);
    }

    @Test
    void getWarehouseById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Warehouse> result = warehouseService.getWarehouseById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(warehouseRepository, times(1)).findById(999L);
    }

    @Test
    void createWarehouse_WithValidRequest_ShouldReturnCreatedWarehouse() {
        // Given
        CreateWarehouseRequest request = new CreateWarehouseRequest("New Warehouse", "New Location");
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        Warehouse result = warehouseService.createWarehouse(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Warehouse");
        verify(warehouseRepository, times(1)).save(any(Warehouse.class));
    }

    @Test
    void deleteWarehouse_WhenExists_ShouldDeleteSuccessfully() {
        // Given
        when(warehouseRepository.existsById(1L)).thenReturn(true);
        doNothing().when(warehouseRepository).deleteById(1L);

        // When
        warehouseService.deleteWarehouse(1L);

        // Then
        verify(warehouseRepository, times(1)).existsById(1L);
        verify(warehouseRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteWarehouse_WhenNotExists_ShouldThrowException() {
        // Given
        when(warehouseRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> warehouseService.deleteWarehouse(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Warehouse not found with id: '999'");

        verify(warehouseRepository, times(1)).existsById(999L);
        verify(warehouseRepository, never()).deleteById(any());
    }

    @Test
    void deleteWarehouse_WithExistingStock_ShouldThrowException() {
        // Given
        when(warehouseRepository.existsById(1L)).thenReturn(true);
        when(stockRepository.existsByWarehouseId(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> warehouseService.deleteWarehouse(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("existing stock");

        verify(warehouseRepository, never()).deleteById(any());
    }

    @Test
    void deleteWarehouse_WithExistingOrders_ShouldThrowException() {
        // Given
        when(warehouseRepository.existsById(1L)).thenReturn(true);
        when(orderRepository.existsByWarehouseId(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> warehouseService.deleteWarehouse(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("existing orders");

        verify(warehouseRepository, never()).deleteById(any());
    }

    @Test
    void deleteWarehouse_WithExistingShipments_ShouldThrowException() {
        // Given
        when(warehouseRepository.existsById(1L)).thenReturn(true);
        when(shipmentRepository.existsByWarehouseId(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> warehouseService.deleteWarehouse(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("existing shipments");

        verify(warehouseRepository, never()).deleteById(any());
    }
}
