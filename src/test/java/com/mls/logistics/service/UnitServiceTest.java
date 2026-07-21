package com.mls.logistics.service;

import com.mls.logistics.domain.Unit;
import com.mls.logistics.dto.request.CreateUnitRequest;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.repository.OrderRepository;
import com.mls.logistics.repository.UnitRepository;
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
 * Unit tests for UnitService.
 *
 * Tests business logic without requiring database or Spring context.
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private UnitService unitService;

    private Unit testUnit;

    @BeforeEach
    void setUp() {
        testUnit = new Unit();
        testUnit.setId(1L);
        testUnit.setName("Test Unit");
        testUnit.setLocation("Test Location");
    }

    @Test
    void getAllUnits_ShouldReturnAllUnits() {
        // Given
        List<Unit> units = Arrays.asList(testUnit);
        when(unitRepository.findAll()).thenReturn(units);

        // When
        List<Unit> result = unitService.getAllUnits();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Unit");
        verify(unitRepository, times(1)).findAll();
    }

    @Test
    void getUnitById_WhenExists_ShouldReturnUnit() {
        // Given
        when(unitRepository.findById(1L)).thenReturn(Optional.of(testUnit));

        // When
        Optional<Unit> result = unitService.getUnitById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Unit");
        verify(unitRepository, times(1)).findById(1L);
    }

    @Test
    void getUnitById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        when(unitRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Unit> result = unitService.getUnitById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(unitRepository, times(1)).findById(999L);
    }

    @Test
    void createUnit_WithValidRequest_ShouldReturnCreatedUnit() {
        // Given
        CreateUnitRequest request = new CreateUnitRequest("New Unit", "New Location");
        when(unitRepository.save(any(Unit.class))).thenReturn(testUnit);

        // When
        Unit result = unitService.createUnit(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Unit");
        verify(unitRepository, times(1)).save(any(Unit.class));
    }

    @Test
    void deleteUnit_WhenExists_ShouldDeleteSuccessfully() {
        // Given
        when(unitRepository.existsById(1L)).thenReturn(true);
        doNothing().when(unitRepository).deleteById(1L);

        // When
        unitService.deleteUnit(1L);

        // Then
        verify(unitRepository, times(1)).existsById(1L);
        verify(unitRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteUnit_WhenNotExists_ShouldThrowException() {
        // Given
        when(unitRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> unitService.deleteUnit(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Unit not found with id: '999'");

        verify(unitRepository, times(1)).existsById(999L);
        verify(unitRepository, never()).deleteById(any());
    }

    @Test
    void deleteUnit_WithExistingOrders_ShouldThrowException() {
        // Given
        when(unitRepository.existsById(1L)).thenReturn(true);
        when(orderRepository.existsByUnitId(1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> unitService.deleteUnit(1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("existing orders");

        verify(unitRepository, never()).deleteById(any());
    }
}
