package com.mls.logistics.service;

import com.mls.logistics.domain.Movement;
import com.mls.logistics.domain.Resource;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.domain.Warehouse;
import com.mls.logistics.dto.request.CreateStockRequest;
import com.mls.logistics.exception.DuplicateResourceException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.repository.MovementRepository;
import com.mls.logistics.repository.StockRepository;
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
 * Unit tests for StockService.
 *
 * Tests business logic without requiring database or Spring context.
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private MovementRepository movementRepository;

    @InjectMocks
    private StockService stockService;

    private Stock testStock;

    @BeforeEach
    void setUp() {
        Resource resource = new Resource();
        resource.setId(1L);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);

        testStock = new Stock();
        testStock.setId(1L);
        testStock.setResource(resource);
        testStock.setWarehouse(warehouse);
        testStock.setQuantity(20);
    }

    @Test
    void getAllStocks_ShouldReturnAllStocks() {
        // Given
        List<Stock> stocks = Arrays.asList(testStock);
        when(stockRepository.findAll()).thenReturn(stocks);

        // When
        List<Stock> result = stockService.getAllStocks();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuantity()).isEqualTo(20);
        verify(stockRepository, times(1)).findAll();
    }

    @Test
    void getStockById_WhenExists_ShouldReturnStock() {
        // Given
        when(stockRepository.findById(1L)).thenReturn(Optional.of(testStock));

        // When
        Optional<Stock> result = stockService.getStockById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getQuantity()).isEqualTo(20);
        verify(stockRepository, times(1)).findById(1L);
    }

    @Test
    void getStockById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        when(stockRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Stock> result = stockService.getStockById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(stockRepository, times(1)).findById(999L);
    }

    @Test
    void createStock_WithValidRequest_ShouldReturnCreatedStock() {
        // Given
        CreateStockRequest request = new CreateStockRequest(1L, 1L, 20);
        when(stockRepository.save(any(Stock.class))).thenReturn(testStock);
        when(movementRepository.save(any(Movement.class))).thenReturn(new Movement());

        // When
        Stock result = stockService.createStock(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(20);
        verify(stockRepository, times(1)).save(any(Stock.class));
        verify(movementRepository, times(1)).save(any(Movement.class));
    }

    @Test
    void createStock_WhenResourceAlreadyStockedInWarehouse_ShouldThrowException() {
        // Given: stocks has a UNIQUE(resource_id, warehouse_id) constraint —
        // a duplicate must be rejected with a clean 409, not a raw DB error.
        CreateStockRequest request = new CreateStockRequest(1L, 1L, 20);
        when(stockRepository.findByResourceIdAndWarehouseId(1L, 1L)).thenReturn(Optional.of(testStock));

        // When & Then
        assertThatThrownBy(() -> stockService.createStock(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("resource id 1")
                .hasMessageContaining("warehouse id 1");

        verify(stockRepository, never()).save(any());
    }

    @Test
    void deleteStock_WhenExistsWithoutHistory_ShouldDeleteSuccessfully() {
        // Given
        when(stockRepository.existsById(1L)).thenReturn(true);
        when(movementRepository.existsByStockId(1L)).thenReturn(false);
        doNothing().when(stockRepository).deleteById(1L);

        // When
        stockService.deleteStock(1L);

        // Then
        verify(stockRepository, times(1)).existsById(1L);
        verify(stockRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteStock_WithMovementHistory_ShouldThrowException() {
        // Given: the stock has audit history — deleting it would erase the trail
        when(stockRepository.existsById(1L)).thenReturn(true);
        when(movementRepository.existsByStockId(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> stockService.deleteStock(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("audit trail");

        verify(stockRepository, never()).deleteById(any());
    }

    @Test
    void getMinQuantityByWarehouseId_ShouldMapDatabaseProjectionRowsToAMap() {
        // Given: the MIN(quantity)-per-warehouse grouping happens in SQL
        // (StockRepository.minQuantityByWarehouse) — this test only checks
        // the service correctly maps those projection rows into a Map.
        var rowOne = mock(StockRepository.WarehouseMinQuantity.class);
        when(rowOne.getWarehouseId()).thenReturn(1L);
        when(rowOne.getMinQuantity()).thenReturn(5);
        var rowTwo = mock(StockRepository.WarehouseMinQuantity.class);
        when(rowTwo.getWarehouseId()).thenReturn(2L);
        when(rowTwo.getMinQuantity()).thenReturn(0);

        when(stockRepository.minQuantityByWarehouse()).thenReturn(List.of(rowOne, rowTwo));

        // When
        var result = stockService.getMinQuantityByWarehouseId();

        // Then
        assertThat(result).containsEntry(1L, 5).containsEntry(2L, 0);
    }

    @Test
    void getStockQuantityByWarehouse_ShouldMapDatabaseProjectionRowsToAMap() {
        // Given: the SUM(quantity)-per-warehouse grouping and ordering
        // happens in SQL (StockRepository.sumQuantityByWarehouse) — this
        // test only checks the service preserves that order in the Map.
        var rowOne = mock(StockRepository.WarehouseQuantity.class);
        when(rowOne.getWarehouseName()).thenReturn("Central Depot");
        when(rowOne.getTotal()).thenReturn(120L);
        var rowTwo = mock(StockRepository.WarehouseQuantity.class);
        when(rowTwo.getWarehouseName()).thenReturn("Northern Depot");
        when(rowTwo.getTotal()).thenReturn(30L);

        when(stockRepository.sumQuantityByWarehouse()).thenReturn(List.of(rowOne, rowTwo));

        // When
        var result = stockService.getStockQuantityByWarehouse();

        // Then
        assertThat(result).containsExactly(
                java.util.Map.entry("Central Depot", 120L),
                java.util.Map.entry("Northern Depot", 30L)
        );
    }

    @Test
    void deleteStock_WhenNotExists_ShouldThrowException() {
        // Given
        when(stockRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> stockService.deleteStock(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Stock not found with id: '999'");

        verify(stockRepository, times(1)).existsById(999L);
        verify(stockRepository, never()).deleteById(any());
    }
}
